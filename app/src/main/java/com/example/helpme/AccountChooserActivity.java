package com.example.helpme;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AccountChooserActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_chooser);

        View userCard = findViewById(R.id.userCard);
        View helperCard = findViewById(R.id.helperCard);


// AccountChooserActivity.java (replace the onClick listeners)
        userCard.setOnClickListener(v -> {
            Intent i = new Intent(this, AuthActivity.class);
            i.putExtra("ROLE", "user");  // used only on first sign-up
            startActivity(i);
        });

        helperCard.setOnClickListener(v -> {
            Intent i = new Intent(this, AuthActivity.class);
            i.putExtra("ROLE", "helper"); // used only on first sign-up
            startActivity(i);
        });

    }
}
