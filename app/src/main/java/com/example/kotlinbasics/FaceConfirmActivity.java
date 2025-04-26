package com.example.kotlinbasics;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.text.SimpleDateFormat;
import java.util.*;

public class FaceConfirmActivity extends AppCompatActivity {

    private ImageView faceImageView;
    private Button confirmBtn, cancelBtn;
    private Bitmap faceBitmap;
    private FaceEmbedManager embedManager;
    private boolean fromFalseNegative;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_confirm);

        faceImageView = findViewById(R.id.detected_face_image_view);
        confirmBtn = findViewById(R.id.confirm_button);
        cancelBtn = findViewById(R.id.cancel_button);
        embedManager = new FaceEmbedManager(this, "face_recognition_sface_2021dec.onnx");

        Intent intent = getIntent();
        byte[] byteArray = intent.getByteArrayExtra("image");
        fromFalseNegative = intent.getBooleanExtra("fromFalseNegative", false);
        Log.d("DEBUG_FLAG", "fromFalseNegative: " + fromFalseNegative);

        if (byteArray != null) {
            faceBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
            faceImageView.setImageBitmap(faceBitmap);
        } else {
            Log.e("FaceConfirmActivity", "❌ No face image received.");
            Toast.makeText(this, "Error loading face image", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        confirmBtn.setOnClickListener(v -> {
            if (fromFalseNegative) {
                Log.w("FaceConfirm", "⛔ No additional log saved. False negative already recorded.");
                runFaceRecognition(faceBitmap);
                Toast.makeText(this, "✅ Confirmed despite false negative", Toast.LENGTH_SHORT).show();
                goToNextScreen();
            } else {
                Log.d("FaceConfirm", "🟢 Saving real face log from FaceConfirm.");
                saveConfirmedLog();
                runFaceRecognition(faceBitmap);
                Toast.makeText(this, "✅ Face Confirmed!", Toast.LENGTH_SHORT).show();
                goToNextScreen();
            }
        });

        cancelBtn.setOnClickListener(v -> {
            Toast.makeText(this, "❌ Face Rejected!", Toast.LENGTH_SHORT).show();

            if (fromFalseNegative) {
                deleteFalseNegativeLog(() -> {
                    Log.d("FaceConfirm", "🗑️ False negative log removed after rejection.");
                    goBackToEnroll();
                });
            } else {
                goBackToEnroll();
            }
        });
    }

    private void saveConfirmedLog() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.e("FaceConfirm", "❌ No logged-in user found.");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        String email = auth.getCurrentUser().getEmail();
        long timestamp = System.currentTimeMillis();

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Singapore"));
        String readableTime = sdf.format(new Date(timestamp));

        Map<String, Object> log = new HashMap<>();
        log.put("userId", userId);
        log.put("email", email);
        log.put("probability", 1.0f);
        log.put("decision", false); // Real face
        log.put("timestamp", timestamp);
        log.put("readable_time", readableTime);
        log.put("source", "FaceConfirm");

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("enrol_logs")
                .document(userId)
                .set(Collections.singletonMap("initialized", true), SetOptions.merge())
                .addOnSuccessListener(aVoid -> db.collection("enrol_logs")
                        .document(userId)
                        .collection("attempts")
                        .add(log)
                        .addOnSuccessListener(docRef -> Log.d("FaceConfirm", "✅ Real face log saved"))
                        .addOnFailureListener(e -> Log.e("FaceConfirm", "❌ Failed to save log", e)));
    }

    private void deleteFalseNegativeLog(Runnable onComplete) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            onComplete.run();
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("enrol_logs")
                .document(userId)
                .collection("attempts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Boolean isFalseNegative = doc.getBoolean("false_negative");
                        if (Boolean.TRUE.equals(isFalseNegative)) {
                            doc.getReference().delete()
                                    .addOnSuccessListener(v -> Log.d("FaceConfirm", "🗑️ False negative log deleted"))
                                    .addOnFailureListener(e -> Log.e("FaceConfirm", "❌ Deletion failed", e));
                        }
                    }
                    onComplete.run();
                })
                .addOnFailureListener(e -> {
                    Log.e("FaceConfirm", "❌ Fetch log failed", e);
                    onComplete.run();
                });
    }

    private void runFaceRecognition(Bitmap faceBitmap) {
        try {
            Mat mat = new Mat();
            Utils.bitmapToMat(faceBitmap, mat);
            Size inputSize = new Size(112, 112);
            Imgproc.resize(mat, mat, inputSize);
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGR);
            mat.convertTo(mat, CvType.CV_32F, 1.0 / 255.0);

            Core.subtract(mat, new Scalar(127.5, 127.5, 127.5), mat);
            Core.divide(mat, new Scalar(128, 128, 128), mat);

            float[] inputArray = new float[1 * 3 * 112 * 112];
            int idx = 0;
            for (int c = 0; c < 3; c++) {
                for (int h = 0; h < 112; h++) {
                    for (int w = 0; w < 112; w++) {
                        double[] pixel = mat.get(h, w);
                        inputArray[idx++] = (float) pixel[c];
                    }
                }
            }

            float[] embedding = embedManager.generateEmbedding(inputArray);
            embedManager.uploadUserEmbedding(embedding);

        } catch (Exception e) {
            Log.e("FaceConfirmActivity", "❌ Face recognition failed", e);
            Toast.makeText(this, "Error running face recognition", Toast.LENGTH_SHORT).show();
        }
    }

    private void goToNextScreen() {
        Intent intent = new Intent(this, enroll_auth.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void goBackToEnroll() {
        Intent cancelIntent = new Intent(this, FaceEnrollActivity.class);
        cancelIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(cancelIntent);
        finish();
    }
}
