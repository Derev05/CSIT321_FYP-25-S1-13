package com.example.kotlinbasics;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
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
    private static final long OTP_EXPIRATION_TIME = 60 * 60 * 1000; // 1 hour
    private static final long RESEND_COOLDOWN = 5 * 60 * 1000; // 5 mins
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
        generatedOtp = getIntent().getStringExtra("otp");
        otpGeneratedTime = SystemClock.elapsedRealtime();

        emailDisplay.setText("Email: " + userEmail);
        showToast("Please check your inbox or spam! OTP sent to " + userEmail);
        startResendCooldown();

        resendOtpButton.setOnClickListener(v -> {
            resendOtpButton.setEnabled(false);
            sendOtp(userEmail);
        });

        verifyOtpButton.setOnClickListener(v -> {
            String enteredOtp = otpInput.getText().toString().trim();
            if (enteredOtp.equals(generatedOtp)) {
                long now = System.currentTimeMillis();
                sharedPreferences.edit().putLong(KEY_LAST_OTP_VERIFIED, now).apply();
                startActivity(new Intent(this, MainMenu.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
            } else {
                showToast("❌ Incorrect OTP. Try again.");
            }
        });
    }

    private void sendOtp(String email) {
        loadingSpinner.setVisibility(View.VISIBLE);
        generatedOtp = GmailSender.generateOTP();
        otpGeneratedTime = SystemClock.elapsedRealtime();

        GmailSender.sendEmailAsync(email, "Your OTP Code", "Your OTP is: " + generatedOtp, success -> {
            runOnUiThread(() -> {
                loadingSpinner.setVisibility(View.GONE);
                if (success) {
                    showToast("✅ OTP resent.");
                    startResendCooldown();
                } else {
                    showToast("❌ Failed to resend OTP.");
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
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_LAST_OTP_VERIFIED); // prevent OTP skip
        editor.apply();
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
