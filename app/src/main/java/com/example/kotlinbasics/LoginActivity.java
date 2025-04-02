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
import android.widget.*;
import com.google.firebase.auth.*;

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

    private SharedPreferences sharedPreferences;
    private ExecutorService emailExecutor = Executors.newSingleThreadExecutor();

    private static final String TAG = "LoginActivity";
    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_LAST_OTP_VERIFIED = "last_otp_verified";
    private static final String KEY_LAST_UID = "last_user_uid";
    private static final long OTP_VALID_DURATION = 60 * 60 * 1000; // 1 hour

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        emailInput = findViewById(R.id.myUsername);
        passwordInput = findViewById(R.id.myPassword);
        loginButton = findViewById(R.id.signInButton);
        signUpButton = findViewById(R.id.signUpButton);
        forgotPasswordButton = findViewById(R.id.forgotButton);
        rememberMeCheckBox = findViewById(R.id.checkBox);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        togglePassword = findViewById(R.id.togglePassword);

        emailInput.setText(sharedPreferences.getString(KEY_EMAIL, ""));
        passwordInput.setText(sharedPreferences.getString(KEY_PASSWORD, ""));
        rememberMeCheckBox.setChecked(!sharedPreferences.getString(KEY_EMAIL, "").isEmpty());

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

        loginButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                showCustomToast("Please enter your email and password.");
                return;
            }

            loadingSpinner.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);

            mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
                loadingSpinner.setVisibility(View.GONE);
                loginButton.setEnabled(true);

                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null && user.isEmailVerified()) {
                        String currentUid = user.getUid();
                        String lastUid = sharedPreferences.getString(KEY_LAST_UID, null);

                        boolean isNewUser = lastUid == null || !lastUid.equals(currentUid);
                        long lastOtp = sharedPreferences.getLong(KEY_LAST_OTP_VERIFIED, 0);
                        boolean otpValid = (System.currentTimeMillis() - lastOtp) < OTP_VALID_DURATION;

                        if (rememberMeCheckBox.isChecked()) {
                            sharedPreferences.edit()
                                    .putString(KEY_EMAIL, email)
                                    .putString(KEY_PASSWORD, password)
                                    .putString(KEY_LAST_UID, currentUid)
                                    .apply();
                        } else {
                            sharedPreferences.edit()
                                    .remove(KEY_EMAIL)
                                    .remove(KEY_PASSWORD)
                                    .apply();
                        }

                        if (isNewUser || !otpValid) {
                            Log.d(TAG, "🛡 OTP required (new user or expired)");
                            generateAndSendOTP(email);
                        } else {
                            Log.d(TAG, "✅ OTP session valid. Proceeding to dashboard.");
                            proceedToDashboard();
                        }
                    } else {
                        showCustomToast("Please verify your email before logging in. Check your inbox or spam!");
                        if (user != null) user.sendEmailVerification();
                    }
                } else {
                    showCustomToast("Login Failed: " + task.getException().getMessage());
                }
            });
        });

        signUpButton.setOnClickListener(v -> startActivity(new Intent(this, SignUpActivity.class)));
        forgotPasswordButton.setOnClickListener(v -> startActivity(new Intent(this, ForgotUser.class)));
    }

    private void generateAndSendOTP(String email) {
        String otp = String.valueOf(new Random().nextInt(900000) + 100000);
        sharedPreferences.edit().putLong(KEY_LAST_OTP_VERIFIED, 0).apply(); // reset OTP validity

        emailExecutor.execute(() -> {
            String subject = "Your OTP Code For BioAuth";
            String body = "Your OTP is: " + otp + "\n\nUse this code to verify your login. Code will expire in 1 hour.";
            GmailSender.sendEmailAsync(email, subject, body, success -> {
                Log.d(TAG, success ? "✅ OTP email sent" : "❌ OTP email failed");
            });
        });

        Intent intent = new Intent(LoginActivity.this, OtpVerificationActivity.class);
        intent.putExtra("email", email);
        intent.putExtra("otp", otp);
        startActivity(intent);
        finish(); // prevents user from going back
    }

    private void proceedToDashboard() {
        Intent intent = new Intent(this, MainMenu.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void showCustomToast(String msg) {
        View view = LayoutInflater.from(this).inflate(R.layout.custom_toast_layout, findViewById(R.id.toastText));
        ((TextView) view.findViewById(R.id.toastText)).setText(msg);
        Toast toast = new Toast(this);
        toast.setView(view);
        toast.setGravity(Gravity.BOTTOM, 0, 100);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.show();
    }

    @Override
    protected void onDestroy() {
        emailExecutor.shutdown();
        super.onDestroy();
    }
}
