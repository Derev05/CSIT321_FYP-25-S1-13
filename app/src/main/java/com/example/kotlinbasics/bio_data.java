package com.example.kotlinbasics;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class bio_data extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private void showCustomToast (String message){
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(android.R.id.content), false);

        TextView text = layout.findViewById(R.id.toastText);
        text.setText(message);

        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.setGravity(Gravity.BOTTOM, 0, 150);
        toast.show();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms_condition);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();


        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String uid = user.getUid();
            FirebaseFirestore.getInstance().collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String userType = documentSnapshot.getString("userType");
                    if ("regular".equalsIgnoreCase(userType)) {
                        showUpgradePopup();

                    }
                }
            });
        }


        TextView termsCondition = findViewById(R.id.termsText);
        termsCondition.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ✅ Start Terms & Conditions Activity
                Intent intent = new Intent(bio_data.this, activity_terms_condition.class);
                startActivity(intent);
            }
        });

    }


    private void showUpgradePopup() {
        Dialog dialog = new Dialog(bio_data.this);
        dialog.setContentView(R.layout.upgrade_premium);  // your XML layout
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCancelable(true);

        Button upgradeNow = dialog.findViewById(R.id.upgradeNowBtn);
        TextView laterText = dialog.findViewById(R.id.laterText);

        upgradeNow.setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                String uid = user.getUid();

                Map<String, Object> userData = new HashMap<>();
                userData.put("email", user.getEmail());
                userData.put("userType", "Premium");

                db.collection("users").document(uid).update(userData)
                        .addOnSuccessListener(aVoid -> {
                            showCustomToast("🎉 You're now a Premium user!");
                            dialog.dismiss();
                            Intent intent = new Intent(bio_data.this, MainMenu.class);
                            startActivity(intent);
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            showCustomToast("❌ Failed to upgrade. Try again.");
                            Log.e("FIRESTORE", "Upgrade failed", e);
                        });
            } else {
                showCustomToast("⚠ User not logged in.");
                dialog.dismiss();
            }
        });

        laterText.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

    }
}