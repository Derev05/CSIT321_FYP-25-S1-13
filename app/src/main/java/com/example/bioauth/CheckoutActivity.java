package com.example.bioauth;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class CheckoutActivity extends AppCompatActivity {

    private EditText cardNumberInput, expiryInput, cvcInput;
    private Button purchaseButton;
    private ImageButton backButton;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        // Initialize views
        cardNumberInput = findViewById(R.id.cardNumberInput);
        expiryInput = findViewById(R.id.expiryInput);
        cvcInput = findViewById(R.id.cvcInput);
        purchaseButton = findViewById(R.id.purchaseButton);
        backButton = findViewById(R.id.backButton);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Set up back button click listener
        backButton.setOnClickListener(v -> {
            // Close current activity and return to previous one
            finish();
        });

        purchaseButton.setOnClickListener(v -> {
            String card = cardNumberInput.getText().toString().trim();
            String expiry = expiryInput.getText().toString().trim();
            String cvc = cvcInput.getText().toString().trim();

            if (card.isEmpty() || expiry.isEmpty() || cvc.isEmpty()) {
                showToast("Please fill in all fields.");
                return;
            }

            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                db.collection("users").document(user.getUid())
                        .update("status", "premium")
                        .addOnSuccessListener(unused -> {
                            showToast("🎉 You're now a premium user!");
                            startActivity(new Intent(CheckoutActivity.this, MainMenu.class));
                            finish();
                        })
                        .addOnFailureListener(e -> showToast("Failed to update premium status."));
            } else {
                showToast("User not logged in.");
            }
        });
    }

    private void showToast(String msg) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast_layout,
                findViewById(android.R.id.content), false);
        TextView text = layout.findViewById(R.id.toastText);
        text.setText(msg);
        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.setGravity(Gravity.BOTTOM, 0, 150);
        toast.show();
    }
}