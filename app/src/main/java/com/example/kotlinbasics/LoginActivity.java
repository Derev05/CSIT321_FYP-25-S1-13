package com.example.kotlinbasics;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText emailInput, passwordInput;
    private Button loginButton;
    private TextView signUpButton, forgotPasswordButton;
    private CheckBox rememberMeCheckBox;
    private ProgressBar loadingSpinner;
    private ImageButton togglePassword;
    private boolean isPasswordVisible = false;
    private static final String TAG = "LoginActivity";
    private ExecutorService emailExecutor = Executors.newSingleThreadExecutor();
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE);

        emailInput = findViewById(R.id.myUsername);
        passwordInput = findViewById(R.id.myPassword);
        loginButton = findViewById(R.id.signInButton);
        signUpButton = findViewById(R.id.signUpButton);
        forgotPasswordButton = findViewById(R.id.forgotButton);
        rememberMeCheckBox = findViewById(R.id.checkBox);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        togglePassword = findViewById(R.id.togglePassword);

        // ✅ Load saved email from SharedPreferences
        String savedEmail = sharedPreferences.getString("savedEmail", "");
        if (!savedEmail.isEmpty()) {
            emailInput.setText(savedEmail);
            rememberMeCheckBox.setChecked(true);
        }

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

        // ✅ Handle Login Button Click
        loginButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                showCustomToast("Please enter your email and password.");
                return;
            }

            loadingSpinner.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        loadingSpinner.setVisibility(View.GONE);
                        loginButton.setEnabled(true);

                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null && user.isEmailVerified()) {
                                Log.d(TAG, "✅ Email login successful. Sending OTP...");

                                // ✅ Save or Clear Email in SharedPreferences
                                if (rememberMeCheckBox.isChecked()) {
                                    sharedPreferences.edit().putString("savedEmail", email).apply();
                                } else {
                                    sharedPreferences.edit().remove("savedEmail").apply();
                                }

                                // ✅ Generate OTP
                                String otp = generateOTP();
                                emailExecutor.execute(() -> sendOtpEmail(email, otp));

                                // ✅ Redirect to OTP Verification Page
                                Intent intent = new Intent(LoginActivity.this, OtpVerificationActivity.class);
                                intent.putExtra("email", email);
                                intent.putExtra("otp", otp);
                                startActivity(intent);
                                finish();
                            } else {
                                showCustomToast("Please verify your email before logging in.");
                                if (user != null) user.sendEmailVerification();
                            }
                        } else {
                            Exception e = task.getException();
                            if (e instanceof FirebaseAuthInvalidCredentialsException) {
                                showCustomToast("❌ Wrong password. Try again.");
                            } else if (e instanceof FirebaseAuthInvalidUserException) {
                                showCustomToast("❌ Email not registered.");
                            } else {
                                showCustomToast("Login Failed: " + e.getMessage());
                            }
                            Log.e(TAG, "❌ Login Failed: " + e.getMessage());
                        }
                    });
        });

        // ✅ Sign Up Button Click
        signUpButton.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
            finish();
        });

        // ✅ Forgot Password Button Click
        forgotPasswordButton.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, ForgotUser.class));
        });
    }

    // ✅ Generate a 6-digit OTP
    private String generateOTP() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    // ✅ Send OTP via Email
    private void sendOtpEmail(String email, String otp) {
        try {
            String subject = "Your OTP Code For BioAuth";
            String messageBody = "Your OTP is: " + otp + "\n\nUse this code to verify your login. Code will expire in 3 hours.";

            GmailSender.sendEmailAsync(email, subject, messageBody, success -> {
                if (success) {
                    Log.d(TAG, "✅ OTP sent to email: " + email);
                } else {
                    Log.e(TAG, "❌ Failed to send OTP email.");
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to send OTP email: " + e.getMessage());
        }
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
