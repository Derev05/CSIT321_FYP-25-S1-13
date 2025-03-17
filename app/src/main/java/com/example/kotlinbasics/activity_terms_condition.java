package com.example.kotlinbasics;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class activity_terms_condition extends AppCompatActivity {

    private void showCustomToast(Context context, String message) {
        // Inflate the custom layout
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(R.id.toastText));

        // Set the text inside the toast
        TextView text = layout.findViewById(R.id.toastText);
        text.setText(message);

        // Create and show the toast
        Toast toast = new Toast(context);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.setGravity(Gravity.CENTER, 0, 500);
        toast.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms_condition);  // Ensure this matches your layout file name

        TextView termsTextView = findViewById(R.id.termsText);
        termsTextView.setText(Html.fromHtml(getString(R.string.terms_conditions_text), Html.FROM_HTML_MODE_LEGACY));

        Button decline = findViewById(R.id.declineButton);
        decline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(activity_terms_condition.this, MainMenu.class);
                startActivity(intent);

            }
        });
        CheckBox agree = findViewById(R.id.agreeCheckbox);
        Button acknowledge = findViewById(R.id.acknowledgeButton);
        acknowledge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!agree.isChecked()) {
                    showCustomToast(activity_terms_condition.this, "⚠ Please agree to the Terms & Conditions to proceed");
                } else {
                    Intent intent = new Intent(activity_terms_condition.this, enroll_auth.class);
                    startActivity(intent);
                }
            }
        });


    }
}


