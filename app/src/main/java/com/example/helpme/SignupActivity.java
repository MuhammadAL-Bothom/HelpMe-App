package com.example.helpme;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private String role = "user";

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // Shared
    private EditText fullName, email, password, phone;
    private TextView primaryBtn, toggleText, roleTitle;

    // User-only
    private EditText carType, carColor, carModel;

    // Helper-only
    private EditText expType, expLevel, locationEt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        role = getIntent().getStringExtra("ROLE");
        if (role == null) role = "user";
        role = role.toLowerCase();

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        bindViews();
        configureForRole();

        primaryBtn.setOnClickListener(v -> doSignup());
        toggleText.setOnClickListener(v -> finish()); // back to login
    }

    private void bindViews() {
        fullName   = findViewById(R.id.fullName);
        email      = findViewById(R.id.email);
        password   = findViewById(R.id.password);
        phone      = findViewById(R.id.phone);
        primaryBtn = findViewById(R.id.primaryBtn);
        toggleText = findViewById(R.id.toggleText);
        roleTitle  = findViewById(R.id.roleTitle);

        carType    = findViewById(R.id.carType);
        carColor   = findViewById(R.id.carColor);
        carModel   = findViewById(R.id.carModel);

        expType    = findViewById(R.id.expType);
        expLevel   = findViewById(R.id.expLevel);
        locationEt = findViewById(R.id.locationEt);
    }

    private void configureForRole() {
        boolean isHelper = "helper".equals(role);

        // Subtitle and button label (helper mock shows "LOGIN")
        roleTitle.setVisibility(isHelper ? View.VISIBLE : View.GONE);
        primaryBtn.setText("SIGN UP");

        // Toggle user fields
        carType.setVisibility(isHelper ? View.GONE : View.VISIBLE);
        carColor.setVisibility(isHelper ? View.GONE : View.VISIBLE);
        carModel.setVisibility(isHelper ? View.GONE : View.VISIBLE);

        // Toggle helper fields
        expType.setVisibility(isHelper ? View.VISIBLE : View.GONE);
        expLevel.setVisibility(isHelper ? View.VISIBLE : View.GONE);
        locationEt.setVisibility(isHelper ? View.VISIBLE : View.GONE);
    }

    private void doSignup() {
        String nm = txt(fullName);
        String em = txt(email);
        String pw = txt(password);
        String ph = txt(phone);

        if (TextUtils.isEmpty(nm) || TextUtils.isEmpty(em) || TextUtils.isEmpty(pw)) {
            Toast.makeText(this, "Fill FULL NAME, EMAIL and PASSWORD", Toast.LENGTH_SHORT).show();
            return;
        }

        primaryBtn.setEnabled(false);

        auth.createUserWithEmailAndPassword(em, pw)
                .addOnSuccessListener(res -> {
                    String uid = res.getUser().getUid();

                    Map<String, Object> doc = new HashMap<>();
                    doc.put("role", role);
                    doc.put("fullName", nm);
                    doc.put("email", em);
                    doc.put("phone", ph);

                    if ("helper".equals(role)) {
                        doc.put("experienceType", txt(expType));
                        doc.put("experienceLevel", txt(expLevel));
                        doc.put("location", txt(locationEt));
                    } else {
                        doc.put("carType",  txt(carType));
                        doc.put("carColor", txt(carColor));
                        doc.put("carModel", txt(carModel));
                    }

                    db.collection("users").document(uid).set(doc)
                            .addOnSuccessListener(unused -> {
                                primaryBtn.setEnabled(true);
                                Toast.makeText(this, "Signed up as " + role + ". Please log in.", Toast.LENGTH_LONG).show();
                                finish(); // back to AuthActivity (login)
                            })
                            .addOnFailureListener(e -> {
                                primaryBtn.setEnabled(true);
                                Toast.makeText(this, "Profile save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    primaryBtn.setEnabled(true);
                    Toast.makeText(this, "Signup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private String txt(EditText et) {
        return et == null ? "" : et.getText().toString().trim();
    }
}
