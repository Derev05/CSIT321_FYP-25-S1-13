package com.example.kotlinbasics;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class ForgotUser extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText emailInput;
    private Button submitButton, cancelButton;
    private ProgressBar loadingSpinner;
    private static final String TAG = "ForgotUserActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Link UI elements
        emailInput = findViewById(R.id.emailInput);
        submitButton = findViewById(R.id.submitButton);
        cancelButton = findViewById(R.id.cancelButton);
        loadingSpinner = findViewById(R.id.loadingSpinner);

        // Handle Password Reset
        submitButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();

            if (email.isEmpty()) {
                showCustomToast("❌ Please enter your email.");
                return;
            }

            loadingSpinner.setVisibility(View.VISIBLE);
            submitButton.setEnabled(false);

            // ✅ Try sending reset email directly
            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        loadingSpinner.setVisibility(View.GONE);
                        submitButton.setEnabled(true);

                        if (task.isSuccessful()) {
                            showCustomToast("✅ Reset link sent to your email!");
                            Log.d(TAG, "Reset link sent to: " + email);
                            startActivity(new Intent(ForgotUser.this, LoginActivity.class));
                            finish();
                        } else {
                            // ✅ Handle error if email is not found
                            if (task.getException() instanceof FirebaseAuthInvalidUserException) {
                                showCustomToast("❌ No account found with this email.");
                            } else {
                                showCustomToast("❌ Error: " + task.getException().getMessage());
                            }
                            Log.e(TAG, "Error sending reset link: " + task.getException().getMessage());
                        }
                    });
        });

        // Handle Cancel Button
        cancelButton.setOnClickListener(v -> finish());
    }

    // ✅ Show Custom Toast for Full Message Visibility
    private void showCustomToast(String message) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(R.id.toastText));

        TextView toastText = layout.findViewById(R.id.toastText);
        toastText.setText(message);

        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.BOTTOM, 0, 100); // Positioning
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }
}
