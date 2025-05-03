package com.example.kotlinbasics;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

public class OtpVerificationActivity extends AppCompatActivity {

    private EditText otpInput;
    private Button resendOtpButton, verifyOtpButton;
    private ProgressBar loadingSpinner;
    private TextView emailDisplay;

    private String generatedOtp, userEmail;
    private long otpGeneratedTime;
    private CountDownTimer countDownTimer;

    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_LAST_OTP_VERIFIED = "last_otp_verified";
    private static final String KEY_PENDING_OTP = "pending_otp";
    private static final String KEY_OTP_CREATED_TIME = "otp_created_time";
    private static final long OTP_EXPIRATION_TIME = 60 * 60 * 1000; // 1 hour
    private static final long RESEND_COOLDOWN = 60 * 1000; // 1 minute
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_login);

        otpInput = findViewById(R.id.otpInput);
        resendOtpButton = findViewById(R.id.resendOtpButton);
        verifyOtpButton = findViewById(R.id.verifyOtpButton);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        emailDisplay = findViewById(R.id.emailDisplay);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        userEmail = getIntent().getStringExtra("email");
        generatedOtp = sharedPreferences.getString(KEY_PENDING_OTP, "");
        otpGeneratedTime = sharedPreferences.getLong(KEY_OTP_CREATED_TIME, 0);

        emailDisplay.setText("Email: " + userEmail);
        showToast("📨 OTP sent to " + userEmail + ". Valid for 1 hour.");
        startResendCooldown();

        otpInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() == 6) verifyOtp(s.toString().trim());
            }
        });

        verifyOtpButton.setOnClickListener(v -> {
            verifyOtp(otpInput.getText().toString().trim());
        });

        resendOtpButton.setOnClickListener(v -> {
            resendOtpButton.setEnabled(false);
            sendOtp(userEmail);
        });
    }

    private void verifyOtp(String enteredOtp) {
        long now = System.currentTimeMillis();
        if ((now - otpGeneratedTime) > OTP_EXPIRATION_TIME) {
            showToast("❌ OTP expired. Please resend a new one.");
            sharedPreferences.edit().remove(KEY_PENDING_OTP).remove(KEY_OTP_CREATED_TIME).apply();
            return;
        }

        if (enteredOtp.equals(generatedOtp)) {
            sharedPreferences.edit()
                    .putLong(KEY_LAST_OTP_VERIFIED, now)
                    .remove(KEY_PENDING_OTP)
                    .remove(KEY_OTP_CREATED_TIME)
                    .apply();

            startActivity(new Intent(this, MainMenu.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        } else {
            showToast("❌ Incorrect OTP. Try again.");
        }
    }

    private void sendOtp(String email) {
        loadingSpinner.setVisibility(View.VISIBLE);
        generatedOtp = GmailSender.generateOTP();
        otpGeneratedTime = System.currentTimeMillis();

        sharedPreferences.edit()
                .putString(KEY_PENDING_OTP, generatedOtp)
                .putLong(KEY_OTP_CREATED_TIME, otpGeneratedTime)
                .apply();

        GmailSender.sendEmailAsync(email, "Your OTP Code", "Your OTP is: " + generatedOtp, success -> {
            runOnUiThread(() -> {
                loadingSpinner.setVisibility(View.GONE);
                if (success) {
                    showToast("✅ New OTP sent to " + email);
                    startResendCooldown();
                } else {
                    showToast("❌ Failed to send OTP.");
                    resendOtpButton.setEnabled(true);
                }
            });
        });
    }

    private void startResendCooldown() {
        resendOtpButton.setEnabled(false);
        countDownTimer = new CountDownTimer(RESEND_COOLDOWN, 1000) {
            public void onTick(long millisUntilFinished) {
                resendOtpButton.setText("Wait " + (millisUntilFinished / 1000) + "s");
            }
            public void onFinish() {
                resendOtpButton.setText("Resend OTP");
                resendOtpButton.setEnabled(true);
            }
        }.start();
    }

    @Override
    public void onBackPressed() {
        sharedPreferences.edit().remove(KEY_LAST_OTP_VERIFIED).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        if (countDownTimer != null) countDownTimer.cancel();
        super.onDestroy();
    }

    private void showToast(String msg) {
        View layout = LayoutInflater.from(this).inflate(R.layout.custom_toast_layout, findViewById(R.id.toastText));
        ((TextView) layout.findViewById(R.id.toastText)).setText(msg);
        Toast toast = new Toast(this);
        toast.setView(layout);
        toast.setGravity(Gravity.BOTTOM, 0, 100);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.show();
    }
}
