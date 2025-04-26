package com.example.kotlinbasics;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.*;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.airbnb.lottie.LottieAnimationView;
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

    private LottieAnimationView mascotView, loadingMascot;
    private SharedPreferences sharedPreferences;
    private ExecutorService emailExecutor = Executors.newSingleThreadExecutor();

    private static final String TAG = "LoginActivity";
    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_LAST_OTP_VERIFIED = "last_otp_verified";
    private static final String KEY_LAST_UID = "last_user_uid";
    private static final String KEY_PENDING_OTP = "pending_otp"; // ✅ Added
    private static final long OTP_VALID_DURATION = 60 * 60 * 1000; // 1 hour

    private boolean loginInProgress = false;

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
        mascotView = findViewById(R.id.mascotIdle);
        loadingMascot = findViewById(R.id.loadingMascot);

        playMascot("idle.json");

        emailInput.setText(sharedPreferences.getString(KEY_EMAIL, ""));
        passwordInput.setText(sharedPreferences.getString(KEY_PASSWORD, ""));
        rememberMeCheckBox.setChecked(!sharedPreferences.getString(KEY_EMAIL, "").isEmpty());

        emailInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) emailInput.setSelection(0);
        });

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
            if (loginInProgress) return;
            hideKeyboard();

            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                triggerHapticFeedback();
                playMascot("fail.json");
                showCustomToast("Please enter your email and password.");
                return;
            }

            loginInProgress = true;
            disableInputs();
            loadingSpinner.setVisibility(View.VISIBLE);
            loadingMascot.setVisibility(View.VISIBLE);
            loadingMascot.setAnimation("loading.json");
            loadingMascot.playAnimation();

            mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
                loadingSpinner.setVisibility(View.GONE);
                loadingMascot.setVisibility(View.GONE);

                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null && user.isEmailVerified()) {
                        String currentUid = user.getUid();
                        String lastUid = sharedPreferences.getString(KEY_LAST_UID, null);
                        long lastOtp = sharedPreferences.getLong(KEY_LAST_OTP_VERIFIED, 0);
                        boolean isNewUser = lastUid == null || !lastUid.equals(currentUid);
                        boolean otpExpired = (System.currentTimeMillis() - lastOtp) >= OTP_VALID_DURATION;

                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        if (rememberMeCheckBox.isChecked()) {
                            editor.putString(KEY_EMAIL, email);
                            editor.putString(KEY_PASSWORD, password);
                        } else {
                            editor.remove(KEY_EMAIL);
                            editor.remove(KEY_PASSWORD);
                        }
                        editor.putString(KEY_LAST_UID, currentUid);
                        editor.apply();

                        if (isNewUser || otpExpired) {
                            String pendingOtp = sharedPreferences.getString(KEY_PENDING_OTP, null);
                            if (pendingOtp != null) {
                                Intent intent = new Intent(this, OtpVerificationActivity.class);
                                intent.putExtra("email", email);
                                intent.putExtra("otp", pendingOtp);
                                startActivity(intent);
                                finish();
                            } else {
                                generateAndSendOTP(email);
                            }
                        } else {
                            playMascot("success.json");
                            proceedToDashboard();
                        }
                    } else {
                        triggerHapticFeedback();
                        showCustomToast("Please verify your email before logging in.");
                        if (user != null) user.sendEmailVerification();
                        playMascot("fail.json");
                        resetLoginState();
                    }
                } else {
                    triggerHapticFeedback();
                    showCustomToast("Login Failed: " + task.getException().getMessage());
                    playMascot("fail.json");
                    resetLoginState();
                }
            });
        });

        signUpButton.setOnClickListener(v -> startActivity(new Intent(this, SignUpActivity.class)));
        forgotPasswordButton.setOnClickListener(v -> startActivity(new Intent(this, ForgotUser.class)));
    }

    private void generateAndSendOTP(String email) {
        String otp = String.format("%06d", new Random().nextInt(999999));

        sharedPreferences.edit()
                .putString(KEY_PENDING_OTP, otp)
                .putLong(KEY_LAST_OTP_VERIFIED, 0)
                .apply();

        emailExecutor.execute(() -> {
            String subject = "Your OTP Code For BioAuth";
            String body = "Your OTP is: " + otp + "\n\nUse this code to verify your login.";
            GmailSender.sendEmailAsync(email, subject, body, success ->
                    Log.d(TAG, success ? "✅ OTP email sent" : "❌ OTP email failed")
            );
        });

        Intent intent = new Intent(this, OtpVerificationActivity.class);
        intent.putExtra("email", email);
        intent.putExtra("otp", otp);
        startActivity(intent);
        finish();
    }

    private void proceedToDashboard() {
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(this, MainMenu.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }, 3000);
    }

    private void triggerHapticFeedback() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(200);
            }
        }
    }

    private void playMascot(String jsonFileName) {
        if (mascotView != null) {
            mascotView.setAnimation(jsonFileName);
            mascotView.playAnimation();
            if (!jsonFileName.equals("idle.json")) {
                new Handler().postDelayed(() -> {
                    mascotView.setAnimation("idle.json");
                    mascotView.playAnimation();
                }, 4000);
            }
        }
    }

    private void disableInputs() {
        emailInput.setEnabled(false);
        passwordInput.setEnabled(false);
        loginButton.setEnabled(false);
        togglePassword.setEnabled(false);
        rememberMeCheckBox.setEnabled(false);
        signUpButton.setEnabled(false);
        forgotPasswordButton.setEnabled(false);
    }

    private void enableInputs() {
        emailInput.setEnabled(true);
        passwordInput.setEnabled(true);
        loginButton.setEnabled(true);
        togglePassword.setEnabled(true);
        rememberMeCheckBox.setEnabled(true);
        signUpButton.setEnabled(true);
        forgotPasswordButton.setEnabled(true);
    }

    private void resetLoginState() {
        loginInProgress = false;
        enableInputs();
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

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            view.clearFocus();
        }
    }

    @Override
    protected void onDestroy() {
        emailExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            View view = getCurrentFocus();
            if (view instanceof EditText) {
                android.graphics.Rect outRect = new android.graphics.Rect();
                view.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    view.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}
