package com.example.bioauth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.*;

import android.view.View;


public class ManageLogsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListView logListView;
    private TextView headerText;
    private SearchView searchView;
    private Spinner logTypeSpinner;
    private ArrayAdapter<String> adapter;

    private ArrayList<String> displayList = new ArrayList<>();
    private ArrayList<DocumentSnapshot> logDocuments = new ArrayList<>();
    private String currentUID;
    private String selectedLogType = "Enrol Logs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_logs);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUID = mAuth.getCurrentUser().getUid();

        logListView = findViewById(R.id.logListView);
        headerText = findViewById(R.id.headerText);
        searchView = findViewById(R.id.searchView);
        logTypeSpinner = findViewById(R.id.logTypeSpinner);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        logListView.setAdapter(adapter);

        ArrayAdapter<CharSequence> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Enrol Logs", "Auth Logs"});
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        logTypeSpinner.setAdapter(spinnerAdapter);

        logTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedLogType = parent.getItemAtPosition(position).toString();
                fetchLogs();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String newText) {
                filterLogsByDate(newText);
                return true;
            }
        });

        logListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= logDocuments.size() || displayList.get(position).startsWith("⚠️")) {
                Toast.makeText(this, "⚠️ No valid log selected", Toast.LENGTH_SHORT).show();
                return;
            }

            DocumentSnapshot doc = logDocuments.get(position);
            String logText = displayList.get(position);

            new AlertDialog.Builder(this)
                    .setTitle("📋 Log Details")
                    .setMessage(logText)
                    .setPositiveButton("Run Liveness Demo", (dialog, which) -> {
                        Intent intent = new Intent(ManageLogsActivity.this, education_activity.class);
                        startActivity(intent);
                    })
                    .setNegativeButton("Close", null)
                    .show();
        });
    }

    private void fetchLogs() {
        displayList.clear();
        logDocuments.clear();

        headerText.setText("Your " + selectedLogType);

        if ("Enrol Logs".equals(selectedLogType)) {
            db.collection("enrol_logs").document(currentUID).collection("attempts")
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        List<DocumentSnapshot> docs = snapshot.getDocuments();
                        docs.sort((d1, d2) -> Long.compare(
                                d2.getLong("timestamp") != null ? d2.getLong("timestamp") : 0,
                                d1.getLong("timestamp") != null ? d1.getLong("timestamp") : 0));

                        for (DocumentSnapshot doc : docs) {
                            double prob = doc.getDouble("probability") != null ? doc.getDouble("probability") : 0.0;
                            String probFormatted = String.format("%.2f%%", prob * 100);
                            boolean decision = doc.contains("decision") && Boolean.TRUE.equals(doc.getBoolean("decision"));
                            boolean falseNeg = doc.contains("false_negative") && Boolean.TRUE.equals(doc.getBoolean("false_negative"));
                            String time = doc.getString("readable_time") != null ? doc.getString("readable_time") : "Unknown";

                            StringBuilder logEntry = new StringBuilder();
                            logEntry.append("📅 ").append(time)
                                    .append("\n🧠 Probability: ").append(probFormatted)
                                    .append("\n🕵️ Spoof: ").append(decision);
                            if (falseNeg) logEntry.append("\n⚠️ False Negative");

                            displayList.add(logEntry.toString());
                            logDocuments.add(doc);
                        }

                        filterLogsByDate(searchView.getQuery().toString());
                    }).addOnFailureListener(e -> Log.e("ManageLogs", "❌ Enrol log fetch error", e));
        } else {
            db.collection("auth_logs").document(currentUID).collection("attempts")
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        List<DocumentSnapshot> docs = snapshot.getDocuments();
                        docs.sort((d1, d2) -> Long.compare(
                                d2.getLong("timestamp") != null ? d2.getLong("timestamp") : 0,
                                d1.getLong("timestamp") != null ? d1.getLong("timestamp") : 0));

                        for (DocumentSnapshot doc : docs) {
                            String score = doc.getString("formattedScore") != null ? doc.getString("formattedScore") : "N/A";
                            String verified = doc.getString("verified") != null ? doc.getString("verified") : "N/A";
                            String time = doc.getString("readable_time") != null ? doc.getString("readable_time") : "Unknown";

                            String logEntry = "📅 " + time + "\n🔐 Score: " + score + "\n✅ Verified: " + verified;

                            displayList.add(logEntry);
                            logDocuments.add(doc);
                        }

                        filterLogsByDate(searchView.getQuery().toString());
                    }).addOnFailureListener(e -> Log.e("ManageLogs", "❌ Auth log fetch error", e));
        }
    }

    private void filterLogsByDate(String query) {
        ArrayList<String> filtered = new ArrayList<>();

        for (String log : displayList) {
            if (log.toLowerCase().contains(query.toLowerCase())) {
                filtered.add(log);
            }
        }

        displayList.clear();
        if (filtered.isEmpty()) {
            displayList.add("⚠️ No matching logs.");
        } else {
            displayList.addAll(filtered);
        }

        adapter.notifyDataSetChanged();
    }
}
