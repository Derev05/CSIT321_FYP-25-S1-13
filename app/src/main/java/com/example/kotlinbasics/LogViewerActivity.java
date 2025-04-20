package com.example.kotlinbasics;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.*;

import java.util.*;

public class LogViewerActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private ListView listView;
    private TextView headerText;
    private SearchView searchView;
    private Button sortButton;

    private ArrayAdapter<String> adapter;
    private ArrayList<String> displayList = new ArrayList<>();
    private Map<String, String> uidToEmailMap = new HashMap<>();
    private boolean showingLogs = false;
    private boolean sortAscending = true;
    private String currentUID = null;
    private final String RETURN_HINT = "🔙 Tap back button to return to user list.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        db = FirebaseFirestore.getInstance();
        listView = findViewById(R.id.logListView);
        headerText = findViewById(R.id.headerText);
        searchView = findViewById(R.id.searchView);
        sortButton = findViewById(R.id.sortButton);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        listView.setAdapter(adapter);

        fetchUIDs();

        listView.setOnItemClickListener((adapterView, view, position, id) -> {
            if (!showingLogs) {
                String tappedEmail = displayList.get(position).replace("📧 ", "").trim();
                for (Map.Entry<String, String> entry : uidToEmailMap.entrySet()) {
                    if (entry.getValue().equals(tappedEmail)) {
                        currentUID = entry.getKey();
                        fetchLogsForUID(currentUID);
                        searchView.setVisibility(View.GONE);
                        sortButton.setVisibility(View.GONE);
                        break;
                    }
                }
            } else {
                if (displayList.get(position).contains("Tap back button")) {
                    fetchUIDs();
                    searchView.setVisibility(View.VISIBLE);
                    sortButton.setVisibility(View.VISIBLE);
                }
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterEmailList(newText);
                return true;
            }
        });

        sortButton.setOnClickListener(v -> {
            sortAscending = !sortAscending;
            sortEmailList();
        });
    }

    private void fetchUIDs() {
        showingLogs = false;
        headerText.setText("Select a user to view logs:");
        displayList.clear();
        uidToEmailMap.clear();

        db.collection("enrol_logs").get().addOnSuccessListener(snapshot -> {
            if (snapshot.isEmpty()) {
                displayList.add("⚠️ No users with enrolment logs");
                adapter.notifyDataSetChanged();
                return;
            }

            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                String uid = doc.getId();

                db.collection("enrol_logs")
                        .document(uid)
                        .collection("attempts")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .addOnSuccessListener(logs -> {
                            String displayEmail = "Anonymous";
                            if (!logs.isEmpty()) {
                                DocumentSnapshot logDoc = logs.getDocuments().get(0);
                                String email = logDoc.getString("email");
                                if (email != null && !email.isEmpty()) {
                                    displayEmail = email;
                                }
                            }
                            uidToEmailMap.put(uid, displayEmail);
                            filterEmailList(searchView.getQuery().toString());
                        });
            }

        }).addOnFailureListener(e -> Log.e("LogViewer", "❌ Failed to fetch enrol_logs", e));
    }

    private void filterEmailList(String query) {
        displayList.clear();

        List<String> emailList = new ArrayList<>(uidToEmailMap.values());
        for (String email : emailList) {
            if (email.toLowerCase().contains(query.toLowerCase())) {
                displayList.add("📧 " + email);
            }
        }

        sortEmailList();
    }

    private void sortEmailList() {
        Collections.sort(displayList, (a, b) -> {
            String emailA = a.replace("📧 ", "").toLowerCase();
            String emailB = b.replace("📧 ", "").toLowerCase();
            return sortAscending ? emailA.compareTo(emailB) : emailB.compareTo(emailA);
        });
        adapter.notifyDataSetChanged();
    }

    private void fetchLogsForUID(String uid) {
        showingLogs = true;
        displayList.clear();

        // Fetch the latest log to get email for the header
        db.collection("enrol_logs")
                .document(uid)
                .collection("attempts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(latestSnapshot -> {
                    String displayEmail = "Unknown User";
                    if (!latestSnapshot.isEmpty()) {
                        DocumentSnapshot latestDoc = latestSnapshot.getDocuments().get(0);
                        String email = latestDoc.getString("email");
                        if (email != null && !email.isEmpty()) {
                            displayEmail = email;
                        }
                    }
                    headerText.setText("Logs for: " + displayEmail); // ✅ update header

                    // Now fetch all logs
                    db.collection("enrol_logs")
                            .document(uid)
                            .collection("attempts")
                            .orderBy("timestamp", Query.Direction.DESCENDING)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                if (snapshot.isEmpty()) {
                                    displayList.add("⚠️ No logs for this user.");
                                } else {
                                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                                        double prob = doc.contains("probability") ? doc.getDouble("probability") : 0.0;
                                        String probFormatted = String.format("%.2f%%", prob * 100);
                                        boolean decision = doc.contains("decision") && Boolean.TRUE.equals(doc.getBoolean("decision"));
                                        String time = doc.contains("readable_time") ? doc.getString("readable_time") : "Unknown time";
                                        displayList.add("📅 " + time + "\n🧠 Probability: " + probFormatted + "\n🕵️ Spoof: " + decision);
                                    }
                                }

                                displayList.add(RETURN_HINT);
                                adapter.notifyDataSetChanged();
                            })
                            .addOnFailureListener(e -> Log.e("LogViewer", "❌ Failed to fetch logs", e));
                })
                .addOnFailureListener(e -> Log.e("LogViewer", "❌ Failed to fetch email for header", e));
    }
}
