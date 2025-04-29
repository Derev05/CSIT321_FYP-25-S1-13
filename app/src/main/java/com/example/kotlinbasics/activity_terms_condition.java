package com.example.kotlinbasics;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.LoadAdError;


import java.util.concurrent.Executor;

public class activity_terms_condition extends AppCompatActivity {

    private InterstitialAd interstitialAd;
    private String userStatus = "free";
    private Button decline, acknowledge;
    private CheckBox agree;
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private boolean hasSecureDeviceAuth = false;
    private FirebaseAuth mAuth;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms_condition);

        TextView termsTextView = findViewById(R.id.termsText);
        termsTextView.setText(Html.fromHtml(getString(R.string.terms_conditions_text), Html.FROM_HTML_MODE_LEGACY));

        acknowledge = findViewById(R.id.acknowledgeButton);
        decline = findViewById(R.id.declineButton);
        agree = findViewById(R.id.agreeCheckbox);
        mAuth = FirebaseAuth.getInstance();

        MobileAds.initialize(this, initializationStatus -> {});
        loadInterstitialAd();
        setupBiometricOrPasswordFallback();

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String status = doc.getString("status");
                if (status != null) userStatus = status;
            }
        });

        decline.setOnClickListener(view -> {
            startActivity(new Intent(this, MainMenu.class));
            finish();
        });

        acknowledge.setOnClickListener(v -> {
            if (!agree.isChecked()) {
                showCustomToast(this, "⚠ Please agree to the Terms & Conditions to proceed");
                return;
            }

            if ("free".equalsIgnoreCase(userStatus)) {
                acknowledge.setEnabled(false);
                decline.setEnabled(false);

                if (interstitialAd != null) {
                    interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            acknowledge.setEnabled(true);
                            decline.setEnabled(true);
                            authenticateSecurely();
                            loadInterstitialAd(); // ✅ Reload ad after shown
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(AdError adError) {
                            acknowledge.setEnabled(true);
                            decline.setEnabled(true);
                            authenticateSecurely();
                            loadInterstitialAd(); // ✅ Retry loading
                        }
                    });
                    interstitialAd.show(this);

                } else {
                    showCustomToast(this, "⚠ Ad not ready. Proceeding in 5 seconds...");
                    new Handler().postDelayed(() -> {
                        acknowledge.setEnabled(true);
                        decline.setEnabled(true);
                        authenticateSecurely();
                    }, 5000);
                }
            } else {
                authenticateSecurely(); // Premium user skips disable
            }
        });
    }

    private void setupBiometricOrPasswordFallback() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int authStatus = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL
        );

        hasSecureDeviceAuth = (authStatus == BiometricManager.BIOMETRIC_SUCCESS);

        executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                goToEnroll();
            }

            @Override public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                showCustomToast(activity_terms_condition.this, "❌ Authentication error: " + errString);
            }

            @Override public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                showCustomToast(activity_terms_condition.this, "❌ Authentication failed");
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Acknowledge")
                .setSubtitle("Use device biometrics or password")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
    }

    private void authenticateSecurely() {
        if (hasSecureDeviceAuth) {
            // Prompt user to choose authentication method
            new AlertDialog.Builder(this)
                    .setTitle("Choose Authentication Method to acknowledge")
                    .setMessage("Which do you prefer?")
                    .setPositiveButton("Biometric / Device", (dialog, which) -> biometricPrompt.authenticate(promptInfo))
                    .setNegativeButton("App Password", (dialog, which) -> promptPasswordFallback())
                    .setCancelable(false)
                    .show();
        } else {
            // If no biometric available, fallback directly to password
            promptPasswordFallback();
        }
    }


    private void promptPasswordFallback() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(50, 20, 50, 20);

        EditText input = new EditText(this);
        input.setHint("Enter your password");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(inputParams);

        CheckBox showPassword = new CheckBox(this);
        showPassword.setText("Show");
        showPassword.setPadding(20, 0, 0, 0);

        layout.addView(input);
        layout.addView(showPassword);

        showPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            input.setSelection(input.getText().length()); // move cursor to the end
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Enter Password")
                .setMessage("Your device doesn't support biometrics or you prefer using app password.\nEnter your app password to continue.")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("Acknowledge", null)
                .setNegativeButton("Cancel", (d, which) -> {
                    acknowledge.setEnabled(true);
                    decline.setEnabled(true);
                    d.dismiss();
                })
                .create();

        dialog.setOnShowListener(d -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(v -> {
                String password = input.getText().toString().trim();
                if (password.isEmpty()) {
                    input.setError("Password cannot be empty");
                    return;
                }

                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null && user.getEmail() != null) {
                    AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);
                    user.reauthenticate(credential).addOnSuccessListener(task -> {
                        dialog.dismiss();
                        goToEnroll();
                    }).addOnFailureListener(e -> {
                        input.setError("❌ Incorrect password");
                        acknowledge.setEnabled(true);
                        decline.setEnabled(true);
                    });
                }
            });
        });

        dialog.show();
    }



    private void goToEnroll() {
        Intent intent = new Intent(this, enroll_auth.class);
        startActivity(intent);
        finish();
    }

    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(this, "ca-app-pub-4621437870843076/2525805576", adRequest,
                new InterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                    }

                    @Override public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                        interstitialAd = null;
                    }
                });
    }

    private void showCustomToast(Context context, String message) {
        View layout = LayoutInflater.from(context).inflate(R.layout.custom_toast_layout, findViewById(R.id.toastText));
        TextView text = layout.findViewById(R.id.toastText);
        text.setText(message);
        Toast toast = new Toast(context);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.setGravity(Gravity.CENTER, 0, 700);
        toast.show();
    }

    @Override
    public void onBackPressed() {
        // ❌ Disabled
    }
}
