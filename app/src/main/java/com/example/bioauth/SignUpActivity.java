package com.example.bioauth;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.*;

public class SignUpActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private EditText emailInput, passwordInput, confirmPasswordInput;
    private Button signUpButton, cancelButton;
    private ImageButton togglePassword, toggleConfirmPassword;
    private static final String TAG = "SignUpActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance(); // ✅ Add storage

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        signUpButton = findViewById(R.id.signUpButton);
        cancelButton = findViewById(R.id.cancelButton);
        togglePassword = findViewById(R.id.togglePassword);
        toggleConfirmPassword = findViewById(R.id.toggleConfirmPassword);

        togglePassword.setOnClickListener(v -> toggleVisibility(passwordInput, togglePassword));
        toggleConfirmPassword.setOnClickListener(v -> toggleVisibility(confirmPasswordInput, toggleConfirmPassword));

        emailInput.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) emailInput.setSelection(0); });
        passwordInput.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) passwordInput.setSelection(0); });
        confirmPasswordInput.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) confirmPasswordInput.setSelection(0); });

        findViewById(android.R.id.content).setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    currentFocus.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);

                    if (currentFocus == emailInput) emailInput.setSelection(0);
                    if (currentFocus == passwordInput) passwordInput.setSelection(0);
                    if (currentFocus == confirmPasswordInput) confirmPasswordInput.setSelection(0);
                }
            }
            return false;
        });

        signUpButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            String confirmPass = confirmPasswordInput.getText().toString().trim();

            if (email.isEmpty()) { showCustomToast("Email cannot be empty"); emailInput.requestFocus(); return; }
            if (password.isEmpty()) { showCustomToast("Password cannot be empty"); passwordInput.requestFocus(); return; }
            if (confirmPass.isEmpty()) { showCustomToast("Please confirm your password"); confirmPasswordInput.requestFocus(); return; }
            if (!password.equals(confirmPass)) { showCustomToast("Passwords do not match"); confirmPasswordInput.requestFocus(); return; }
            if (!isPasswordStrong(password)) { showCustomToast("Password too weak! Use 8+ characters with upper/lowercase, numbers, and symbols."); passwordInput.requestFocus(); return; }

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "✅ User registered.");
                            sendVerificationEmail();

                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                String userId = user.getUid();
                                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH);
                                sdf.setTimeZone(TimeZone.getTimeZone("Asia/Singapore"));
                                String joinedAtSGT = sdf.format(new Date());

                                Map<String, Object> userData = new HashMap<>();
                                userData.put("email", user.getEmail());
                                userData.put("status", "free");
                                userData.put("joinedAt", joinedAtSGT);
                                userData.put("timestamp", System.currentTimeMillis());

                                db.collection("users")
                                        .document(userId)
                                        .set(userData)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "✅ User data saved in Firestore");
                                            uploadDefaultProfilePhoto(userId); // ✅ Upload default photo
                                        })
                                        .addOnFailureListener(e -> Log.e(TAG, "❌ Firestore error: " + e.getMessage()));
                            }
                        } else {
                            showCustomToast("Registration Failed: " + task.getException().getMessage());
                        }
                    });
        });

        cancelButton.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void uploadDefaultProfilePhoto(String userId) {
        Uri defaultPhotoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.drawable.default_avatar); // ✅ Default image
        StorageReference storageRef = storage.getReference().child("profilePhotos/" + userId + ".jpg");

        storageRef.putFile(defaultPhotoUri)
                .addOnSuccessListener(taskSnapshot -> Log.d(TAG, "✅ Default profile photo uploaded"))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to upload default profile photo: " + e.getMessage()));
    }

    private boolean isPasswordStrong(String password) {
        if (password.length() < 8) return false;
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*+=?-].*");
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    private void toggleVisibility(EditText field, ImageButton toggleBtn) {
        boolean visible = field.getTransformationMethod() instanceof HideReturnsTransformationMethod;
        field.setTransformationMethod(visible ? PasswordTransformationMethod.getInstance() : HideReturnsTransformationMethod.getInstance());
        toggleBtn.setImageResource(visible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
        field.setSelection(field.getText().length());
    }

    private void showCustomToast(String message) {
        View layout = LayoutInflater.from(this).inflate(R.layout.custom_toast_layout, findViewById(android.R.id.content), false);
        TextView text = layout.findViewById(R.id.toastText);
        text.setText(message);
        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.setGravity(Gravity.BOTTOM, 0, 150);
        toast.show();
    }

    private void sendVerificationEmail() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            showCustomToast("Verification email sent. Check your inbox or spam!");
                            startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
                            finish();
                        } else {
                            showCustomToast("Failed to send verification email.");
                        }
                    });
        }
    }
}
