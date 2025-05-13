package com.example.bioauth;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

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

        mAuth = FirebaseAuth.getInstance();

        emailInput = findViewById(R.id.emailInput);
        submitButton = findViewById(R.id.submitButton);
        cancelButton = findViewById(R.id.cancelButton);
        loadingSpinner = findViewById(R.id.loadingSpinner);

        // ✅ Scroll to front when emailInput loses focus
        emailInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                emailInput.setSelection(0);
            }
        });

        // ✅ Handle touch outside to scroll back and hide keyboard
        findViewById(android.R.id.content).setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                View focused = getCurrentFocus();
                if (focused != null) {
                    focused.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);

                    if (focused == emailInput) {
                        emailInput.setSelection(0);
                    }
                }
            }
            return false;
        });

        submitButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();

            if (email.isEmpty()) {
                showCustomToast("❌ Please enter your email.");
                return;
            }

            loadingSpinner.setVisibility(View.VISIBLE);
            submitButton.setEnabled(false);

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
                            if (task.getException() instanceof FirebaseAuthInvalidUserException) {
                                showCustomToast("❌ No account found with this email.");
                            } else {
                                showCustomToast("❌ Error: " + task.getException().getMessage());
                            }
                            Log.e(TAG, "Error sending reset link: " + task.getException().getMessage());
                        }
                    });
        });

        cancelButton.setOnClickListener(v -> finish());
    }

    private void showCustomToast(String message) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(R.id.toastText));

        TextView toastText = layout.findViewById(R.id.toastText);
        toastText.setText(message);

        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.BOTTOM, 0, 100);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }
}
