package com.example.kotlinbasics;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DeleteLogsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListView logListView;
    private TextView headerText;
    private SearchView searchView;
    private Spinner logTypeSpinner;
    private Button clearLogsButton, deleteSelectedButton, selectAllButton, cancelSelectButton;

    private ArrayAdapter<String> adapter;
    private ArrayList<String> displayList = new ArrayList<>();
    private ArrayList<DocumentSnapshot> logDocuments = new ArrayList<>();
    private HashSet<Integer> selectedPositions = new HashSet<>();
    private boolean isMultiSelectMode = false;
    private String currentUID;
    private String selectedLogType = "Enrol Logs";

    private Toast activeToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_logs);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUID = mAuth.getCurrentUser().getUid();

        logListView = findViewById(R.id.logListView);
        headerText = findViewById(R.id.headerText);
        searchView = findViewById(R.id.searchView);
        clearLogsButton = findViewById(R.id.clearLogsButton);
        deleteSelectedButton = findViewById(R.id.deleteSelectedButton);
        selectAllButton = findViewById(R.id.selectAllButton);
        cancelSelectButton = findViewById(R.id.cancelSelectButton);
        logTypeSpinner = findViewById(R.id.logTypeSpinner);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        logListView.setAdapter(adapter);
        logListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        deleteSelectedButton.setVisibility(View.GONE);
        selectAllButton.setVisibility(View.GONE);
        cancelSelectButton.setVisibility(View.GONE);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Enrol Logs", "Auth Logs"});
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        logTypeSpinner.setAdapter(spinnerAdapter);

        logTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                exitMultiSelectMode(); // cancel multi-select first to avoid crash
                selectedLogType = parent.getItemAtPosition(position).toString();
                fetchLogs();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });


        clearLogsButton.setOnClickListener(v -> confirmClearAllLogs());

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String newText) {
                filterLogsByDate(newText);
                return true;
            }
        });

        logListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= logDocuments.size() || displayList.get(position).startsWith("⚠️")) {
                Toast.makeText(DeleteLogsActivity.this, "⚠️ No valid log selected", Toast.LENGTH_SHORT).show();
                return;
            }

            final DocumentSnapshot doc = logDocuments.get(position);
            final String logText = displayList.get(position);
            final String typeCopy = selectedLogType;

            if (isMultiSelectMode) {
                if (selectedPositions.contains(position)) {
                    selectedPositions.remove(position);
                    view.setBackgroundColor(0x00000000);
                } else {
                    selectedPositions.add(position);
                    view.setBackgroundColor(0x5533B5E5);
                }
                deleteSelectedButton.setText("Delete Selected (" + selectedPositions.size() + ")");
            } else {
                new AlertDialog.Builder(DeleteLogsActivity.this)
                        .setTitle("📝 Log Details")
                        .setMessage(logText)
                        .setPositiveButton("Delete This Log", (dialog, which) -> {
                            doc.getReference().delete().addOnSuccessListener(aVoid -> {
                                if ("Enrol Logs".equals(typeCopy)) {
                                    long ts = doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0;
                                    deleteMatchingAuthLogs(ts);
                                } else {
                                    Toast.makeText(DeleteLogsActivity.this, "✅ Log deleted", Toast.LENGTH_SHORT).show();
                                    fetchLogs();
                                }
                            }).addOnFailureListener(e -> {
                                Toast.makeText(DeleteLogsActivity.this, "❌ Failed to delete", Toast.LENGTH_SHORT).show();
                            });
                        })
                        .setNegativeButton("Close", null)
                        .show();
            }
        });

        logListView.setOnItemLongClickListener((parent, view, position, id) -> {
            isMultiSelectMode = true;
            selectedPositions.clear();
            selectedPositions.add(position);
            view.setBackgroundColor(0x5533B5E5);
            deleteSelectedButton.setVisibility(View.VISIBLE);
            selectAllButton.setVisibility(View.VISIBLE);
            cancelSelectButton.setVisibility(View.VISIBLE);
            deleteSelectedButton.setText("Delete Selected (1)");
            return true;
        });

        deleteSelectedButton.setOnClickListener(v -> {
            if (selectedPositions.isEmpty()) return;

            new AlertDialog.Builder(this)
                    .setTitle("Confirm Deletion")
                    .setMessage("Delete " + selectedPositions.size() + " selected logs?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        WriteBatch batch = db.batch();
                        ArrayList<Integer> sorted = new ArrayList<>(selectedPositions);
                        Collections.sort(sorted, Collections.reverseOrder());

                        for (int pos : sorted) {
                            DocumentSnapshot doc = logDocuments.get(pos);
                            batch.delete(doc.getReference());
                        }

                        batch.commit().addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "✅ Deleted selected logs", Toast.LENGTH_SHORT).show();
                            exitMultiSelectMode();
                            fetchLogs();
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });


        selectAllButton.setOnClickListener(v -> {
            selectedPositions.clear();
            for (int i = 0; i < logDocuments.size(); i++) {
                selectedPositions.add(i);
                logListView.setItemChecked(i, true);
            }
            deleteSelectedButton.setText("Delete Selected (" + selectedPositions.size() + ")");
            highlightSelected();
        });

        cancelSelectButton.setOnClickListener(v -> {
            exitMultiSelectMode();
            fetchLogs();
        });
    }

    private void showCustomToast(String message) {
        if (activeToast != null) {
            activeToast.cancel();
        }
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
        android.view.View layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(android.R.id.content), false);

        android.widget.TextView text = layout.findViewById(R.id.toastText);
        text.setText(message);

        activeToast = new Toast(this);
        activeToast.setDuration(Toast.LENGTH_SHORT);
        activeToast.setView(layout);
        activeToast.setGravity(android.view.Gravity.BOTTOM, 0, 150);
        activeToast.show();
    }
    private void exitMultiSelectMode() {
        isMultiSelectMode = false;
        selectedPositions.clear();
        deleteSelectedButton.setVisibility(View.GONE);
        selectAllButton.setVisibility(View.GONE);
        cancelSelectButton.setVisibility(View.GONE);
    }

    private void fetchLogs() {
        displayList.clear();
        logDocuments.clear();
        headerText.setText("🗑 " + selectedLogType);

        if ("Enrol Logs".equals(selectedLogType)) {
            db.collection("enrol_logs").document(currentUID).collection("attempts")
                    .get().addOnSuccessListener(snapshot -> {
                        List<DocumentSnapshot> docs = snapshot.getDocuments();
                        docs.sort((a, b) -> Long.compare(
                                b.getLong("timestamp") != null ? b.getLong("timestamp") : 0,
                                a.getLong("timestamp") != null ? a.getLong("timestamp") : 0));
                        for (DocumentSnapshot doc : docs) {
                            double prob = doc.getDouble("probability") != null ? doc.getDouble("probability") : 0.0;
                            String time = doc.getString("readable_time") != null ? doc.getString("readable_time") : "Unknown";
                            boolean spoof = doc.contains("decision") && Boolean.TRUE.equals(doc.getBoolean("decision"));
                            boolean fn = doc.contains("false_negative") && Boolean.TRUE.equals(doc.getBoolean("false_negative"));

                            StringBuilder entry = new StringBuilder("📅 " + time + "\n🧠 Probability: " + String.format("%.2f%%", prob * 100));
                            entry.append("\n🕵️ Spoof: ").append(spoof);
                            if (fn) entry.append("\n⚠️ False Negative");

                            displayList.add(entry.toString());
                            logDocuments.add(doc);
                        }
                        filterLogsByDate(searchView.getQuery().toString());
                    });
        } else {
            db.collection("auth_logs").document(currentUID).collection("attempts")
                    .get().addOnSuccessListener(snapshot -> {
                        List<DocumentSnapshot> docs = snapshot.getDocuments();
                        docs.sort((a, b) -> Long.compare(
                                b.getLong("timestamp") != null ? b.getLong("timestamp") : 0,
                                a.getLong("timestamp") != null ? a.getLong("timestamp") : 0));
                        for (DocumentSnapshot doc : docs) {
                            String time = doc.getString("readable_time") != null ? doc.getString("readable_time") : "Unknown";
                            String score = doc.getString("formattedScore") != null ? doc.getString("formattedScore") : "N/A";
                            String verified = doc.getString("verified") != null ? doc.getString("verified") : "Unknown";

                            displayList.add("📅 " + time + "\n🔐 Score: " + score + "\n✅ Verified: " + verified);
                            logDocuments.add(doc);
                        }
                        filterLogsByDate(searchView.getQuery().toString());
                    });
        }
    }

    private void highlightSelected() {
        logListView.post(() -> {
            for (int i = 0; i < logListView.getChildCount(); i++) {
                View itemView = logListView.getChildAt(i);
                if (selectedPositions.contains(i)) {
                    itemView.setBackgroundColor(0x5533B5E5);
                } else {
                    itemView.setBackgroundColor(0x00000000);
                }
            }
        });
    }

    private void filterLogsByDate(String query) {
        ArrayList<String> filtered = new ArrayList<>();
        for (String log : displayList) {
            if (log.toLowerCase().contains(query.toLowerCase())) {
                filtered.add(log);
            }
        }
        displayList.clear();
        displayList.addAll(filtered.isEmpty() ? Collections.singletonList("⚠️ No matching logs.") : filtered);
        adapter.notifyDataSetChanged();
        highlightSelected(); // redraw selected items
    }

    private void confirmClearAllLogs() {
        new AlertDialog.Builder(this)
                .setTitle("🗑 Clear All Logs?")
                .setMessage("This will permanently delete all " + selectedLogType + ". Proceed?")
                .setPositiveButton("Delete All", (dialog, which) -> {
                    CollectionReference ref = "Enrol Logs".equals(selectedLogType)
                            ? db.collection("enrol_logs").document(currentUID).collection("attempts")
                            : db.collection("auth_logs").document(currentUID).collection("attempts");

                    ref.get().addOnSuccessListener(snapshot -> {
                        WriteBatch batch = db.batch();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                        batch.commit().addOnSuccessListener(aVoid -> {
                            showCustomToast("All logs deleted");
                            fetchLogs();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteMatchingAuthLogs(long enrolTimestamp) {
        db.collection("auth_logs").document(currentUID).collection("attempts")
                .get()
                .addOnSuccessListener(authSnapshot -> {
                    WriteBatch batch = db.batch();
                    AtomicInteger count = new AtomicInteger(0);
                    for (DocumentSnapshot doc : authSnapshot.getDocuments()) {
                        Long ts = doc.getLong("timestamp");
                        if (ts != null && Math.abs(ts - enrolTimestamp) <= 75000) {
                            batch.delete(doc.getReference());
                            count.getAndIncrement();
                        }
                    }
                    batch.commit().addOnSuccessListener(aVoid -> {
                        showCustomToast("✅ Log + " + count.get() + " auth logs deleted");
                        fetchLogs();
                    });
                });
    }
}
