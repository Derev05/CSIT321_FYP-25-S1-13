package com.example.bioauth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import android.app.AlertDialog;


public class CoverActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private TextView reviewSummaryText;
    private static final String TAG = "CoverActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cover);

        View startButton = findViewById(R.id.startButton);
        TextView web = findViewById(R.id.browseWeb);
        reviewSummaryText = findViewById(R.id.reviewSummaryText); // ✅ Clickable review summary
        db = FirebaseFirestore.getInstance();

        web.setOnClickListener(view ->
        {
            String url = "https://marcalmeda1999.wixsite.com/bioauthfyp25s113";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse(url));
            startActivity(intent);
        });

        // ✅ Start Button: Navigates to LoginActivity
        startButton.setOnClickListener(view -> {
            Intent intent = new Intent(CoverActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        // ✅ Clickable Review Summary: Navigates to ReviewDisplayActivity
        reviewSummaryText.setOnClickListener(view -> {
            Intent intent = new Intent(CoverActivity.this, ReviewDisplayActivity.class);
            startActivity(intent);
            finish();
        });

        // ✅ Fetch and display total number of reviews & average rating
        loadReviewData();
    }

    @Override
    public void onBackPressed() {
        // You can show a dialog to ask if the user wants to exit or just call super to exit normally
        new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to exit?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, id) -> super.onBackPressed())  // Normal back press
                .setNegativeButton("No", null)
                .show();
    }


    private void loadReviewData() {
        db.collection("reviews").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                QuerySnapshot querySnapshot = task.getResult();
                if (querySnapshot != null && !querySnapshot.isEmpty()) {
                    int totalReviews = querySnapshot.size();
                    double totalRating = 0;

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        Long ratingValue = document.getLong("rating");
                        if (ratingValue != null) {
                            totalRating += ratingValue;
                        }
                    }

                    double averageRating = totalRating / totalReviews;
                    String formattedRating = String.format("%.1f", averageRating);
                    reviewSummaryText.setText(formattedRating + " ★ \n" + totalReviews + " reviews");
                } else {
                    reviewSummaryText.setText("No reviews yet");
                }
            } else {
                Log.e(TAG, "❌ Error loading reviews: ", task.getException());
                reviewSummaryText.setText("Reviews unavailable");
            }
        });
    }
}
