package com.example.kotlinbasics;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.concurrent.Executor;
import android.content.Intent;


public class MainMenu extends AppCompatActivity {

    private TextView userEmailText;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bioauth); // Ensure this matches your new layout file

        userEmailText = findViewById(R.id.userEmailText);
        mAuth = FirebaseAuth.getInstance();

        // ✅ Get the logged-in user and display the email
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String email = user.getEmail();
            userEmailText.setText("Signed in as: " + email);
        } else {
            userEmailText.setText("Not Signed In");
        }

        ImageView biometric = findViewById(R.id.biometricIcon);
        biometric.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainMenu.this, activity_terms_condition.class);
                startActivity(intent);
            }
        });
        ImageView review = findViewById(R.id.reviewIcon);
        review.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainMenu.this, reviews_data.class);
                startActivity(intent);

            }
        });


        // ✅ Biometric Authentication Setup
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(MainMenu.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Toast.makeText(MainMenu.this, "✅ Authentication Successful!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(MainMenu.this, "❌ Authentication Failed", Toast.LENGTH_SHORT).show();
            }
        });

        // ✅ Show biometric prompt
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Use fingerprint to continue")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }
}
