package com.example.bioauth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import androidx.appcompat.app.AppCompatActivity;

public class enroll_auth extends AppCompatActivity {

    private LinearLayout enrollBtn, authBtn;
    private AdView adview;
    private AdRequest adrequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.enroll_authenticate);

        LinearLayout enrollBtn = findViewById(R.id.biometricIcon);
        LinearLayout authBtn = findViewById(R.id.authenticationIcon);


        enrollBtn.setOnClickListener(v -> {
            Intent enrollIntent = new Intent(enroll_auth.this, FaceEnrollActivity.class);
            startActivity(enrollIntent);
        });

        // You can add authBtn logic here if needed
        authBtn.setOnClickListener(v ->{
            FirebaseAuth auth = FirebaseAuth.getInstance();
            String userId = auth.getCurrentUser().getUid();
            StorageReference storageReference = FirebaseStorage.getInstance().getReference();
            StorageReference embeddingRef = storageReference.child("embeddings/" + userId +".enc");

            embeddingRef.getMetadata().addOnSuccessListener(storageMetadata -> {
                Intent authIntent = new Intent(enroll_auth.this, FaceAuthActivity.class);
                startActivity(authIntent);
            }).addOnFailureListener(e ->{
                Toast.makeText(enroll_auth.this, "Please enroll first before authentication.", Toast.LENGTH_LONG).show();
            });
        });
    }

    @Override
    public void onBackPressed() {
        // Go back to MainMenu instead of default behavior
        Intent intent = new Intent(enroll_auth.this, MainMenu.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish(); // close current activity
    }
}
