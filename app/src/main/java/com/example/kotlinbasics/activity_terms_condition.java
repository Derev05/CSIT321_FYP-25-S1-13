package com.example.kotlinbasics;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class activity_terms_condition extends AppCompatActivity {

    private InterstitialAd interstitialAd;
    private String userStatus = "free"; // Default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms_condition);

        TextView termsTextView = findViewById(R.id.termsText);
        termsTextView.setText(Html.fromHtml(getString(R.string.terms_conditions_text), Html.FROM_HTML_MODE_LEGACY));

        CheckBox agree = findViewById(R.id.agreeCheckbox);
        Button acknowledge = findViewById(R.id.acknowledgeButton);
        Button decline = findViewById(R.id.declineButton);

        // Initialize Mobile Ads
        MobileAds.initialize(this, initializationStatus -> {});
        loadInterstitialAd();

        // Load user status from Firestore
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String status = doc.getString("status");
                if (status != null) userStatus = status;
            }
        });

        // Decline button
        decline.setOnClickListener(view -> {
            startActivity(new Intent(activity_terms_condition.this, MainMenu.class));
            finish();
        });

        // Acknowledge button
        acknowledge.setOnClickListener(v -> {
            if (!agree.isChecked()) {
                showCustomToast(this, "⚠ Please agree to the Terms & Conditions to proceed");
                return;
            }

            acknowledge.setEnabled(false); // ✅ Prevent spamming multiple clicks

            if ("free".equalsIgnoreCase(userStatus)) {
                if (interstitialAd != null) {
                    interstitialAd.setFullScreenContentCallback(new com.google.android.gms.ads.FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            goToEnroll();
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                            goToEnroll();
                        }
                    });
                    interstitialAd.show(activity_terms_condition.this);
                } else {
                    showCustomToast(this, "⚠ Ad not ready. Proceeding in 5 seconds...");
                    new Handler().postDelayed(this::goToEnroll, 5000);
                }
            } else {
                goToEnroll(); // Premium user
            }
        });
    }

    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(this, "ca-app-pub-4621437870843076/2525805576", adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError adError) {
                        interstitialAd = null;
                    }
                });
    }

    private void goToEnroll() {
        Intent intent = new Intent(activity_terms_condition.this, enroll_auth.class);
        startActivity(intent);
        finish();
    }

    private void showCustomToast(Context context, String message) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(R.id.toastText));
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
        startActivity(new Intent(activity_terms_condition.this, MainMenu.class));
        finish();
    }
}
