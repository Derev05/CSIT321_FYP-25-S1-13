package com.example.kotlinbasics;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class reviews_data extends AppCompatActivity {

    private RatingBar ratingBar;
    private EditText reviewInput;
    private RadioGroup recommendGroup;
    private Button submitReviewButton, deleteReviewButton, backToHomeButton;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private boolean isUpdating = false;  // ✅ Track if user is updating

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);

        // ✅ Initialize Firestore & Firebase Auth
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // ✅ Initialize Views
        ratingBar = findViewById(R.id.ratingBar);
        reviewInput = findViewById(R.id.reviewInput);
        recommendGroup = findViewById(R.id.recommendGroup);
        submitReviewButton = findViewById(R.id.submitReviewButton);
        deleteReviewButton = findViewById(R.id.deleteReviewButton);
        backToHomeButton = findViewById(R.id.backToHomeButton);

        // ✅ Check if User Has an Existing Review
        checkExistingReview();

        // ✅ Submit/Update Review Button Click
        submitReviewButton.setOnClickListener(v -> submitOrUpdateReview());

        // ✅ Delete Review Button Click
        deleteReviewButton.setOnClickListener(v -> deleteReview());

        // ✅ Back to Home Button Click
        backToHomeButton.setOnClickListener(v -> {
            Intent intent = new Intent(reviews_data.this, MainMenu.class);
            startActivity(intent);
            finish();
        });
    }

    private void checkExistingReview() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Log.e("Firestore", "User is not logged in!");
            return;
        }

        String userId = user.getUid();
        db.collection("reviews").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Log.d("Firestore", "✅ Existing review found!");
                        isUpdating = true;

                        // ✅ Load existing review data
                        ratingBar.setRating(documentSnapshot.getDouble("rating").floatValue());
                        reviewInput.setText(documentSnapshot.getString("reviewText"));
                        String recommend = documentSnapshot.getString("recommend");

                        if ("Yes".equals(recommend)) {
                            recommendGroup.check(R.id.recommendYes);
                        } else {
                            recommendGroup.check(R.id.recommendNo);
                        }

                        // ✅ Update UI
                        submitReviewButton.setText("Update Review");
                        deleteReviewButton.setVisibility(View.VISIBLE); // Show Delete button
                    } else {
                        Log.d("Firestore", "No existing review found.");
                    }
                })
                .addOnFailureListener(e -> Log.e("Firestore", "Error checking for existing review", e));
    }

    private void submitOrUpdateReview() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showCustomToast("You must be logged in to submit a review.");
            return;
        }

        String userId = user.getUid();
        String userEmail = user.getEmail();
        float rating = ratingBar.getRating();
        String reviewText = reviewInput.getText().toString().trim();
        int selectedRecommendId = recommendGroup.getCheckedRadioButtonId();
        String recommend = (selectedRecommendId == R.id.recommendYes) ? "Yes" : (selectedRecommendId == R.id.recommendNo) ? "No" : "";

        if (rating == 0 || reviewText.isEmpty() || recommend.isEmpty()) {
            showCustomToast("Please enter a rating, review, and recommendation choice");
            return;
        }

        // ✅ Prepare Review Data
        Map<String, Object> review = new HashMap<>();
        review.put("userId", userId);
        review.put("email", userEmail);
        review.put("rating", rating);
        review.put("reviewText", reviewText);
        review.put("recommend", recommend);
        review.put("timestamp", System.currentTimeMillis());

        db.collection("reviews").document(userId).set(review)
                .addOnSuccessListener(aVoid -> {
                    showCustomToast(isUpdating ? "Review updated successfully!" : "Review submitted successfully!");

                    if (!isUpdating) {
                        checkExistingReview();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Firestore", "Error submitting review: " + e.getMessage(), e);
                    showCustomToast("Error: " + e.getMessage());
                });
    }

    private void deleteReview() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showCustomToast("You must be logged in to delete your review.");
            return;
        }

        String userId = user.getUid();

        db.collection("reviews").document(userId).delete()
                .addOnSuccessListener(aVoid -> {
                    showCustomToast("Review deleted successfully!");

                    // ✅ Reset UI after deletion
                    reviewInput.setText("");
                    ratingBar.setRating(0);
                    recommendGroup.clearCheck();
                    submitReviewButton.setText("Submit Review");
                    deleteReviewButton.setVisibility(View.GONE);
                    isUpdating = false;
                })
                .addOnFailureListener(e -> {
                    Log.e("Firestore", "Error deleting review: " + e.getMessage(), e);
                    showCustomToast("Error: " + e.getMessage());
                });
    }

    private void showCustomToast(String message) {
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
}
