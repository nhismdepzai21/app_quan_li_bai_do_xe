package com.example.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class quanliActivity extends AppCompatActivity {

    private TextView tvTime, tvDate;
    private TextView textView6, tvSlot2, tvSlot3, tvSlot4, tvSlot5, tvSlot6, tvSlot7, tvSlot8, tvSlot9, tvSlot10;
    private TextView tvSlotsAvailable, tvtv;
    private Handler handler = new Handler();
    private EditText edtIp;
    private ImageView cameraView;
    private ImageView imageView6, ivSlot2, ivSlot3, ivSlot4, ivSlot5, ivSlot6, ivSlot7, ivSlot8, ivSlot9, ivSlot10;
    private WebSocket webSocket;
    private OkHttpClient client;
    private Bitmap currentFrame;
    private DatabaseReference mDatabase;

    // State tracking
    private Map<String, String> previousParkingState = new HashMap<>();
    private String previousCongVaoState = "";
    private String pendingHistoryEntryKey = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_quanli);

        initializeUI();
        loadAllStates();

        client = new OkHttpClient();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        setupFirebaseListener();

        handler.postDelayed(updateTimeRunnable, 0);
        setupButtons();
        autoConnect();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void initializeUI() {
        tvTime = findViewById(R.id.textView19);
        tvDate = findViewById(R.id.textView20);
        edtIp = findViewById(R.id.edtIp);
        cameraView = findViewById(R.id.cameraView);

        // Initialize slots 1-4
        imageView6 = findViewById(R.id.imageView6);
        textView6 = findViewById(R.id.textView6);
        ivSlot2 = findViewById(R.id.imageView4);
        tvSlot2 = findViewById(R.id.textView9);
        ivSlot3 = findViewById(R.id.imageView7);
        tvSlot3 = findViewById(R.id.textView13);
        ivSlot4 = findViewById(R.id.imageView8);
        tvSlot4 = findViewById(R.id.textView14);

        // Initialize slots 5-10
        ivSlot5 = findViewById(R.id.imageView9);
        tvSlot5 = findViewById(R.id.textView21);
        ivSlot6 = findViewById(R.id.imageView10);
        tvSlot6 = findViewById(R.id.textView22);
        ivSlot7 = findViewById(R.id.imageView11);
        tvSlot7 = findViewById(R.id.textView23);
        ivSlot8 = findViewById(R.id.imageView12);
        tvSlot8 = findViewById(R.id.textView24);
        ivSlot9 = findViewById(R.id.imageView13);
        tvSlot9 = findViewById(R.id.textView25);
        ivSlot10 = findViewById(R.id.imageView14);
        tvSlot10 = findViewById(R.id.textView26);

        tvSlotsAvailable = findViewById(R.id.textView16);
        tvtv = findViewById(R.id.tvtv);

        setupTeamInfoDialog();
    }

    private void setupTeamInfoDialog() {
        tvtv.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Thông Tin Thành Viên");
            StringBuilder message = new StringBuilder();
            message.append("1. Phùng Quang Hiệp\n");
            message.append("   MSSV: 22174800110\n\n");
            message.append("2. Đặng Văn Hưng\n");
            message.append("   MSSV: 22174800072\n\n");
            message.append("3. Phạm Huy Hoàng\n");
            message.append("   MSSV: 22174800087\n\n");
            message.append("\nĐề tài: Hệ thống quản lý bãi đỗ xe thông minh");

            builder.setMessage(message.toString());
            builder.setPositiveButton("Đóng", (dialog, which) -> dialog.dismiss());

            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    private void setupButtons() {
        Button btnXem = findViewById(R.id.btnxem);
        Button btnSearch = findViewById(R.id.btnSearch);
        Button btnConnect = findViewById(R.id.btn);

        btnConnect.setOnClickListener(v -> {
            String ip = edtIp.getText().toString().trim();
            if (ip.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập IP ESP32-CAM", Toast.LENGTH_SHORT).show();
                return;
            }
            getSharedPreferences("MyPrefs", MODE_PRIVATE).edit().putString("esp_ip", ip).apply();
            connectToESP32("ws://" + ip + ":81");
        });

        btnXem.setOnClickListener(v -> {
            Intent intent = new Intent(quanliActivity.this, lichsuActivity.class);
            startActivity(intent);
        });

        btnSearch.setOnClickListener(v -> {
            Intent intent = new Intent(quanliActivity.this, SearchHistoryActivity.class);
            startActivity(intent);
        });
    }

    private void autoConnect() {
        String savedIp = getSharedPreferences("MyPrefs", MODE_PRIVATE).getString("esp_ip", "");
        if (!savedIp.isEmpty()) {
            edtIp.setText(savedIp);
            connectToESP32("ws://" + savedIp + ":81");
        }
    }

    private void setupFirebaseListener() { // firebase
        mDatabase.child("parking").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) return;

                handleGateTrigger(dataSnapshot.child("CongVao").getValue(String.class));

                Map<String, String> currentParkingState = new HashMap<>();
                currentParkingState.put("S1", dataSnapshot.child("S1").getValue(String.class));
                currentParkingState.put("S2", dataSnapshot.child("S2").getValue(String.class));
                currentParkingState.put("S3", dataSnapshot.child("S3").getValue(String.class));
                currentParkingState.put("S4", dataSnapshot.child("S4").getValue(String.class));
                currentParkingState.put("S5", dataSnapshot.child("S5").getValue(String.class));
                currentParkingState.put("S6", dataSnapshot.child("S6").getValue(String.class));
                currentParkingState.put("S7", dataSnapshot.child("S7").getValue(String.class));
                currentParkingState.put("S8", dataSnapshot.child("S8").getValue(String.class));
                currentParkingState.put("S9", dataSnapshot.child("S9").getValue(String.class));
                currentParkingState.put("S10", dataSnapshot.child("S10").getValue(String.class));

                updateAllSlotsUI(currentParkingState);
                checkForParkingChanges(currentParkingState);

                Long availableSlots = dataSnapshot.child("slots").getValue(Long.class);
                if (availableSlots != null) {
                    tvSlotsAvailable.setText(availableSlots + "/10");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(quanliActivity.this, "Lỗi đọc Firebase: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleGateTrigger(String currentCongVao) {
        if (currentCongVao != null && !currentCongVao.equals(previousCongVaoState)) {
            if (currentCongVao.equals("Co xe vao")) {
                createPendingHistoryEntry();
            }
            previousCongVaoState = currentCongVao;
            saveStringState("previousCongVaoState", previousCongVaoState);
        }
    }

    private void createPendingHistoryEntry() {
        // Check for existing pending entry
        if (pendingHistoryEntryKey != null) {
            Toast.makeText(this, "Đang có xe khác chờ vào vị trí!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check camera status
        if (currentFrame == null) {
            Toast.makeText(this, "Camera chưa sẵn sàng, không thể chụp ảnh!", Toast.LENGTH_SHORT).show();
            return;
        }

        // First generate entry time to ensure consistency
        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        // Create database entry first to get the key
        DatabaseReference historyRef = mDatabase.child("lichsu");
        String key = historyRef.push().getKey();
        if (key == null) {
            Toast.makeText(this, "Lỗi tạo bản ghi!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create folders
        File mainFolder = new File(getExternalFilesDir(null), "SmartParking");
        File waitingFolder = new File(mainFolder, "WaitingArea");
        if (!mainFolder.exists()) mainFolder.mkdirs();
        if (!waitingFolder.exists()) waitingFolder.mkdirs();

        // Save image with entry key to ensure uniqueness
        String imageName = "waiting_" + key + ".jpg";
        File imageFile = new File(waitingFolder, imageName);
        String imagePath = imageFile.getAbsolutePath();

        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            if (!currentFrame.compress(Bitmap.CompressFormat.JPEG, 90, fos)) {
                Toast.makeText(this, "Lỗi lưu ảnh!", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi lưu ảnh!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create history entry with all required data
        LichSu newEntry = new LichSu("Xe đang chờ", date, time, "", "", "", imagePath);

        // Save to database
        historyRef.child(key).setValue(newEntry)
            .addOnSuccessListener(aVoid -> {
                pendingHistoryEntryKey = key;
                saveStringState("pendingHistoryEntryKey", pendingHistoryEntryKey);
                saveStringState("waitingImagePath", imagePath);
                Toast.makeText(quanliActivity.this, "Đã lưu ảnh xe vào thư mục chờ", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                // Clean up on failure
                new File(imagePath).delete();
                Toast.makeText(quanliActivity.this, "Lỗi lưu thông tin: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    private void checkForParkingChanges(Map<String, String> currentParkingState) {
        for (Map.Entry<String, String> entry : currentParkingState.entrySet()) {
            String slotId = entry.getKey();
            String currentStatus = entry.getValue();
            String previousStatus = previousParkingState.get(slotId);

            if (previousStatus != null && currentStatus != null && !currentStatus.equals(previousStatus)) {
                if (previousStatus.equals("Con Trong") && currentStatus.equals("Da co xe")) {
                    if (pendingHistoryEntryKey != null) {
                        assignPendingEntryToSlot(slotId);
                    }
                } else if (previousStatus.equals("Da co xe") && currentStatus.equals("Con Trong")) {
                    updateHistoryOnExit(slotId);
                }
            }
        }
        previousParkingState.clear();
        previousParkingState.putAll(currentParkingState);
        saveParkingState(previousParkingState);
    }

    private void assignPendingEntryToSlot(String slotId) {
        DatabaseReference pendingEntryRef = mDatabase.child("lichsu").child(pendingHistoryEntryKey);
        String viTri = "Vị trí " + slotId.substring(1);

        // Create slot folder if it doesn't exist
        File mainFolder = new File(getExternalFilesDir(null), "SmartParking");
        File slotFolder = new File(mainFolder, "Slot" + slotId.substring(1));
        if (!slotFolder.exists()) {
            slotFolder.mkdirs();
        }

        // Get waiting image path
        String waitingImagePath = getSharedPreferences("ParkingState", MODE_PRIVATE).getString("waitingImagePath", null);
        if (waitingImagePath != null) {
            File waitingImage = new File(waitingImagePath);
            if (waitingImage.exists()) {
                // Create new image name with timestamp
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String newImageName = slotId + "_" + timestamp + ".jpg";
                File newImage = new File(slotFolder, newImageName);

                try {
                    // Just copy the file, don't delete the original
                    java.nio.file.Files.copy(waitingImage.toPath(), newImage.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    String newImagePath = newImage.getAbsolutePath();

                    // Save the new image path for this slot
                    SharedPreferences.Editor editor = getSharedPreferences("ParkingState", MODE_PRIVATE).edit();
                    editor.putString("image_path_for_" + slotId, newImagePath);
                    editor.apply();

                    // Update history entry with new location
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("viTri", viTri);
                    updates.put("duongDanAnh", newImagePath);
                    updates.put("trangThai", "Đã đỗ xe");

                    pendingEntryRef.updateChildren(updates)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(quanliActivity.this, "Xe đã vào " + viTri, Toast.LENGTH_SHORT).show();
                            // Update UI for this slot
                            ImageView slotImage = null;
                            switch(slotId) {
                                case "S1": slotImage = imageView6; break;
                                case "S2": slotImage = ivSlot2; break;
                                case "S3": slotImage = ivSlot3; break;
                                case "S4": slotImage = ivSlot4; break;
                                case "S5": slotImage = ivSlot5; break;
                                case "S6": slotImage = ivSlot6; break;
                                case "S7": slotImage = ivSlot7; break;
                                case "S8": slotImage = ivSlot8; break;
                                case "S9": slotImage = ivSlot9; break;
                                case "S10": slotImage = ivSlot10; break;
                            }
                            if (slotImage != null) {
                                slotImage.setImageBitmap(BitmapFactory.decodeFile(newImagePath));
                            }
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(quanliActivity.this, "Lỗi cập nhật vị trí: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                } catch (IOException e) {
                    Toast.makeText(this, "Lỗi lưu ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }

        // Clear pending states
        pendingHistoryEntryKey = null;
        saveStringState("pendingHistoryEntryKey", null);
        saveStringState("waitingImagePath", null);
    }
    
    private void updateHistoryOnExit(String slotId) {
        try {
            String viTri = "Vị trí " + slotId.substring(1);
            Query lastEntryQuery = mDatabase.child("lichsu").orderByChild("viTri").equalTo(viTri);

            lastEntryQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    try {
                        String keyToUpdate = null;
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            LichSu entry = snapshot.getValue(LichSu.class);
                            if (entry != null && (entry.getNgayRa() == null || entry.getNgayRa().isEmpty())) {
                                keyToUpdate = snapshot.getKey();
                                break;
                            }
                        }

                        if (keyToUpdate != null) {
                            Map<String, Object> exitUpdate = new HashMap<>();
                            exitUpdate.put("ngayRa", new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date()));
                            exitUpdate.put("gioRa", new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
                            mDatabase.child("lichsu").child(keyToUpdate).updateChildren(exitUpdate)
                                .addOnSuccessListener(aVoid -> Toast.makeText(quanliActivity.this, "Đã cập nhật thông tin xe ra", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(quanliActivity.this, "Lỗi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(quanliActivity.this, "Lỗi xử lý dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(quanliActivity.this, "Lỗi truy vấn Firebase: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(quanliActivity.this, "Lỗi cập nhật xe ra: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAllSlotsUI(Map<String, String> currentParkingState) {
        updateSlotUI("S1", currentParkingState.get("S1"), textView6, imageView6);
        updateSlotUI("S2", currentParkingState.get("S2"), tvSlot2, ivSlot2);
        updateSlotUI("S3", currentParkingState.get("S3"), tvSlot3, ivSlot3);
        updateSlotUI("S4", currentParkingState.get("S4"), tvSlot4, ivSlot4);
        updateSlotUI("S5", currentParkingState.get("S5"), tvSlot5, ivSlot5);
        updateSlotUI("S6", currentParkingState.get("S6"), tvSlot6, ivSlot6);
        updateSlotUI("S7", currentParkingState.get("S7"), tvSlot7, ivSlot7);
        updateSlotUI("S8", currentParkingState.get("S8"), tvSlot8, ivSlot8);
        updateSlotUI("S9", currentParkingState.get("S9"), tvSlot9, ivSlot9);
        updateSlotUI("S10", currentParkingState.get("S10"), tvSlot10, ivSlot10);
    }
    
    private void updateSlotUI(String slotId, String status, TextView textView, ImageView imageView) {
        if (slotId == null || status == null) return;
        String slotName = "Vị trí " + slotId.substring(1);
        if (status.equals("Da co xe")) {
            textView.setText(slotName + " (Đã có xe)");
            String imagePath = getSharedPreferences("ParkingState", MODE_PRIVATE).getString("image_path_for_" + slotId, null);
            if (imagePath != null) {
                File imgFile = new File(imagePath);
                if (imgFile.exists()) {
                    imageView.setImageBitmap(BitmapFactory.decodeFile(imgFile.getAbsolutePath()));
                } else {
                    imageView.setImageResource(R.drawable._15a1b21f_40eb_4bbe_bd52_7366c8b89c28__removebg_preview);
                }
            } else {
                 imageView.setImageResource(R.drawable._15a1b21f_40eb_4bbe_bd52_7366c8b89c28__removebg_preview);
            }
        } else {
            textView.setText(slotName + " (Trống)");
            imageView.setImageResource(R.drawable._3660e6c6_9fc8_444a_8017_18473ce4c77c__removebg_preview);
        }
    }

    private void saveStringState(String key, String value) {
        SharedPreferences.Editor editor = getSharedPreferences("ParkingState", MODE_PRIVATE).edit();
        if (value == null) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
        editor.apply();
    }

    private void saveParkingState(Map<String, String> state) {
        SharedPreferences.Editor editor = getSharedPreferences("ParkingState", MODE_PRIVATE).edit();
        for (Map.Entry<String, String> entry : state.entrySet()) {
            editor.putString("slot_" + entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    private void loadAllStates() {
        SharedPreferences prefs = getSharedPreferences("ParkingState", MODE_PRIVATE);
        pendingHistoryEntryKey = prefs.getString("pendingHistoryEntryKey", null);
        previousCongVaoState = prefs.getString("previousCongVaoState", "Khong co xe");

        previousParkingState.put("S1", prefs.getString("slot_S1", "Con Trong"));
        previousParkingState.put("S2", prefs.getString("slot_S2", "Con Trong"));
        previousParkingState.put("S3", prefs.getString("slot_S3", "Con Trong"));
        previousParkingState.put("S4", prefs.getString("slot_S4", "Con Trong"));
        previousParkingState.put("S5", prefs.getString("slot_S5", "Con Trong"));
        previousParkingState.put("S6", prefs.getString("slot_S6", "Con Trong"));
        previousParkingState.put("S7", prefs.getString("slot_S7", "Con Trong"));
        previousParkingState.put("S8", prefs.getString("slot_S8", "Con Trong"));
        previousParkingState.put("S9", prefs.getString("slot_S9", "Con Trong"));
        previousParkingState.put("S10", prefs.getString("slot_S10", "Con Trong"));
    }

    private String saveToInternalStorage(Bitmap bitmap, String fileName) {
        File directory = getDir("imageDir", Context.MODE_PRIVATE);
        File path = new File(directory, fileName + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(path)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return path.getAbsolutePath();
    }

    private String getImagePath(String fileName) {
        return new File(getDir("imageDir", Context.MODE_PRIVATE), fileName + ".jpg").getAbsolutePath();
    }

    private void connectToESP32(String url) {
        Request request = new Request.Builder().url(url).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response res) {
                runOnUiThread(() -> Toast.makeText(quanliActivity.this, "Đã kết nối ESP32!", Toast.LENGTH_SHORT).show());
            }
            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JSONObject json = new JSONObject(text);
                    byte[] decodedBytes = Base64.decode(json.getString("image"), Base64.DEFAULT);
                    runOnUiThread(() -> {
                        currentFrame = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                        cameraView.setImageBitmap(currentFrame);
                    });
                } catch (Exception e) { e.printStackTrace(); }
            }
            @Override
            public void onFailure(WebSocket ws, Throwable t, Response res) {
                 runOnUiThread(() -> Toast.makeText(quanliActivity.this, "Kết nối thất bại", Toast.LENGTH_LONG).show());
            }
        });
    }

    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            tvTime.setText(new SimpleDateFormat("⏰HH:mm:ss", Locale.getDefault()).format(new Date()));
            tvDate.setText(new SimpleDateFormat("\uD83D\uDCC5 dd/MM/yyyy", Locale.getDefault()).format(new Date()));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateTimeRunnable);
        if (isFinishing() && webSocket != null) {
            webSocket.close(1000, "Activity destroyed");
        }
    }
}
