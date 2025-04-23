package com.example.kotlinbasics;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class education_activity extends AppCompatActivity {

    private FirebaseFirestore db;
    private TextView summaryText, livenessValue;
    private ImageView phoneVideo;

    private int totalLogs = 0;
    private int spoofed = 0;
    private int falseNegative = 0;
    private int falsePositive = 0;
    private float rejectionRate = 0f;
    private float fnr = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.educational_library);

        summaryText = findViewById(R.id.summaryTitle);
        livenessValue = findViewById(R.id.livenessValue);
        phoneVideo = findViewById(R.id.phone_vid);


        Glide.with(this)
                .asGif()
                .load(R.drawable.spoofvideo) // Use the filename without extension
                .into(phoneVideo);


        db = FirebaseFirestore.getInstance();
        fetchLogData();
    }

    private void fetchLogData() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("enrol_logs")
                .document(uid)
                .collection("attempts")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> logs = snapshot.getDocuments();
                    totalLogs = logs.size();

                    for (DocumentSnapshot doc : logs) {
                        boolean isSpoof = doc.contains("decision") && Boolean.TRUE.equals(doc.getBoolean("decision"));
                        boolean isFalseNegative = doc.contains("false_negative") && Boolean.TRUE.equals(doc.getBoolean("false_negative"));
                        boolean isFalsePositive = doc.contains("false_positive") && Boolean.TRUE.equals(doc.getBoolean("false_positive"));

                        if (isSpoof) {
                            spoofed++;
                            if (isFalseNegative) {
                                falseNegative++;
                            }
                        }

                        if (!isSpoof && isFalsePositive) {
                            falsePositive++;
                        }
                    }

                    rejectionRate = totalLogs > 0 ? (float) spoofed / totalLogs * 100f : 0f;
                    fnr = spoofed > 0 ? (float) falseNegative / spoofed * 100f : 0f;

                    updateSummaryText();
                })
                .addOnFailureListener(e -> summaryText.setText("⚠️ Failed to load summary."));
    }

    private void updateSummaryText() {
        String summary = String.format(
                "Out of %d logs, %d were identified as spoofed, with %d flagged by users as real faces wrongfully detected as spoofed.\n\n" +
                        "This puts the current spoof detection model at %.2f%% rejection rate with a %.2f%% false negative rate.",
                totalLogs, spoofed, falseNegative, rejectionRate, fnr
        );

        summaryText.setText(summary);
        livenessValue.setText("Based on " + totalLogs + " attempts");
    }
}
