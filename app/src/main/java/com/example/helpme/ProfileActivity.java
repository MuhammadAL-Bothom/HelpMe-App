package com.example.helpme;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseUser user;

    // UI
    private TextView title, roleTitle, primaryBtn;
    private EditText fullName, email, password, phone;
    private EditText carType, carColor, carModel;        // user
    private EditText expType, expLevel, locationEt;      // helper

    private String role = "user";
    private boolean isEditing = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
        user = auth.getCurrentUser();

        bindViews();

        if (user == null) {
            finish();
            return;
        }

        loadProfile();

        primaryBtn.setOnClickListener(v -> {
            if (!isEditing) {
                switchToEdit(true);
            } else {
                saveChanges();
            }
        });
    }

    private void bindViews() {
        title      = findViewById(R.id.title);
        roleTitle  = findViewById(R.id.roleTitle);
        primaryBtn = findViewById(R.id.primaryBtn);

        fullName = findViewById(R.id.fullName);
        email    = findViewById(R.id.email);
        password = findViewById(R.id.password);
        phone    = findViewById(R.id.phone);

        carType  = findViewById(R.id.carType);
        carColor = findViewById(R.id.carColor);
        carModel = findViewById(R.id.carModel);

        expType    = findViewById(R.id.expType);
        expLevel   = findViewById(R.id.expLevel);
        locationEt = findViewById(R.id.locationEt);
    }

    private void loadProfile() {
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(this::applySnapshot)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void applySnapshot(DocumentSnapshot s) {
        if (s == null || !s.exists()) {
            Toast.makeText(this, "Profile not found", Toast.LENGTH_LONG).show();
            return;
        }

        // role + titles
        Object r = s.get("role");
        role = r == null ? "user" : String.valueOf(r).toLowerCase();
        if ("helper".equals(role)) {
            roleTitle.setVisibility(View.VISIBLE);
            title.setText("HELP ME APP");
        } else {
            roleTitle.setVisibility(View.GONE);
            title.setText("PROFILE");
        }

        // shared
        fullName.setText(getStr(s, "fullName"));
        email.setText(user.getEmail() == null ? getStr(s, "email") : user.getEmail());
        password.setText(""); // never readable
        phone.setText(getStr(s, "phone"));

        if ("helper".equals(role)) {
            // show helper fields, hide user fields
            setVis(carType, false);
            setVis(carColor, false);
            setVis(carModel, false);

            setVis(expType, true);
            setVis(expLevel, true);
            setVis(locationEt, true);

            expType.setText(getStr(s, "experienceType"));
            expLevel.setText(getStr(s, "experienceLevel"));
            locationEt.setText(getStr(s, "location"));

        } else {
            // show user fields, hide helper fields
            setVis(expType, false);
            setVis(expLevel, false);
            setVis(locationEt, false);

            setVis(carType, true);
            setVis(carColor, true);
            setVis(carModel, true);

            carType.setText(getStr(s, "carType"));
            carColor.setText(getStr(s, "carColor"));
            carModel.setText(getStr(s, "carModel"));
        }

        switchToEdit(false); // start in view mode
    }

    private void switchToEdit(boolean edit) {
        isEditing = edit;
        primaryBtn.setText(edit ? "SAVE" : "EDIT");

        boolean enable = edit;

        fullName.setEnabled(enable);
        email.setEnabled(enable);
        password.setEnabled(enable);
        phone.setEnabled(enable);

        if ("helper".equals(role)) {
            expType.setEnabled(enable);
            expLevel.setEnabled(enable);
            locationEt.setEnabled(enable);

            carType.setEnabled(false);
            carColor.setEnabled(false);
            carModel.setEnabled(false);
        } else {
            carType.setEnabled(enable);
            carColor.setEnabled(enable);
            carModel.setEnabled(enable);

            expType.setEnabled(false);
            expLevel.setEnabled(false);
            locationEt.setEnabled(false);
        }
    }

    private void saveChanges() {
        String nm = txt(fullName);
        String em = txt(email);
        String ph = txt(phone);
        String newPw = txt(password); // optional

        if (TextUtils.isEmpty(nm) || TextUtils.isEmpty(em)) {
            Toast.makeText(this, "Name & Email are required", Toast.LENGTH_SHORT).show();
            return;
        }

        primaryBtn.setEnabled(false);

        // Build update map
        Map<String, Object> doc = new HashMap<>();
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

        // Update Firestore first
        db.collection("users").document(user.getUid()).update(doc)
                .addOnSuccessListener(unused -> {
                    // Update email in Auth if changed
                    if (!TextUtils.isEmpty(em) && !em.equals(user.getEmail())) {
                        user.updateEmail(em)
                                .addOnSuccessListener(u -> {})
                                .addOnFailureListener(err ->
                                        Toast.makeText(this, "Email update (Auth) failed: " + err.getMessage(), Toast.LENGTH_LONG).show());
                    }

                    // Update password if provided
                    if (!TextUtils.isEmpty(newPw)) {
                        user.updatePassword(newPw)
                                .addOnSuccessListener(u -> {})
                                .addOnFailureListener(err ->
                                        Toast.makeText(this, "Password update failed: " + err.getMessage(), Toast.LENGTH_LONG).show());
                    }

                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                    primaryBtn.setEnabled(true);
                    switchToEdit(false);
                })
                .addOnFailureListener(e -> {
                    primaryBtn.setEnabled(true);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /* helpers */
    private static String getStr(DocumentSnapshot s, String key) {
        Object v = s.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static String txt(EditText e) {
        return e == null ? "" : e.getText().toString().trim();
    }

    private static void setVis(View v, boolean show) {
        v.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
