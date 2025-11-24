package com.example.app;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.ImageButton;
import android.widget.Toast;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class lichsuActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private LichSuAdapter adapter;
    private List<LichSu> lichSuList;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lichsu);

        // Initialize views
        recyclerView = findViewById(R.id.recyclerViewLichSu);
        ImageButton btnBack = findViewById(R.id.btnBack);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        lichSuList = new ArrayList<>();
        adapter = new LichSuAdapter(lichSuList);

        // Set up long click listener for delete
        adapter.setOnItemLongClickListener((lichSu, position) -> {
            showDeleteConfirmationDialog(lichSu, position);
            return true;
        });

        recyclerView.setAdapter(adapter);

        mDatabase = FirebaseDatabase.getInstance().getReference().child("lichsu");
        loadLichSuData();

        // Simple back button functionality without animation
        btnBack.setOnClickListener(v -> finish());
    }

    private void showDeleteConfirmationDialog(LichSu lichSu, int position) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc chắn muốn xóa mục này không?")
            .setPositiveButton("Xóa", (dialog, which) -> {
                deleteEntry(lichSu, position);
            })
            .setNegativeButton("Hủy", null)
            .show();
    }

    private void deleteEntry(LichSu lichSu, int position) {
        if (lichSu.getKey() != null) {
            // Direct deletion using the stored key
            mDatabase.child(lichSu.getKey()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    adapter.deleteItem(position);
                    Toast.makeText(lichsuActivity.this,
                        "Đã xóa thành công",
                        Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(lichsuActivity.this,
                        "Lỗi khi xóa: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                });
        } else {
            // Fallback to searching by criteria if key is not available
            mDatabase.orderByChild("viTri").equalTo(lichSu.getViTri())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        boolean found = false;
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            LichSu entry = snapshot.getValue(LichSu.class);
                            if (entry != null &&
                                entry.getViTri().equals(lichSu.getViTri()) &&
                                entry.getNgayVao().equals(lichSu.getNgayVao()) &&
                                entry.getGioVao().equals(lichSu.getGioVao())) {

                                found = true;
                                snapshot.getRef().removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        adapter.deleteItem(position);
                                        Toast.makeText(lichsuActivity.this,
                                            "Đã xóa thành công",
                                            Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(lichsuActivity.this,
                                            "Lỗi khi xóa: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                    });
                                break;
                            }
                        }

                        if (!found) {
                            Toast.makeText(lichsuActivity.this,
                                "Không tìm thấy mục cần xóa",
                                Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(lichsuActivity.this,
                            "Lỗi: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    }
                });
        }
    }

    private void loadLichSuData() {
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                lichSuList.clear();
                Map<String, LichSu> uniqueEntries = new HashMap<>();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    LichSu lichSu = snapshot.getValue(LichSu.class);
                    if (lichSu != null && lichSu.getNgayVao() != null && !lichSu.getNgayVao().isEmpty()) {
                        lichSu.setKey(snapshot.getKey());
                        String key = lichSu.getViTri() + "_" + lichSu.getNgayVao() + "_" + lichSu.getGioVao();
                        if (!uniqueEntries.containsKey(key) ||
                            (lichSu.getDuongDanAnh() != null && !lichSu.getDuongDanAnh().isEmpty())) {
                            uniqueEntries.put(key, lichSu);
                        }
                    }
                }

                lichSuList.addAll(uniqueEntries.values());

                // Enhanced sorting logic using SimpleDateFormat
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                lichSuList.sort((a, b) -> {
                    try {
                        Date dateA = dateFormat.parse(a.getNgayVao() + " " + a.getGioVao());
                        Date dateB = dateFormat.parse(b.getNgayVao() + " " + b.getGioVao());
                        return dateB.compareTo(dateA); // Sort in descending order (newest first)
                    } catch (ParseException e) {
                        // Fallback to string comparison if parsing fails
                        String timeA = a.getNgayVao() + " " + a.getGioVao();
                        String timeB = b.getNgayVao() + " " + b.getGioVao();
                        return timeB.compareTo(timeA);
                    }
                });

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(lichsuActivity.this,
                    "Lỗi: " + databaseError.getMessage(),
                    Toast.LENGTH_SHORT).show();
            }
        });
    }
}
