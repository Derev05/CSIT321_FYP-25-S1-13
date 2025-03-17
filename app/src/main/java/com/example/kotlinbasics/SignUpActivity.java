package com.example.kotlinbasics;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SignUpActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText emailInput, passwordInput, confirmPasswordInput;
    private Button signUpButton, cancelButton;
    private ProgressBar passwordStrengthBar;
    private TextView passwordStrengthText;
    private ImageButton togglePassword, toggleConfirmPassword;
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    private static final String TAG = "SignUpActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        mAuth = FirebaseAuth.getInstance();

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        signUpButton = findViewById(R.id.signUpButton);
        cancelButton = findViewById(R.id.cancelButton);
        passwordStrengthBar = findViewById(R.id.passwordStrengthBar);
        passwordStrengthText = findViewById(R.id.passwordStrengthText);
        togglePassword = findViewById(R.id.togglePassword);
        toggleConfirmPassword = findViewById(R.id.toggleConfirmPassword);

        // ✅ Monitor password input for strength
        passwordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePasswordStrength(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // ✅ Toggle Password Visibility
        togglePassword.setOnClickListener(v -> {
            if (isPasswordVisible) {
                passwordInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
                togglePassword.setImageResource(R.drawable.ic_visibility_off);
            } else {
                passwordInput.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                togglePassword.setImageResource(R.drawable.ic_visibility);
            }
            passwordInput.setSelection(passwordInput.getText().length());
            isPasswordVisible = !isPasswordVisible;
        });

        // ✅ Toggle Confirm Password Visibility
        toggleConfirmPassword.setOnClickListener(v -> {
            if (isConfirmPasswordVisible) {
                confirmPasswordInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
                toggleConfirmPassword.setImageResource(R.drawable.ic_visibility_off);
            } else {
                confirmPasswordInput.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                toggleConfirmPassword.setImageResource(R.drawable.ic_visibility);
            }
            confirmPasswordInput.setSelection(confirmPasswordInput.getText().length());
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
        });

        signUpButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            String confirmPass = confirmPasswordInput.getText().toString().trim();

            // ✅ Check if fields are empty
            if (email.isEmpty()) {
                showCustomToast("Email cannot be empty");
                emailInput.requestFocus();
                return;
            }
            if (password.isEmpty()) {
                showCustomToast("Password cannot be empty");
                passwordInput.requestFocus();
                return;
            }
            if (confirmPass.isEmpty()) {
                showCustomToast("Please confirm your password");
                confirmPasswordInput.requestFocus();
                return;
            }

            // ✅ Check if passwords match
            if (!password.equals(confirmPass)) {
                showCustomToast("Passwords do not match");
                confirmPasswordInput.requestFocus();
                return;
            }

            // ✅ Check password strength
            if (!isPasswordStrong(password)) {
                showCustomToast("Password is too weak! Use upper/lower letters, numbers, and symbols");
                passwordInput.requestFocus();
                return;
            }

            // ✅ Register user with Firebase
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "User registered successfully!");
                            sendVerificationEmail();
                        } else {
                            String errorMessage = task.getException().getMessage();
                            showCustomToast("Registration Failed: " + errorMessage);
                        }
                    });
        });

        cancelButton.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
            finish();
        });
    }

    // ✅ Show a custom Toast (prevents text cutoff)
    // ✅ Custom Toast to prevent text cutoff
    private void showCustomToast(String message) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(android.R.id.content), false);

        TextView text = layout.findViewById(R.id.toastText);
        text.setText(message);

        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.setGravity(Gravity.BOTTOM, 0, 150); // Position it at the bottom
        toast.show();
    }


    // ✅ Update password strength bar dynamically
    private void updatePasswordStrength(String password) {
        int strength = calculateStrength(password);

        if (strength < 40) {
            passwordStrengthText.setText("Weak");
            passwordStrengthText.setTextColor(Color.RED);
            passwordStrengthBar.setProgress(strength);
        } else if (strength < 70) {
            passwordStrengthText.setText("Medium");
            passwordStrengthText.setTextColor(Color.YELLOW);
            passwordStrengthBar.setProgress(strength);
        } else {
            passwordStrengthText.setText("Strong");
            passwordStrengthText.setTextColor(Color.GREEN);
            passwordStrengthBar.setProgress(strength);
        }
    }

    // ✅ Calculate password strength
    private int calculateStrength(String password) {
        int score = 0;

        if (password.length() >= 6) score += 20;
        if (password.length() >= 10) score += 20;
        if (password.matches(".*[A-Z].*")) score += 20;
        if (password.matches(".*[a-z].*")) score += 10;
        if (password.matches(".*\\d.*")) score += 20;
        if (password.matches(".*[!@#$%^&*+=?-].*")) score += 30;

        return Math.min(score, 100);
    }

    // ✅ Check if password is strong
    private boolean isPasswordStrong(String password) {
        return calculateStrength(password) >= 70;
    }

    // ✅ Send email verification
    private void sendVerificationEmail() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            showCustomToast("Verification email sent. Check your inbox!");
                            startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
                            finish();
                        } else {
                            showCustomToast("Failed to send verification email.");
                        }
                    });
        }
    }
}
