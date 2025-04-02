package com.example.kotlinbasics;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public class FaceEmbedManager {
    private static final String tag = "FaceEmbedManager";
    private static final float similarity_threshold = 0.7f;

    private final Context context;
    private OrtEnvironment environment;
    private OrtSession session;
    private final String modelPath;
    private static final String EMBED_PATH = "embeddings/";
    private String uid;

    public FaceEmbedManager(Context context, String modelPath){
        this.context = context.getApplicationContext();
        this.modelPath = modelPath;
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        init();
    }

    private void init(){
        try {
            environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

            AssetManager assetManager = context.getAssets();

            // Open the ONNX model file as an InputStream
            InputStream modelStream = assetManager.open(modelPath);

            // Read the InputStream into a byte array
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int bytesRead;
            while ((bytesRead = modelStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            byte[] modelBytes = buffer.toByteArray();

            // Close the InputStream and buffer
            modelStream.close();
            buffer.close();

            session = environment.createSession(modelBytes,sessionOptions);
            Log.i(tag, "Model loaded successfully");
            Log.i(tag, uid);
        } catch (Exception e){
            Log.e(tag, "Failed to initialize embed manager");
            throw new RuntimeException("Failed to initialize model.", e);
        }
    }

    public float[] generateEmbedding(float[] inputArray) throws Exception {
        // Get input name from model
        String inputName = session.getInputNames().iterator().next();

        // Create input tensor (assuming shape [1, 3, 112, 112])
        OnnxTensor inputTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(inputArray),
                new long[]{1, 3, 112, 112});

        // Run inference
        OrtSession.Result output = session.run(Collections.singletonMap(inputName, inputTensor));

        // Get output tensor (assuming output is [1, embedding_size])
        float[][] embeddingArray = (float[][]) output.get(0).getValue();
        float[] embedding = embeddingArray[0];

        float norm = 0.0f;
        for (float value : embedding) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);

        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = embedding[i] / norm;
        }

        float checkNorm = 0f;
        for (float v : embedding) checkNorm += v*v;
        Log.d("Normalization", "Post-normalization norm: " + Math.sqrt(checkNorm));

        return embedding; // Return first (and only) batch
    }

    public byte[] encryptEmbedding(float[] embedding,String uid){
        try {
            // Convert the embedding to JSON
            Gson gson = new Gson();
            String json = gson.toJson(embedding);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            // Initialize cipher with AES-GCM
            SecretKey secretKey = getOrGenerateSecretKey(uid);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            // Encrypt the data
            byte[] iv = cipher.getIV();
            byte[] encryptedData = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));

            // Combine IV and encrypted data (IV needed for decryption)
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(iv);
            outputStream.write(encryptedData);

            return outputStream.toByteArray();
        } catch (Exception e) {
            Log.e(tag, "Encryption failed", e);
            return null;
        }
    }

    private SecretKey getOrGenerateSecretKey(String uid) throws Exception {
        // Use Android KeyStore to securely store keys
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        String alias = "embedding_key_" + uid;

        // Check if key already exists
        if (!keyStore.containsAlias(alias)) {
            // Generate new key if doesn't exist
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build();

            keyGenerator.init(keyGenParameterSpec);
            return keyGenerator.generateKey();
        }

        // Retrieve existing key
        return ((KeyStore.SecretKeyEntry) keyStore.getEntry(alias, null)).getSecretKey();
    }

    public void uploadUserEmbedding(float[] embedding) {
        String userId = uid;
        byte[] encryptedData = encryptEmbedding(embedding, userId);

        if (encryptedData == null) {
            Toast.makeText(context, "Encryption failed", Toast.LENGTH_SHORT).show();
            return;
        }

        StorageReference storageReference = FirebaseStorage.getInstance().getReference();
        StorageReference embeddingRef = storageReference.child(EMBED_PATH + uid +".enc");

        UploadTask uploadTask = embeddingRef.putBytes(encryptedData);

        uploadTask.addOnSuccessListener(taskSnapshot -> {
            Toast.makeText(context, "Embedding uploaded successfully", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            Toast.makeText(context, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    public void downloadAndDecryptUserEmbedding(DecryptionCallback callback) {
        String userId = uid;
        StorageReference storageReference = FirebaseStorage.getInstance().getReference();
        StorageReference embeddingRef = storageReference.child(EMBED_PATH + userId + ".enc");

        File localFile;
        try {
            localFile = File.createTempFile("embedding", ".enc", context.getCacheDir());
        } catch (IOException e) {
            callback.onError("Failed to create temp file");
            return;
        }

        // Download the file
        embeddingRef.getFile(localFile).addOnSuccessListener(taskSnapshot -> {
            try (InputStream inputStream = new FileInputStream(localFile);
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                // Read file in chunks
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                // Get complete byte array
                byte[] encryptedDataWithIv = outputStream.toByteArray();

                // Decrypt the data
                float[] embedding = decryptEmbedding(encryptedDataWithIv, userId);

                if (embedding != null) {
                    callback.onSuccess(embedding);
                } else {
                    callback.onError("Decryption failed");
                }
            } catch (IOException e) {
                callback.onError("File read failed: " + e.getMessage());
            } catch (Exception e) {
                callback.onError("Decryption error: " + e.getMessage());
            } finally {
                localFile.delete();
            }
        }).addOnFailureListener(e -> {
            callback.onError("Download failed: " + e.getMessage());
            localFile.delete();
        });
    }

    public float[] decryptEmbedding(byte[] encryptedEmbedding, String userId){
        try {
            byte[] iv = Arrays.copyOfRange(encryptedEmbedding, 0 ,12);

            byte[] encryptedEmbed = Arrays.copyOfRange(encryptedEmbedding, 12, encryptedEmbedding.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            SecretKey secretKey = getOrGenerateSecretKey(userId);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decryptedEmbedding = cipher.doFinal(encryptedEmbed);
            String json = new String(decryptedEmbedding, StandardCharsets.UTF_8);

            Gson gson = new Gson();
            return gson.fromJson(json, float[].class);
        } catch (Exception e) {
            Log.e(tag,"Decryption failed", e);
            return null;
        }
    }

    public void verifyFace(float[] newEmbedding, String userId, VerificationCallback callback) {
        downloadAndDecryptUserEmbedding(new DecryptionCallback() {
            @Override
            public void onSuccess(float[] storedEmbedding) {
                float similarity = cosineSimilarity(newEmbedding, storedEmbedding);
                Log.d(tag, "Similarity score: " + similarity);
                boolean isVerified = similarity >= similarity_threshold;
                callback.onVerificationResult(isVerified, similarity);
            }

            @Override
            public void onError(String errorMessage) {
                Log.w(tag, "Verification failed: " + errorMessage);
                callback.onVerificationError(errorMessage);
            }
        });
    }

    // Define the verification callback interface


    private float cosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            throw new IllegalArgumentException("Embeddings must have same length");
        }

        float dotProduct = 0;
        float norm1 = 0;
        float norm2 = 0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        return (float) (dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2)));
    }

    public interface DecryptionCallback {
        void onSuccess(float[] embedding);
        void onError(String errorMessage);
    }

    public interface VerificationCallback {
        void onVerificationResult(boolean isVerified, float similarityScore);
        void onVerificationError(String errorMessage);
    }

}
