package com.example.bioauth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainMenu extends AppCompatActivity {

    private TextView userEmailText;
    private CircleImageView topProfileImage;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private LinearLayout mitigationIcon, biometric, review, profile;
    private com.airbnb.lottie.LottieAnimationView mainMenuLoading;
    private View mainRoot;
    private boolean isPremiumUser = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bioauth);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Find views
        mainRoot = findViewById(R.id.mainRoot);
        mainMenuLoading = findViewById(R.id.mainMenuLoading);
        userEmailText = findViewById(R.id.userEmailText);
        topProfileImage = findViewById(R.id.topProfileImage);
        mitigationIcon = findViewById(R.id.mitigationIcon);
        biometric = findViewById(R.id.biometricIcon);
        review = findViewById(R.id.reviewIcon);
        profile = findViewById(R.id.profileIcon);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            userEmailText.setText(user.getEmail());

            refreshProfilePhoto(); // ✅ Initial load

            // Click Email or Profile Photo to go UserProfileActivity
            View.OnClickListener goToProfile = v -> {
                Intent profileIntent = new Intent(MainMenu.this, UserProfileActivity.class);
                startActivity(profileIntent);
            };
            userEmailText.setOnClickListener(goToProfile);
            topProfileImage.setOnClickListener(goToProfile);

            // Check Premium Status
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            String status = snapshot.getString("status");
                            isPremiumUser = "premium".equalsIgnoreCase(status);
                        } else {
                            isPremiumUser = false;
                        }
                        mitigationIcon.setOnClickListener(v -> {
                            if (isPremiumUser) {
                                showLoadingThenNavigate(mitigation_activity.class);
                            } else {
                                showCustomToast("Must be a premium user to access this feature.");
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        isPremiumUser = false;
                        mitigationIcon.setOnClickListener(v -> {
                            showCustomToast("Failed to check user status.");
                        });
                    });
        } else {
            userEmailText.setText("Not Signed In");
            mitigationIcon.setOnClickListener(v -> {
                showCustomToast("You must be logged in to use this feature.");
            });
        }

        // Other buttons
        biometric.setOnClickListener(v -> checkUserStatusAndProceed(activity_terms_condition.class));
        review.setOnClickListener(v -> showLoadingThenNavigate(reviews_data.class));
        profile.setOnClickListener(v -> showLoadingThenNavigate(UserProfileActivity.class));

        // Menu (3 dots)
        findViewById(R.id.menuButton).setOnClickListener(this::showPopupMenu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProfilePhoto(); // ✅ Refresh latest photo when coming back
    }

    private void refreshProfilePhoto() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            mainMenuLoading.setVisibility(View.VISIBLE);
            mainMenuLoading.setAnimation("loading.json");
            mainMenuLoading.playAnimation();
            topProfileImage.setVisibility(View.INVISIBLE); // hide profile during loading

            FirebaseStorage.getInstance().getReference()
                    .child("profilePhotos/" + user.getUid() + ".jpg")
                    .getDownloadUrl()
                    .addOnSuccessListener(uri -> {
                        Glide.with(MainMenu.this)
                                .load(uri)
                                .placeholder(R.drawable.default_avatar)
                                .into(topProfileImage);

                        mainMenuLoading.cancelAnimation();
                        mainMenuLoading.setVisibility(View.GONE);
                        topProfileImage.setVisibility(View.VISIBLE);
                    })
                    .addOnFailureListener(e -> {
                        topProfileImage.setImageResource(R.drawable.default_avatar);

                        mainMenuLoading.cancelAnimation();
                        mainMenuLoading.setVisibility(View.GONE);
                        topProfileImage.setVisibility(View.VISIBLE);
                    });
        }
    }

    private void checkUserStatusAndProceed(Class<?> destinationActivity) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showCustomToast("User not logged in.");
            return;
        }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        String status = snapshot.getString("status");
                        if ("premium".equalsIgnoreCase(status)) {
                            showLoadingThenNavigate(destinationActivity);
                        } else {
                            goToUpgradeScreen(destinationActivity);
                        }
                    } else {
                        showCustomToast("User data not found.");
                    }
                })
                .addOnFailureListener(e -> showCustomToast("Failed to check status."));
    }

    private void showPopupMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.main_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_profile) {
                startActivity(new Intent(MainMenu.this, UserProfileActivity.class));
                return true;
            } else if (item.getItemId() == R.id.menu_logout) {
                mAuth.signOut();
                showCustomToast("Logged out successfully!");
                Intent intent = new Intent(MainMenu.this, CoverActivity.class);
                startActivity(intent);
                finish();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showCustomToast(String message) {
        View layout = LayoutInflater.from(this).inflate(R.layout.custom_toast_layout, findViewById(android.R.id.content), false);
        TextView text = layout.findViewById(R.id.toastText);
        text.setText(message);
        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.setGravity(Gravity.BOTTOM, 0, 150);
        toast.show();
    }

    private void showLoadingThenNavigate(Class<?> destinationActivity) {
        if (mainMenuLoading.getVisibility() == View.VISIBLE) return;

        mainMenuLoading.setVisibility(View.VISIBLE);
        mainMenuLoading.setAnimation("loading.json");
        mainMenuLoading.playAnimation();

        disableMainScreen(true);

        new android.os.Handler().postDelayed(() -> {
            mainMenuLoading.cancelAnimation();
            mainMenuLoading.setVisibility(View.GONE);

            disableMainScreen(false);

            Intent intent = new Intent(MainMenu.this, destinationActivity);
            startActivity(intent);
        }, 1000);
    }

    private void disableMainScreen(boolean disable) {
        biometric.setEnabled(!disable);
        mitigationIcon.setEnabled(!disable);
        review.setEnabled(!disable);
        profile.setEnabled(!disable);
        findViewById(R.id.menuButton).setEnabled(!disable);
        findViewById(R.id.userEmailText).setEnabled(!disable);
        mainRoot.setClickable(disable);
    }

    private void goToUpgradeScreen(Class<?> afterUpgradeTarget) {
        if (!isFinishing()) {
            mainMenuLoading.setVisibility(View.VISIBLE);
            mainMenuLoading.setAnimation("loading.json");
            mainMenuLoading.playAnimation();

            disableMainScreen(true);

            new android.os.Handler().postDelayed(() -> {
                mainMenuLoading.cancelAnimation();
                mainMenuLoading.setVisibility(View.GONE);

                setContentView(R.layout.upgrade_premium);

                findViewById(R.id.upgradeNowBtn).setOnClickListener(v -> {
                    startActivity(new Intent(MainMenu.this, CheckoutActivity.class));
                });

                findViewById(R.id.laterText).setOnClickListener(v -> {
                    showCustomToast("Continuing as free user...");
                    new android.os.Handler().postDelayed(() -> {
                        Intent intent = new Intent(MainMenu.this, afterUpgradeTarget);
                        startActivity(intent);
                        finish();
                    }, 1000);
                });
            }, 1200);
        }
    }

    @Override
    public void onBackPressed() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Logout Confirmation")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    mAuth.signOut();
                    showCustomToast("Logged out successfully!");
                    Intent intent = new Intent(MainMenu.this, CoverActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }
}
