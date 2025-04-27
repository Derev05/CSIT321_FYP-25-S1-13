package com.example.kotlinbasics;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
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
    private Button changePasswordButton, deleteAccountButton, upgradeButton;
    private ImageButton backButton;
    private de.hdodenhof.circleimageview.CircleImageView profileImageView;
    private LottieAnimationView profileLoadingAnimation;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private Uri selectedImageUri;
    private boolean isUploading = false;
    private Toast currentToast; // ✅ for instant toast handling

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        userEmailText = findViewById(R.id.userEmail);
        userStatusText = findViewById(R.id.userStatus);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        deleteAccountButton = findViewById(R.id.deleteAccountButton);
        upgradeButton = findViewById(R.id.upgradeButton);
        backButton = findViewById(R.id.backButton);
        profileImageView = findViewById(R.id.profileImageView);
        profileLoadingAnimation = findViewById(R.id.profileLoadingAnimation);

        backButton.setOnClickListener(v -> {
            if (!isUploading) {
                startActivity(new Intent(UserProfileActivity.this, MainMenu.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            } else {
                showCustomInstantToast("Uploading... please wait.");
            }
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
            if (isUploading) {
                showCustomInstantToast("Uploading... please wait.");
                return;
            }
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
            if (isUploading) {
                showCustomInstantToast("Uploading... please wait.");
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Delete Account")
                    .setMessage("Are you sure you want to delete your account? This cannot be undone.")
                    .setPositiveButton("Delete", (dialog, which) -> deleteUserAccount())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        upgradeButton.setOnClickListener(v -> {
            if (isUploading) {
                showCustomInstantToast("Uploading... please wait.");
                return;
            }
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
            if (!isUploading) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                pickImageLauncher.launch(intent);
            } else {
                showCustomInstantToast("Uploading... please wait.");
            }
        });
    }

    private void uploadProfilePhoto(Uri imageUri) {
        if (imageUri == null) return;

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        isUploading = true;
        disableAll(true);

        profileLoadingAnimation.setVisibility(View.VISIBLE);
        profileLoadingAnimation.setAnimation("loading.json");
        profileLoadingAnimation.playAnimation();
        profileImageView.setVisibility(View.INVISIBLE);

        StorageReference profileRef = storage.getReference().child("profilePhotos/" + user.getUid() + ".jpg");

        profileRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    new Handler().postDelayed(() -> {
                        profileLoadingAnimation.cancelAnimation();
                        profileLoadingAnimation.setVisibility(View.GONE);
                        profileImageView.setVisibility(View.VISIBLE);
                        showToast("✅ Profile photo updated!");
                        isUploading = false;
                        disableAll(false);
                        loadProfilePhoto(user.getUid());
                    }, 4000); // ✅ 4 seconds delay
                })
                .addOnFailureListener(e -> {
                    profileLoadingAnimation.cancelAnimation();
                    profileLoadingAnimation.setVisibility(View.GONE);
                    profileImageView.setVisibility(View.VISIBLE);
                    showToast("❌ Failed to upload photo.");
                    isUploading = false;
                    disableAll(false);
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
                        } else {
                            upgradeButton.setText("Upgrade to Premium");
                            upgradeButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFD700")));
                        }
                    })
                    .addOnFailureListener(e -> showToast("Failed to load user data"));
        }
    }

    private void loadProfilePhoto(String userId) {
        StorageReference profileRef = storage.getReference().child("profilePhotos/" + userId + ".jpg");
        profileRef.getDownloadUrl()
                .addOnSuccessListener(uri -> Glide.with(this)
                        .load(uri)
                        .placeholder(R.drawable.default_avatar)
                        .into(profileImageView))
                .addOnFailureListener(e -> profileImageView.setImageResource(R.drawable.default_avatar));
    }

    private void disableAll(boolean disable) {
        changePasswordButton.setEnabled(!disable);
        deleteAccountButton.setEnabled(!disable);
        upgradeButton.setEnabled(!disable);
        backButton.setEnabled(!disable);
        profileImageView.setEnabled(!disable);
        userEmailText.setEnabled(!disable);
        userStatusText.setEnabled(!disable);
    }

    @Override
    public void onBackPressed() {
        if (isUploading) {
            showCustomInstantToast("Uploading... please wait.");
        } else {
            super.onBackPressed();
        }
    }

    private void showCustomInstantToast(String message) {
        if (currentToast != null) {
            currentToast.cancel();
        }
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(android.R.id.content), false);
        TextView text = layout.findViewById(R.id.toastText);
        text.setText(message);
        currentToast = new Toast(getApplicationContext());
        currentToast.setDuration(Toast.LENGTH_SHORT);
        currentToast.setView(layout);
        currentToast.setGravity(Gravity.BOTTOM, 0, 150);
        currentToast.show();
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
                        showToast("Subscription cancelled.");
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

        db.collection("reviews").whereEqualTo("email", userEmail).get()
                .addOnSuccessListener(querySnapshots -> {
                    for (var doc : querySnapshots) {
                        db.collection("reviews").document(doc.getId()).delete();
                    }

                    // DELETE Profile Photo
                    storage.getReference().child("profilePhotos/" + userId + ".jpg")
                            .delete()
                            .addOnSuccessListener(unused -> {
                                // DELETE Embedding
                                deleteUserEmbedding(userId);
                            })
                            .addOnFailureListener(e -> {
                                // Profile photo not found, but still delete embedding
                                deleteUserEmbedding(userId);
                            });
                });
    }

    private void deleteUserEmbedding(String userId) {
        StorageReference embeddingRef = storage.getReference().child("embeddings/" + userId + ".enc");
        embeddingRef.delete()
                .addOnSuccessListener(aVoid -> {
                    db.collection("users").document(userId).delete()
                            .addOnSuccessListener(unused1 -> {
                                mAuth.getCurrentUser().delete().addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        showToast("Account, photo, embedding and data deleted successfully.");
                                        signOutAndRedirect();
                                    }
                                });
                            });
                })
                .addOnFailureListener(e -> {
                    // If embedding not found, still continue to delete user Firestore document
                    db.collection("users").document(userId).delete()
                            .addOnSuccessListener(unused1 -> {
                                mAuth.getCurrentUser().delete().addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        showToast("Account deleted. No embedding found.");
                                        signOutAndRedirect();
                                    }
                                });
                            });
                });
    }


    private void signOutAndRedirect() {
        mAuth.signOut();
        Intent intent = new Intent(UserProfileActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
