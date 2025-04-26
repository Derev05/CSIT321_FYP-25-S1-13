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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class LivenessDemoAuthActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private static final String MODEL_NAME = "models/anti_spoofing_model.tflite";
    private static final String MODEL_VERSION_FILE = "models/model_version.txt";
    private static final String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    private JavaCameraView javaCameraView;
    private Button authFaceBtn;
    private Mat mRgba, grayFrame;
    private CascadeClassifier faceDetector;
    private Rect detectedFace;
    private int absoluteFaceSize;
    private FaceEmbedManager embedManager;
    private AntiSpoofingClassifier antiSpoofingClassifier;
    private boolean isModelLoaded = false;
    private TextView modelStatusText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_auth);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        javaCameraView = findViewById(R.id.auth_camera_view);
        authFaceBtn = findViewById(R.id.auth_face_button);
        modelStatusText = findViewById(R.id.model_status);

        try {
            FirebaseApp.initializeApp(this);
        } catch (IllegalStateException e) {
            // Already initialized
            Log.d("Firebase", "Firebase already initialized");
        }

        if(!OpenCVLoader.initLocal()){
            Log.e("OpenCV", "Initialization failed.");
        } else {
            Log.d("OpenCV", "Initialization successful.");
            requestCameraPermission();
            checkModelStatus();
        }

        embedManager = new FaceEmbedManager(this, "face_recognition_sface_2021dec.onnx");

        authFaceBtn.setOnClickListener(v -> {
            if(detectedFace != null) {
                verifyUser(userId);
            } else {
                Toast.makeText(this, "No face detected, try again.", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void requestCameraPermission() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            enableCamera();
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableCamera();
            } else {
                Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void enableCamera() {
        javaCameraView.setCameraPermissionGranted();
        javaCameraView.setCameraIndex(1);
        javaCameraView.setCvCameraViewListener(this);
        javaCameraView.enableView();
        loadCascade();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
        grayFrame = new Mat();
        absoluteFaceSize = (int) (height * 0.3);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        javaCameraView.setMaxFrameSize(screenWidth,screenHeight);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        grayFrame.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        Imgproc.cvtColor(mRgba, grayFrame, Imgproc.COLOR_RGBA2GRAY);

        Core.flip(mRgba, mRgba,+1);
        Core.flip(grayFrame, grayFrame,+1);

        MatOfRect faces = new MatOfRect();
        if (faceDetector != null) {
            faceDetector.detectMultiScale(grayFrame, faces, 1.1, 2, 2,
                    new Size(absoluteFaceSize,absoluteFaceSize), new Size());
        }

        Rect[] facesArray = faces.toArray();
        if(facesArray.length > 0) {
            detectedFace = facesArray[0];
            Imgproc.rectangle(mRgba, detectedFace.tl(), detectedFace.br(), new Scalar(255,255,0,255), 3);

            // Run anti-spoofing detection
            float probability = runAntiSpoofing(mRgba, detectedFace);

            // Display probability score above the detected face
            String label = String.format("%.2f%%", probability * 100);
            int textX = (int) detectedFace.tl().x;
            int textY = (int) detectedFace.tl().y - 10;
            Imgproc.putText(mRgba, label, new org.opencv.core.Point(textX, textY),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(0, 255, 0, 255), 2);

        } else {
            detectedFace = null;
        }
        return mRgba;
    }

    private float runAntiSpoofing(Mat frame, Rect face){
        Mat croppedFace = new Mat(frame, face);
        Bitmap faceBitmap = Bitmap.createBitmap(croppedFace.cols(), croppedFace.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(croppedFace, faceBitmap);

        return antiSpoofingClassifier.getSpoofProbability(faceBitmap);
    }

    private void verifyUser(String userId) {
        if (mRgba == null || detectedFace == null) return;

        Mat croppedFace = new Mat(mRgba, detectedFace);
        Bitmap faceBitmap = Bitmap.createBitmap(croppedFace.cols(), croppedFace.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(croppedFace, faceBitmap);

        boolean isReal = antiSpoofingClassifier.isRealFace(faceBitmap);

        if (isReal) {

            try {
                // Preprocess the image
                Mat mat = new Mat();
                Utils.bitmapToMat(faceBitmap, mat);
                Size inputSize = new Size(112, 112);
                Imgproc.resize(mat, mat, inputSize);
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGR);
                mat.convertTo(mat, CvType.CV_32F, 1.0 / 255.0);

                // SFace specific preprocessing
                Core.subtract(mat, new Scalar(127.5, 127.5, 127.5), mat);
                Core.divide(mat, new Scalar(128, 128, 128), mat);


                // Convert the image to a float array in [1, 3, 112, 112] shape
                float[] inputArray = new float[1 * 3 * 112 * 112];
                int idx = 0;
                for (int c = 0; c < 3; c++) { // Channels (RGB)
                    for (int h = 0; h < 112; h++) { // Height
                        for (int w = 0; w < 112; w++) { // Width
                            double[] pixel = mat.get(h, w);
                            inputArray[idx++] = (float) pixel[c]; // Fill the array in CHW format
                        }
                    }
                }

                // Save or process the embeddings
                float[] newEmbedding = embedManager.generateEmbedding(inputArray);

                //Verify user
                embedManager.verifyFace(newEmbedding, userId, new FaceEmbedManager.VerificationCallback() {
                    @Override
                    public void onVerificationResult(boolean isVerified, float similarityScore) {
                        String resultMessage = isVerified ? "Match with the enrollment face" : "No match";
                        String scoreFormatted = String.format("%.2f", similarityScore * 100);

                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null) {
                            String uid = user.getUid();
                            String email = user.getEmail();
                            long timestamp = System.currentTimeMillis();

                            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
                            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Singapore"));
                            String readableTime = sdf.format(new Date(timestamp));

                            Map<String, Object> log = new HashMap<>();
                            log.put("userId", uid);
                            log.put("email", email);
                            log.put("similarityScore", similarityScore);
                            log.put("formattedScore", scoreFormatted + "%");
                            log.put("verified", resultMessage);
                            log.put("timestamp", timestamp);
                            log.put("readable_time", readableTime);

                            FirebaseFirestore.getInstance()
                                    .collection("auth_logs")
                                    .document(uid)
                                    .collection("attempts")
                                    .add(log)
                                    .addOnSuccessListener(ref -> Log.d("FaceAuth", "✅ Auth log saved"))
                                    .addOnFailureListener(e -> Log.e("FaceAuth", "❌ Failed to save auth log", e));
                        }

                        runOnUiThread(() -> {
                            String toastMsg = isVerified ?
                                    "✅ Authenticated (Score: " + scoreFormatted + "%)" :
                                    "❌ Failed (Score: " + scoreFormatted + "%)";
                            Toast.makeText(LivenessDemoAuthActivity.this, toastMsg, Toast.LENGTH_SHORT).show();

                            if (isVerified) {
                                Intent intent = new Intent(LivenessDemoAuthActivity.this, education_activity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                startActivity(intent);
                                finish();
                            }
                        });
                    }

                    @Override
                    public void onVerificationError(String errorMessage) {
                        runOnUiThread(() -> {
                            Toast.makeText(LivenessDemoAuthActivity.this,
                                    "Error: " + errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });

            } catch (Exception e) {
                Log.e("FaceAuthActivity", "Error running face recognition", e);
                Toast.makeText(this, "Error running face recognition", Toast.LENGTH_SHORT).show();
            }
        } else {
            runOnUiThread(() -> Toast.makeText(this, "Spoof detected! Please use a real face.", Toast.LENGTH_LONG).show());
        }
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
                os.write(buffer, 0 ,bytesRead);
            }

            os.close();
            is.close();

            faceDetector = new CascadeClassifier(cascadeFile.getAbsolutePath());
            if(faceDetector.empty()) {
                Log.e("Cascade", "Failed to load face detector.");
            } else {
                Log.d("Cascade", "Face detector loaded successfully.");
            }

        } catch (IOException e){
            e.printStackTrace();
            Log.e("Cascade", "Error loading cascade:" +  e.getMessage());
        }
    }

    private void checkModelStatus() {
        try {
            // Verify Firebase is initialized
            FirebaseApp.getInstance();

            if (isModelDownloaded()) { // Just check if file exists
                Log.d("ModelCheck", "Model exists locally");
                initializeModel();
            } else {
                Log.d("ModelCheck", "Downloading new model");
                downloadAntiSpoofingModel();
            }
            checkModelVersion();
        } catch (IllegalStateException e) {
            Log.e("Firebase", "Firebase not initialized", e);
            updateModelStatus("Security system error");
            Toast.makeText(this, "Security system initialization failed", Toast.LENGTH_LONG).show();
        }
    }

    private void downloadAntiSpoofingModel() {
        updateModelStatus("Preparing download...");

        try {
            StorageReference modelRef = FirebaseStorage.getInstance().getReference(MODEL_NAME);

            // First verify the file exists
            modelRef.getMetadata().addOnSuccessListener(metadata -> {
                updateModelStatus("Downloading...");
                File localFile = getModelFile();

                // Ensure directory exists
                File parent = localFile.getParentFile();
                if (parent != null) parent.mkdirs();

                modelRef.getFile(localFile)
                        .addOnProgressListener(taskSnapshot -> {
                            double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                            updateModelStatus(String.format("Downloading %.1f%%", progress));
                        })
                        .addOnSuccessListener(taskSnapshot -> {
                            if (localFile.exists() && localFile.length() > 0) {
                                updateModelStatus("Verifying...");
                                initializeModel();
                            } else {
                                updateModelStatus("Download failed - empty file");
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e("Download", "Final download failed", e);
                            updateModelStatus("Download failed");
                        });
            }).addOnFailureListener(e -> {
                Log.e("Download", "Metadata check failed", e);
                updateModelStatus("Model not found");
            });
        } catch (Exception e) {
            Log.e("Download", "Setup failed", e);
            updateModelStatus("System error");
        }
    }

    private void initializeModel() {
        try {
            antiSpoofingClassifier = new AntiSpoofingClassifier(getModelFile());
            isModelLoaded = true;
            updateModelStatus("Security active");
            Log.d("ModelInit", "Model initialized successfully");
        } catch (IOException e) {
            Log.e("ModelInit", "Initialization failed", e);
            updateModelStatus("Security initialization failed");
            isModelLoaded = false;
        }
    }

    private File getModelFile() {
        // Extract just the filename
        String fileName = MODEL_NAME.substring(MODEL_NAME.lastIndexOf('/') + 1);
        return new File(getFilesDir(), fileName);
    }

    private boolean isModelDownloaded() {
        return getModelFile().exists();
    }

    private boolean verifyModelFile(File modelFile) {
        try {
            // Basic sanity checks instead of hash verification
            return modelFile.exists() &&
                    modelFile.length() > 0 && // Not empty
                    modelFile.getName().endsWith(".tflite"); // Correct extension
        } catch (Exception e) {
            Log.e("ModelCheck", "File verification error", e);
            return false;
        }
    }

    private void checkModelVersion() {
        FirebaseStorage.getInstance().getReference(MODEL_VERSION_FILE)
                .getBytes(1024)
                .addOnSuccessListener(bytes -> {
                    String remoteVersion = new String(bytes);
                    String localVersion = getPreferences(MODE_PRIVATE)
                            .getString("model_version", "0");

                    if (!remoteVersion.equals(localVersion)) {
                        Log.d("VersionCheck", "New version available: " + remoteVersion);
                        downloadAntiSpoofingModel();
                    }
                })
                .addOnFailureListener(e -> Log.e("VersionCheck", "Version check failed", e));
    }

    private void saveModelVersion() {
        FirebaseStorage.getInstance().getReference(MODEL_VERSION_FILE)
                .getBytes(1024)
                .addOnSuccessListener(bytes -> {
                    String version = new String(bytes);
                    getPreferences(MODE_PRIVATE).edit()
                            .putString("model_version", version)
                            .apply();
                });
    }

    private void updateModelStatus(String message) {
        runOnUiThread(() -> modelStatusText.setText(message));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(OpenCVLoader.initLocal()){
            if(javaCameraView != null) {
                javaCameraView.enableView();
            }
        } else {
            Log.e("OpenCV", "Initialization failed.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(javaCameraView != null) {
            javaCameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null){
            javaCameraView.disableView();
        }
        if (antiSpoofingClassifier != null) {
            antiSpoofingClassifier.close();
        }
    }
}
