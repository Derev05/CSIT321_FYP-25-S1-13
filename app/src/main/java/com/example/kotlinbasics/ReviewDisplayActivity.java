package com.example.kotlinbasics;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReviewDisplayActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView noReviewsText;
    private Button backButton; // ✅ Back Button
    private FirebaseFirestore db;
    private List<ReviewModel> reviewList;
    private ReviewAdapter reviewAdapter;
    private static final String TAG = "ReviewDisplayActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review_display);

        // ✅ Initialize Views
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        noReviewsText = findViewById(R.id.noReviewsText);
        backButton = findViewById(R.id.backButton); // ✅ Initialize Back Button
        db = FirebaseFirestore.getInstance();
        reviewList = new ArrayList<>();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        reviewAdapter = new ReviewAdapter(reviewList);
        recyclerView.setAdapter(reviewAdapter);

        // ✅ Load reviews from Firestore
        fetchReviews();

        // ✅ Handle Back Button Click
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(ReviewDisplayActivity.this, CoverActivity.class);
            startActivity(intent);
            finish(); // Close this activity
        });
    }

    private void fetchReviews() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("reviews").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    progressBar.setVisibility(View.GONE);
                    if (queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "🔥 No reviews found in Firestore!");
                        noReviewsText.setVisibility(View.VISIBLE);
                        return;
                    }

                    reviewList.clear(); // Clear old data before adding new
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        String email = document.getString("email");
                        String reviewText = document.getString("reviewText");
                        String recommend = document.getString("recommend");
                        Long timestamp = document.getLong("timestamp");
                        Long ratingLong = document.getLong("rating");

                        // Debug Logs
                        Log.d(TAG, "✅ Review Loaded: " + email + " | " + reviewText);

                        if (timestamp == null || ratingLong == null) {
                            Log.e(TAG, "❌ Missing data in document: " + document.getId());
                            continue; // Skip invalid data
                        }

                        int rating = ratingLong.intValue();
                        String date = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                .format(new Date(timestamp));

                        reviewList.add(new ReviewModel(email, rating, reviewText, date, recommend));
                    }

                    if (reviewList.isEmpty()) {
                        noReviewsText.setVisibility(View.VISIBLE);
                        Log.d(TAG, "🔥 No valid reviews to display!");
                    } else {
                        reviewAdapter.notifyDataSetChanged();
                        recyclerView.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "❌ Error fetching reviews: ", e);
                });
    }

}

// ✅ Review Model Class
class ReviewModel {
    private final String email;
    private final int rating;
    private final String reviewText;
    private final String date;
    private final String recommend;

    public ReviewModel(String email, int rating, String reviewText, String date, String recommend) {
        this.email = email;
        this.rating = rating;
        this.reviewText = reviewText;
        this.date = date;
        this.recommend = recommend;
    }

    public String getEmail() { return email; }
    public int getRating() { return rating; }
    public String getReviewText() { return reviewText; }
    public String getDate() { return date; }
    public String getRecommend() { return recommend; }
}

// ✅ RecyclerView Adapter
class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ViewHolder> {

    private final List<ReviewModel> reviewList;

    public ReviewAdapter(List<ReviewModel> reviewList) {
        this.reviewList = reviewList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReviewModel review = reviewList.get(position);
        holder.emailTextView.setText(review.getEmail());
        holder.ratingBar.setRating(review.getRating());
        holder.reviewTextView.setText(review.getReviewText());
        holder.dateTextView.setText(review.getDate());
        holder.recommendTextView.setText("Recommend: " + review.getRecommend());
    }

    @Override
    public int getItemCount() {
        return reviewList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView emailTextView, reviewTextView, dateTextView, recommendTextView;
        RatingBar ratingBar;

        public ViewHolder(View itemView) {
            super(itemView);
            emailTextView = itemView.findViewById(R.id.emailTextView);
            reviewTextView = itemView.findViewById(R.id.reviewTextView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            recommendTextView = itemView.findViewById(R.id.recommendTextView);
            ratingBar = itemView.findViewById(R.id.ratingBar);
        }
    }
}
