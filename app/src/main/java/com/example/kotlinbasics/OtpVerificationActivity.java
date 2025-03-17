package com.example.kotlinbasics;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class OtpVerificationActivity extends AppCompatActivity {

    private EditText otpInput;
    private Button resendOtpButton, verifyOtpButton;
    private ProgressBar loadingSpinner;
    private TextView emailDisplay;
    private String generatedOtp;
    private String userEmail;
    private long otpGeneratedTime;
    private static final String TAG = "OtpVerificationActivity";
    private static final long OTP_EXPIRATION_TIME = 10800000; // ✅ 3 hours (10800000 ms)
    private static final long OTP_RESEND_COOLDOWN = 300000; // ✅ 5 minutes (300000 ms)
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_login);

        otpInput = findViewById(R.id.otpInput);
        resendOtpButton = findViewById(R.id.resendOtpButton);
        verifyOtpButton = findViewById(R.id.verifyOtpButton);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        emailDisplay = findViewById(R.id.emailDisplay);

        // ✅ Get email and OTP from LoginActivity
        userEmail = getIntent().getStringExtra("email");
        generatedOtp = getIntent().getStringExtra("otp");
        otpGeneratedTime = SystemClock.elapsedRealtime();

        if (userEmail == null || userEmail.isEmpty()) {
            showCustomToast("Email not found. Please login again.");
            Log.e(TAG, "❌ Email not received from LoginActivity!");
            finish();
            return;
        }

        // ✅ Display user email
        emailDisplay.setText("Email: " + userEmail);

        // ✅ Show custom toast message when user enters OTP screen
        showLongCustomToast("An OTP has been sent to " + userEmail +
                ". Please check your inbox or spam folder.");

        // ✅ Start the OTP resend cooldown (5 minutes)
        startResendCooldown();

        resendOtpButton.setOnClickListener(v -> {
            resendOtpButton.setEnabled(false);
            sendOtp(userEmail);
        });

        verifyOtpButton.setOnClickListener(v -> {
            String enteredOtp = otpInput.getText().toString().trim();
            if (enteredOtp.isEmpty() || enteredOtp.length() != 6) {
                showCustomToast("Enter a valid 6-digit OTP");
                return;
            }
            verifyOtp(enteredOtp);
        });
    }

    private void sendOtp(String email) {
        loadingSpinner.setVisibility(View.VISIBLE);
        resendOtpButton.setEnabled(false);

        // ✅ Generate new OTP and update timestamp
        generatedOtp = GmailSender.generateOTP();
        otpGeneratedTime = SystemClock.elapsedRealtime();
        Log.d(TAG, "📩 New OTP Generated: " + generatedOtp);

        // ✅ Send OTP in background
        GmailSender.sendEmailAsync(email, "Your OTP Code", "Your OTP is: " + generatedOtp, success -> {
            runOnUiThread(() -> {
                loadingSpinner.setVisibility(View.GONE);
                if (success) {
                    showCustomToast("✅ New OTP Sent! Check your email.");
                    startResendCooldown(); // ✅ Restart cooldown (5 mins)
                } else {
                    showCustomToast("❌ Failed to send OTP. Try again.");
                    Log.e(TAG, "❌ Email sending failed for: " + email);
                    resendOtpButton.setEnabled(true);
                }
            });
        });
    }

    private void verifyOtp(String otp) {
        loadingSpinner.setVisibility(View.VISIBLE);
        verifyOtpButton.setEnabled(false);

        long currentTime = SystemClock.elapsedRealtime();

        // ✅ Check if OTP has expired (3 hours)
        if (currentTime - otpGeneratedTime > OTP_EXPIRATION_TIME) {
            showCustomToast("⏳ OTP expired! Request a new one.");
            loadingSpinner.setVisibility(View.GONE);
            verifyOtpButton.setEnabled(true);
            return;
        }

        if (otp.equals(generatedOtp)) {
            showCustomToast("✅ OTP Verified!");
            Log.d(TAG, "✅ OTP Verified! Redirecting to MainMenu...");
            startActivity(new Intent(this, MainMenu.class));
            finish();
        } else {
            showCustomToast("❌ Incorrect OTP. Try again.");
            verifyOtpButton.setEnabled(true);
        }

        loadingSpinner.setVisibility(View.GONE);
    }

    private void startResendCooldown() {
        resendOtpButton.setEnabled(false);
        resendOtpButton.setText("Wait 5 mins...");

        countDownTimer = new CountDownTimer(OTP_RESEND_COOLDOWN, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) (millisUntilFinished / 1000);
                int minutes = secondsRemaining / 60;
                int seconds = secondsRemaining % 60;
                resendOtpButton.setText("Wait " + minutes + "m " + seconds + "s");
            }

            @Override
            public void onFinish() {
                resendOtpButton.setEnabled(true);
                resendOtpButton.setText("Resend OTP");
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        super.onDestroy();
    }

    // ✅ Custom Toast for Full Message Visibility
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

    // ✅ Show Custom Long Toast for 10 Seconds
    private void showLongCustomToast(String message) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(R.id.toastText));

        TextView toastText = layout.findViewById(R.id.toastText);
        toastText.setText(message);

        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.BOTTOM, 0, 100);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);

        // ✅ Keep showing the custom toast multiple times to simulate 10 seconds duration
        new Handler().postDelayed(() -> toast.show(), 2000);
        new Handler().postDelayed(() -> toast.show(), 4000);
        new Handler().postDelayed(() -> toast.show(), 6000);
        new Handler().postDelayed(() -> toast.show(), 8000);
        new Handler().postDelayed(() -> toast.show(), 10000);
    }
}
