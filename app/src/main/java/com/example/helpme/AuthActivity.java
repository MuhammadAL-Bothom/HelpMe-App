package com.example.helpme;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class AuthActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // Views (your layout can still have fullName/phone; we’ll hide them for login)
    private EditText fullName, phone, email, password;
    private TextView primaryBtn, toggleText, forgotText;

    // From AccountChooser: "user" or "helper"
    private String selectedRole = "user";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        String r = getIntent().getStringExtra("ROLE");
        if (r != null) selectedRole = r.toLowerCase();

        fullName   = findViewById(R.id.fullName);
        phone      = findViewById(R.id.phone);
        email      = findViewById(R.id.email);
        password   = findViewById(R.id.password);
        primaryBtn = findViewById(R.id.primaryBtn);
        toggleText = findViewById(R.id.toggleText);
        forgotText = findViewById(R.id.forgotText);

        // This screen is LOGIN only → hide signup-only inputs if they exist in your XML
        if (fullName != null) fullName.setVisibility(View.GONE);
        if (phone != null)    phone.setVisibility(View.GONE);
        primaryBtn.setText("LOGIN");
        toggleText.setText("DONT HAVE ACCOUN!SIGNUP");

        // LOGIN
        primaryBtn.setOnClickListener(v -> doLogin());

        // Go to role-aware SIGN UP
        toggleText.setOnClickListener(v -> {
            Intent i = new Intent(AuthActivity.this, SignupActivity.class);
            i.putExtra("ROLE", selectedRole); // keep same role chosen in AccountChooser
            startActivity(i);
        });

        // Password reset
        forgotText.setOnClickListener(v -> sendReset());
    }

    private void doLogin() {
        final String em = email.getText().toString().trim();
        final String pw = password.getText().toString().trim();

        if (TextUtils.isEmpty(em) || TextUtils.isEmpty(pw)) {
            Toast.makeText(this, "Fill EMAIL & PASSWORD", Toast.LENGTH_SHORT).show();
            return;
        }

        primaryBtn.setEnabled(false);
        auth.signInWithEmailAndPassword(em, pw)
                .addOnSuccessListener(res -> {
                    String uid = res.getUser().getUid();

                    // Read role from Firestore (source of truth)
                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(snapshot -> {
                                primaryBtn.setEnabled(true);

                                if (snapshot == null || !snapshot.exists()) {
                                    auth.signOut();
                                    Toast.makeText(this,
                                            "Profile not found. Please sign up again.",
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }

                                String role = snapshot.contains("role")
                                        ? String.valueOf(snapshot.get("role")).toLowerCase()
                                        : "";

                                if (role.equals(selectedRole)) {
                                    Toast.makeText(this, "Logged in as: " + role, Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(this, HomeActivity.class));
                                    finish();
                                } else {
                                    // Role mismatch → sign out and instruct user
                                    auth.signOut();
                                    String wanted = selectedRole.toUpperCase();
                                    String actual = role.isEmpty() ? "UNKNOWN" : role.toUpperCase();
                                    Toast.makeText(this,
                                            "Role mismatch.\nSelected: " + wanted + " but account is: " + actual +
                                                    ".\nGo back and choose the correct account type.",
                                            Toast.LENGTH_LONG).show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                primaryBtn.setEnabled(true);
                                Toast.makeText(this, "Read profile failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    primaryBtn.setEnabled(true);
                    Toast.makeText(this, "Login failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void sendReset() {
        String em = email.getText().toString().trim();
        if (TextUtils.isEmpty(em)) {
            Toast.makeText(this, "Enter your EMAIL first", Toast.LENGTH_SHORT).show();
            return;
        }
        auth.sendPasswordResetEmail(em)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Reset link sent to " + em, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Reset failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
