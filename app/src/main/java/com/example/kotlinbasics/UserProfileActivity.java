package com.example.kotlinbasics;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class UserProfileActivity extends AppCompatActivity {

    private TextView userEmailText, userStatusText;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private Button changePasswordButton, deleteAccountButton, upgradeButton;
    private ImageButton backButton;
    private de.hdodenhof.circleimageview.CircleImageView profileImageView;

    private ActivityResultLauncher<Intent> pickImageLauncher;
    private Uri selectedImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        userEmailText = findViewById(R.id.userEmail);
        userStatusText = findViewById(R.id.userStatus);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        deleteAccountButton = findViewById(R.id.deleteAccountButton);
        upgradeButton = findViewById(R.id.upgradeButton);
        backButton = findViewById(R.id.backButton);
        profileImageView = findViewById(R.id.profileImageView);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(UserProfileActivity.this, MainMenu.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        fetchUserStatus();
        setupButtonListeners();
        setupProfileImageClick();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchUserStatus();
    }

    private void setupButtonListeners() {
        changePasswordButton.setOnClickListener(v -> {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                mAuth.sendPasswordResetEmail(user.getEmail())
                        .addOnSuccessListener(aVoid -> {
                            showToast("Reset email sent. Please check your inbox.");
                            signOutAndRedirect();
                        })
                        .addOnFailureListener(e -> showToast("Failed to send reset email."));
            }
        });

        deleteAccountButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Account")
                    .setMessage("Are you sure you want to delete your account? This cannot be undone.")
                    .setPositiveButton("Delete", (dialog, which) -> deleteUserAccount())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        upgradeButton.setOnClickListener(v -> {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                db.collection("users").document(user.getUid()).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            String status = documentSnapshot.getString("status");
                            if ("premium".equalsIgnoreCase(status)) {
                                showCancelSubscriptionDialog();
                            } else {
                                startActivity(new Intent(UserProfileActivity.this, CheckoutActivity.class));
                            }
                        });
            }
        });
    }

    private void fetchUserStatus() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            userEmailText.setText(user.getEmail());
            loadProfilePhoto(user.getUid());

            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String status = documentSnapshot.getString("status");
                        userStatusText.setText("Status: " + (status != null ? status : "free"));

                        if ("premium".equalsIgnoreCase(status)) {
                            upgradeButton.setText("Cancel Subscription");
                            upgradeButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF4444")));
                            upgradeButton.setCompoundDrawablesWithIntrinsicBounds(
                                    ContextCompat.getDrawable(this, R.drawable.ic_cancel),
                                    null, null, null);
                        } else {
                            upgradeButton.setText("Upgrade to Premium");
                            upgradeButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFD700")));
                            upgradeButton.setCompoundDrawablesWithIntrinsicBounds(
                                    ContextCompat.getDrawable(this, R.drawable.ic_premium),
                                    null, null, null);
                        }
                    })
                    .addOnFailureListener(e -> showToast("Failed to load user data"));
        } else {
            userEmailText.setText("Not logged in");
            userStatusText.setText("Status: Unknown");
            upgradeButton.setVisibility(View.GONE);
        }
    }

    private void setupProfileImageClick() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        uploadProfilePhoto(selectedImageUri);
                    }
                }
        );

        profileImageView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });
    }

    private void uploadProfilePhoto(Uri imageUri) {
        if (imageUri == null) return;

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        StorageReference profileRef = storage.getReference().child("profilePhotos/" + user.getUid() + ".jpg");

        profileRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    showToast("✅ Profile photo updated!");
                    loadProfilePhoto(user.getUid()); // Refresh
                })
                .addOnFailureListener(e -> showToast("❌ Failed to upload photo."));
    }

    private void loadProfilePhoto(String userId) {
        StorageReference profileRef = storage.getReference().child("profilePhotos/" + userId + ".jpg");

        profileRef.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    Glide.with(this)
                            .load(uri)
                            .placeholder(R.drawable.default_avatar)
                            .into(profileImageView);
                })
                .addOnFailureListener(e -> {
                    profileImageView.setImageResource(R.drawable.default_avatar);
                });
    }

    private void showCancelSubscriptionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Subscription")
                .setMessage("Are you sure you want to cancel your premium subscription?")
                .setPositiveButton("Yes, Cancel", (dialog, which) -> cancelSubscription())
                .setNegativeButton("No", null)
                .show();
    }

    private void cancelSubscription() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid())
                    .update("status", "free")
                    .addOnSuccessListener(unused -> {
                        showToast("Subscription cancelled. You're now on free tier.");
                        fetchUserStatus();
                    })
                    .addOnFailureListener(e -> showToast("Failed to cancel subscription."));
        }
    }

    private void deleteUserAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String userId = user.getUid();
        String userEmail = user.getEmail();

        // Step 1: Delete all reviews by this user
        db.collection("reviews")
                .whereEqualTo("email", userEmail)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    for (var doc : querySnapshots) {
                        db.collection("reviews").document(doc.getId()).delete();
                    }

                    // Step 2: Delete user's profile photo from Firebase Storage
                    StorageReference profileRef = storage.getReference()
                            .child("profilePhotos/" + userId + ".jpg");
                    profileRef.delete()
                            .addOnSuccessListener(unused -> {
                                // Step 3: Delete user document from Firestore
                                db.collection("users").document(userId).delete()
                                        .addOnSuccessListener(unused1 -> {
                                            // Step 4: Delete user authentication account
                                            user.delete().addOnCompleteListener(task -> {
                                                if (task.isSuccessful()) {
                                                    showToast("Account, photo, and data deleted successfully.");
                                                    signOutAndRedirect();
                                                } else {
                                                    showToast("Failed to delete account.");
                                                }
                                            });
                                        })
                                        .addOnFailureListener(e -> showToast("Failed to delete user document."));
                            })
                            .addOnFailureListener(e -> {
                                // Even if profile photo not found (no upload), continue
                                db.collection("users").document(userId).delete()
                                        .addOnSuccessListener(unused1 -> {
                                            user.delete().addOnCompleteListener(task -> {
                                                if (task.isSuccessful()) {
                                                    showToast("Account deleted, no profile photo found.");
                                                    signOutAndRedirect();
                                                } else {
                                                    showToast("Failed to delete account.");
                                                }
                                            });
                                        })
                                        .addOnFailureListener(e2 -> showToast("Failed to delete user document."));
                            });
                })
                .addOnFailureListener(e -> showToast("Failed to delete reviews."));
    }


    private void signOutAndRedirect() {
        mAuth.signOut();
        Intent intent = new Intent(UserProfileActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showToast(String message) {
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
