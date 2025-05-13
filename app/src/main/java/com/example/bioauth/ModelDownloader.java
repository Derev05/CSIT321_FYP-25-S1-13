package com.example.bioauth;

import android.content.Context;
import android.util.Log;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.io.File;

public class ModelDownloader {
    private static final String TAG = "ModelDownloader";

    public interface DownloadCallback {
        void onDownloadComplete(File modelFile);
        void onDownloadFailed(Exception e);
    }

    public static void downloadModel(Context context, String modelName, DownloadCallback callback) {
        // Extract just the filename (remove 'models/' prefix)
        String fileName = modelName.substring(modelName.lastIndexOf('/') + 1);
        File localFile = new File(context.getFilesDir(), fileName);

        // Ensure parent directory exists
        File parentDir = localFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        StorageReference modelRef = FirebaseStorage.getInstance().getReference(modelName);

        modelRef.getFile(localFile)
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d("Download", "Download completed to: " + localFile.getAbsolutePath());
                    callback.onDownloadComplete(localFile);
                })
                .addOnFailureListener(e -> {
                    Log.e("Download", "Failed to download to " + localFile.getAbsolutePath(), e);
                    callback.onDownloadFailed(e);
                });
    }

    // Optional: Delete local model file
    public static boolean deleteLocalModel(Context context, String modelName) {
        File localFile = new File(context.getFilesDir(), modelName);
        return localFile.exists() && localFile.delete();
    }
}