package com.example.kotlinbasics;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.opencv.android.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class FaceEnrollActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private static final String MODEL_NAME = "models/anti_spoofing_model.tflite";
    private static final String MODEL_VERSION_FILE = "models/model_version.txt";

    private JavaCameraView javaCameraView;
    private Button enrollFaceButton;
    private TextView modelStatusText;
    private Mat mRgba, grayFrame;
    private CascadeClassifier faceDetector;
    private Rect detectedFace;
    private int absoluteFaceSize;
    private AntiSpoofingClassifier antiSpoofingClassifier;
    private boolean isModelLoaded = false;
    private FirebaseFirestore db;
    private int consecutiveSpoofCount = 0;
    private static final int SPOOF_THRESHOLD = 3;
    private Toast activeToast;

    private void showInstantToast(String message) {
        if (activeToast != null) activeToast.cancel();
        activeToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        activeToast.show();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_enroll);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        FirebaseApp.initializeApp(this);
        db = FirebaseFirestore.getInstance();

        javaCameraView = findViewById(R.id.camera_view);
        enrollFaceButton = findViewById(R.id.enroll_face_button);
        modelStatusText = findViewById(R.id.model_status);

        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCV", "Initialization failed.");
        } else {
            requestCameraPermission();
            checkModelStatus();
        }

        enrollFaceButton.setOnClickListener(v -> {
            if (detectedFace != null && isModelLoaded) {
                captureAndVerify();
            } else {
                showInstantToast("No face or model not ready.");
            }
        });
    }

    private void captureAndVerify() {
        if (mRgba == null || detectedFace == null) return;

        Mat croppedFace = new Mat(mRgba, detectedFace);
        Bitmap faceBitmap = Bitmap.createBitmap(croppedFace.cols(), croppedFace.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(croppedFace, faceBitmap);

        boolean isReal = antiSpoofingClassifier.isRealFace(faceBitmap);
        float probability = antiSpoofingClassifier.getLastProbability();

        if (isReal) {
            Log.i("AntiSpoof", "✅ Real face. Confidence: " + probability);
            consecutiveSpoofCount = 0;
            proceedWithEnrollment(faceBitmap, false);
        } else {
            Log.w("AntiSpoof", "⚠️ Spoof detected. Confidence: " + probability);
            consecutiveSpoofCount++;
            runOnUiThread(() -> showInstantToast("Spoof detected! Attempt " + consecutiveSpoofCount));

            if (consecutiveSpoofCount >= SPOOF_THRESHOLD) {
                if (javaCameraView != null) javaCameraView.disableView();

                runOnUiThread(() -> new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Are you wearing specs?")
                        .setMessage("You were flagged 3 times. If you're real, tap YES to continue.")
                        .setCancelable(false)
                        .setPositiveButton("YES", (dialog, which) -> {
                            // Proceed and only save 3rd log as false_negative
                            proceedWithEnrollment(faceBitmap, true); // ❗ Don't log here — only FaceConfirm will
                            consecutiveSpoofCount = 0;
                        })
                        .setNegativeButton("NO", (dialog, which) -> {
                            enableCamera(); // let user retry
                            consecutiveSpoofCount = 0;
                        })
                        .show());
            } else {
                saveLog(probability, true, false); // Log 1st and 2nd normally
            }
        }
    }

    private void proceedWithEnrollment(Bitmap faceBitmap, boolean fromFalseNegative) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        faceBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();

        Intent intent = new Intent(this, FaceConfirmActivity.class);
        intent.putExtra("face", new SerializableRect(detectedFace));
        intent.putExtra("image", byteArray);
        intent.putExtra("fromFalseNegative", fromFalseNegative); // Tell confirm activity
        startActivity(intent);
    }

    private void saveLog(float probability, boolean isSpoof, boolean isFalseNegative) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;

        String userId = auth.getCurrentUser().getUid();
        String email = auth.getCurrentUser().getEmail();
        long timestamp = System.currentTimeMillis();

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Singapore"));
        String readableTime = sdf.format(new Date(timestamp));

        Map<String, Object> log = new HashMap<>();
        log.put("userId", userId);
        log.put("email", email);
        log.put("probability", probability);
        log.put("decision", isSpoof);
        log.put("timestamp", timestamp);
        log.put("readable_time", readableTime);
        if (isFalseNegative) {
            log.put("false_negative", true);
        }

        db.collection("enrol_logs")
                .document(userId)
                .set(Collections.singletonMap("initialized", true), SetOptions.merge())
                .addOnSuccessListener(aVoid -> db.collection("enrol_logs")
                        .document(userId)
                        .collection("attempts")
                        .add(log)
                        .addOnSuccessListener(docRef -> Log.d("FaceEnroll", "✅ Log saved"))
                        .addOnFailureListener(e -> Log.e("FaceEnroll", "❌ Failed to save log", e)));
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            enableCamera();
        }
    }

    private void enableCamera() {
        javaCameraView.setCameraPermissionGranted();
        javaCameraView.setCameraIndex(1);
        javaCameraView.setCvCameraViewListener(this);
        javaCameraView.enableView();
        loadCascade();
    }

    private void loadCascade() {
        try {
            InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_default);
            File cascadeDir = getDir("cascade", MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, "haarcascade_frontalface_default.xml");

            FileOutputStream os = new FileOutputStream(cascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }

            is.close(); os.close();

            faceDetector = new CascadeClassifier(cascadeFile.getAbsolutePath());
            if (faceDetector.empty()) Log.e("Cascade", "Failed to load cascade.");
        } catch (IOException e) {
            Log.e("Cascade", "Error loading cascade", e);
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
        grayFrame = new Mat();
        absoluteFaceSize = (int) (height * 0.3);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        javaCameraView.setMaxFrameSize(metrics.widthPixels, metrics.heightPixels);
    }

    @Override
    public void onCameraViewStopped() {
        if (mRgba != null) mRgba.release();
        if (grayFrame != null) grayFrame.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        grayFrame = new Mat();
        Imgproc.cvtColor(mRgba, grayFrame, Imgproc.COLOR_RGBA2GRAY);
        Core.flip(mRgba, mRgba, +1);
        Core.flip(grayFrame, grayFrame, +1);

        MatOfRect faces = new MatOfRect();
        if (faceDetector != null) {
            faceDetector.detectMultiScale(grayFrame, faces, 1.1, 2, 2, new Size(absoluteFaceSize, absoluteFaceSize), new Size());
        }

        Rect[] facesArray = faces.toArray();
        if (facesArray.length > 0) {
            detectedFace = facesArray[0];
            Imgproc.rectangle(mRgba, detectedFace.tl(), detectedFace.br(), new Scalar(255, 255, 0, 255), 3);
        } else {
            detectedFace = null;
        }

        return mRgba;
    }

    private void checkModelStatus() {
        if (isModelDownloaded()) {
            initializeModel();
        } else {
            downloadAntiSpoofingModel();
        }
        checkModelVersion();
    }

    private void initializeModel() {
        try {
            antiSpoofingClassifier = new AntiSpoofingClassifier(getModelFile());
            isModelLoaded = true;
            updateModelStatus("Security active");
        } catch (IOException e) {
            updateModelStatus("Security init failed");
        }
    }

    private File getModelFile() {
        return new File(getFilesDir(), MODEL_NAME.substring(MODEL_NAME.lastIndexOf('/') + 1));
    }

    private boolean isModelDownloaded() {
        File modelFile = getModelFile();
        return modelFile.exists() && modelFile.length() > 0 && modelFile.getName().endsWith(".tflite");
    }

    private void downloadAntiSpoofingModel() {
        updateModelStatus("Downloading model...");
        StorageReference modelRef = FirebaseStorage.getInstance().getReference(MODEL_NAME);
        File localFile = getModelFile();

        modelRef.getFile(localFile)
                .addOnSuccessListener(taskSnapshot -> initializeModel())
                .addOnFailureListener(e -> updateModelStatus("Download failed"));
    }

    private void checkModelVersion() {
        FirebaseStorage.getInstance().getReference(MODEL_VERSION_FILE)
                .getBytes(1024)
                .addOnSuccessListener(bytes -> {
                    String remoteVersion = new String(bytes);
                    String localVersion = getPreferences(MODE_PRIVATE).getString("model_version", "0");
                    if (!remoteVersion.equals(localVersion)) {
                        downloadAntiSpoofingModel();
                    }
                });
    }

    private void updateModelStatus(String msg) {
        runOnUiThread(() -> modelStatusText.setText(msg));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initLocal() && javaCameraView != null) {
            javaCameraView.enableView();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (javaCameraView != null) javaCameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null) javaCameraView.disableView();
        if (antiSpoofingClassifier != null) antiSpoofingClassifier.close();
    }
}
