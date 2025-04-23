package com.example.kotlinbasics;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MenuItem;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainMenu extends AppCompatActivity {

    private TextView userEmailText;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private LinearLayout mitigationIcon;
    private boolean isPremiumUser = false;

    private com.airbnb.lottie.LottieAnimationView mainMenuLoading;

    private View mainRoot;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bioauth);
        mainRoot = findViewById(R.id.mainRoot);
        mainMenuLoading = findViewById(R.id.mainMenuLoading);


        // Firebase init
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        userEmailText = findViewById(R.id.userEmailText);
        FirebaseUser user = mAuth.getCurrentUser();

        mitigationIcon = findViewById(R.id.mitigationIcon);

        if (user != null) {
            userEmailText.setText("Signed in as: " + user.getEmail());
            userEmailText.setOnClickListener(v -> {
                Intent profileIntent = new Intent(MainMenu.this, UserProfileActivity.class);
                startActivity(profileIntent);
            });

            String uid = user.getUid();
            db.collection("users").document(uid).get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            String status = snapshot.getString("status");
                            isPremiumUser = "premium".equalsIgnoreCase(status);
                        } else {
                            isPremiumUser = false;
                        }

                        //  Set mitigationIcon click listener AFTER user status is known
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

        // 3-dot menu
        findViewById(R.id.menuButton).setOnClickListener(this::showPopupMenu);

        LinearLayout biometric = findViewById(R.id.biometricIcon);
        biometric.setOnClickListener(v -> checkUserStatusAndProceed(activity_terms_condition.class));

        LinearLayout review = findViewById(R.id.reviewIcon);
        review.setOnClickListener(v -> showLoadingThenNavigate(reviews_data.class));

    }

    private void checkUserStatusAndProceed(Class<?> destinationActivity) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showCustomToast("User not logged in.");
            return;
        }

        String uid = user.getUid();
        db.collection("users").document(uid).get()
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

    private void goToDestination(Class<?> destinationActivity) {
        if (!isFinishing()) {
            Intent intent = new Intent(MainMenu.this, destinationActivity);
            startActivity(intent);
            finish();
        }
    }

    private void goToUpgradeScreen(Class<?> afterUpgradeTarget) {
        if (!isFinishing()) {
            // Show loading animation first
            mainMenuLoading.setVisibility(View.VISIBLE);
            mainMenuLoading.setAnimation("loading.json");
            mainMenuLoading.playAnimation();

            // Disable interactions while loading
            findViewById(R.id.biometricIcon).setEnabled(false);
            findViewById(R.id.mitigationIcon).setEnabled(false);
            findViewById(R.id.reviewIcon).setEnabled(false);
            findViewById(R.id.menuButton).setEnabled(false);
            findViewById(R.id.userEmailText).setEnabled(false);
            mainRoot.setClickable(true);

            // Delay then swap layout
            new android.os.Handler().postDelayed(() -> {
                mainMenuLoading.cancelAnimation();
                mainMenuLoading.setVisibility(View.GONE);

                // Inflate upgrade layout
                setContentView(R.layout.upgrade_premium);

                // Now re-bind button IDs from new layout
                findViewById(R.id.upgradeNowBtn).setOnClickListener(v -> {
                    startActivity(new Intent(MainMenu.this, CheckoutActivity.class));
                });

                findViewById(R.id.laterText).setOnClickListener(v -> {
                    // Show toast or ad fallback
                    showCustomToast("Continuing as free user...");
                    new android.os.Handler().postDelayed(() -> {
                        Intent intent = new Intent(MainMenu.this, afterUpgradeTarget);
                        startActivity(intent);
                        finish(); // optional
                    }, 1000);
                });

            }, 1200); // 1.2 sec delay to finish animation
        }
    }


    private void showPopupMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.main_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_profile) {
                startActivity(new Intent(MainMenu.this, UserProfileActivity.class));
                return true;
            } else if (id == R.id.menu_logout) {
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

    private void showLoadingThenNavigate(Class<?> destinationActivity) {
        if (mainMenuLoading.getVisibility() == View.VISIBLE) return; // Prevent spam

        // ✅ Show animation
        mainMenuLoading.setVisibility(View.VISIBLE);
        mainMenuLoading.setAnimation("loading.json");
        mainMenuLoading.playAnimation();

        // ✅ Disable interaction for all clickables
        findViewById(R.id.biometricIcon).setEnabled(false);
        findViewById(R.id.mitigationIcon).setEnabled(false);
        findViewById(R.id.reviewIcon).setEnabled(false);
        findViewById(R.id.menuButton).setEnabled(false);
        findViewById(R.id.userEmailText).setEnabled(false); //  Disable email click
        mainRoot.setClickable(true); //  Absorb touches

        new android.os.Handler().postDelayed(() -> {
            //  Hide animation
            mainMenuLoading.cancelAnimation();
            mainMenuLoading.setVisibility(View.GONE);

            //  Re-enable interaction
            findViewById(R.id.biometricIcon).setEnabled(true);
            findViewById(R.id.mitigationIcon).setEnabled(true);
            findViewById(R.id.reviewIcon).setEnabled(true);
            findViewById(R.id.menuButton).setEnabled(true);
            findViewById(R.id.userEmailText).setEnabled(true);
            mainRoot.setClickable(false);

            // Navigate
            Intent intent = new Intent(MainMenu.this, destinationActivity);
            startActivity(intent);

        }, 1000);
    }





    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
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
