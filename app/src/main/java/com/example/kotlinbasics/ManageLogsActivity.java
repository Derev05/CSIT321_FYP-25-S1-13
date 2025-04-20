package com.example.kotlinbasics;

import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.ArrayList;

public class ManageLogsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListView logListView;
    private TextView headerText;
    private SearchView searchView;
    private ArrayAdapter<String> adapter;

    private ArrayList<String> allDisplayList = new ArrayList<>();
    private ArrayList<String> displayList = new ArrayList<>();
    private ArrayList<DocumentSnapshot> logDocuments = new ArrayList<>();
    private ArrayList<DocumentSnapshot> allLogDocuments = new ArrayList<>();
    private String currentUID;

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

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        logListView.setAdapter(adapter);

        fetchLogs();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterLogsByDate(newText);
                return true;
            }
        });

        logListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= logDocuments.size()) return;

            DocumentSnapshot docToDelete = logDocuments.get(position);

            new AlertDialog.Builder(ManageLogsActivity.this)
                    .setTitle("Delete Log")
                    .setMessage("Are you sure you want to delete this log?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        docToDelete.getReference().delete()
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this, "Log deleted.", Toast.LENGTH_SHORT).show();
                                    fetchLogs(); // Refresh after deletion
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Failed to delete log.", Toast.LENGTH_SHORT).show();
                                    Log.e("ManageLogs", "❌ Deletion failed", e);
                                });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void fetchLogs() {
        headerText.setText("Your Logs");
        allDisplayList.clear();
        allLogDocuments.clear();

        db.collection("enrol_logs")
                .document(currentUID)
                .collection("attempts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        allDisplayList.add("⚠️ No logs found.");
                    } else {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            double prob = doc.contains("probability") ? doc.getDouble("probability") : 0.0;
                            String probFormatted = String.format("%.2f%%", prob * 100);

                            boolean decision = doc.contains("decision") && Boolean.TRUE.equals(doc.getBoolean("decision"));
                            String time = doc.contains("readable_time") ? doc.getString("readable_time") : "Unknown";

                            allDisplayList.add("📅 " + time + "\n🧠 Probability: " + probFormatted + "\n🕵️ Spoof: " + decision);
                            allLogDocuments.add(doc);
                        }
                    }
                    filterLogsByDate(searchView.getQuery().toString()); // Initial display
                })
                .addOnFailureListener(e -> Log.e("ManageLogs", "❌ Failed to fetch logs", e));
    }


    private void filterLogsByDate(String query) {
        displayList.clear();
        logDocuments.clear();

        for (int i = 0; i < allDisplayList.size(); i++) {
            String log = allDisplayList.get(i);
            if (log.toLowerCase().contains(query.toLowerCase())) {
                displayList.add(log);
                logDocuments.add(allLogDocuments.get(i));
            }
        }

        if (displayList.isEmpty()) {
            displayList.add("⚠️ No matching logs.");
        }

        adapter.notifyDataSetChanged();
    }
}
