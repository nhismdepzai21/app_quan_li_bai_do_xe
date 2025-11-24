package com.example.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.widget.Toast;
import java.io.File;
import java.util.List;

public class ParkingHistoryAdapter extends RecyclerView.Adapter<ParkingHistoryAdapter.ViewHolder> {
    private List<LichSu> historyList;
    private OnImageClickListener imageClickListener;

    public interface OnImageClickListener {
        void onImageClick(String imagePath);
    }

    public ParkingHistoryAdapter(List<LichSu> historyList) {
        this.historyList = historyList;
    }

    public void setOnImageClickListener(OnImageClickListener listener) {
        this.imageClickListener = listener;
    }

    public void updateData(List<LichSu> newList) {
        this.historyList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_parking_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LichSu history = historyList.get(position);

        // Set license plate number with proper checking
        String bienSo = history.getBienSo();
        if (bienSo != null && !bienSo.isEmpty()) {
            holder.tvLicensePlate.setVisibility(View.VISIBLE);
            holder.tvLicensePlate.setText("Biển số: " + bienSo);
        } else {
            holder.tvLicensePlate.setVisibility(View.GONE);
        }

        // Set other information
        holder.tvSlot.setText(history.getViTri());
        holder.tvTimeIn.setText(history.getNgayVao() + " " + history.getGioVao());

        String timeOut = history.getNgayRa() != null && !history.getNgayRa().isEmpty()
                ? history.getNgayRa() + " " + history.getGioRa()
                : "Chưa ra";
        holder.tvTimeOut.setText(timeOut);

        boolean isParked = history.getNgayRa() == null || history.getNgayRa().isEmpty();
        holder.tvStatus.setText(isParked ? "Đang đỗ" : "Đã rời");
        holder.tvStatus.setTextColor(isParked ? 0xFF4CAF50 : 0xFFFF5722);

        // Load and display the license plate image with proper checking
        String imagePath = history.getDuongDanAnh();
        if (imagePath != null && !imagePath.isEmpty()) {
            File imgFile = new File(imagePath);
            if (imgFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                holder.ivLicensePlate.setImageBitmap(bitmap);
                holder.ivLicensePlate.setVisibility(View.VISIBLE);

                // Chỉ thực hiện nhận dạng nếu chưa có biển số
                if (history.getBienSo() == null || history.getBienSo().isEmpty()) {
                    recognizeLicensePlate(bitmap, history, holder);
                }

                // Set click listener for the image
                holder.ivLicensePlate.setOnClickListener(v -> {
                    if (imageClickListener != null) {
                        imageClickListener.onImageClick(imagePath);
                    }
                });
            } else {
                holder.ivLicensePlate.setVisibility(View.GONE);
            }
        } else {
            holder.ivLicensePlate.setVisibility(View.GONE);
        }
    }

    private void recognizeLicensePlate(Bitmap bitmap, LichSu history, ViewHolder holder) {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        recognizer.process(image)
            .addOnSuccessListener(visionText -> {
                String text = visionText.getText().trim();
                if (!text.isEmpty()) {
                    // Xử lý text để chỉ lấy biển số xe
                    String licensePlate = processLicensePlateText(text);
                    if (licensePlate != null) {
                        // Lưu biển số vào Firebase
                        FirebaseDatabase.getInstance()
                            .getReference()
                            .child("lichsu")
                            .orderByChild("duongDanAnh")
                            .equalTo(history.getDuongDanAnh())
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    for (DataSnapshot child : snapshot.getChildren()) {
                                        child.getRef().child("bienSo").setValue(licensePlate)
                                            .addOnSuccessListener(aVoid -> {
                                                // Cập nhật UI
                                                holder.tvLicensePlate.setVisibility(View.VISIBLE);
                                                holder.tvLicensePlate.setText("Biển số: " + licensePlate);
                                                history.setBienSo(licensePlate);
                                                Toast.makeText(holder.itemView.getContext(),
                                                    "Đã nhận diện biển số: " + licensePlate,
                                                    Toast.LENGTH_SHORT).show();
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(holder.itemView.getContext(),
                                                    "Lỗi khi lưu biển số: " + e.getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                            });
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Toast.makeText(holder.itemView.getContext(),
                                        "Lỗi truy cập database: " + error.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                                }
                            });
                    }
                }
            })
            .addOnFailureListener(e -> {
                Toast.makeText(holder.itemView.getContext(),
                    "Lỗi nhận diện biển số: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            });
    }

    private String processLicensePlateText(String text) {
        // Xử lý text để lấy biển số xe
        // Loại bỏ các ký tự không phải chữ và số
        String processed = text.replaceAll("[^A-Z0-9]", "");

        // Kiểm tra độ dài hợp lệ của biển số xe (ví dụ: từ 7-10 ký tự)
        if (processed.length() >= 7 && processed.length() <= 10) {
            return processed;
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return historyList != null ? historyList.size() : 0;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public TextView tvLicensePlate, tvSlot, tvTimeIn, tvTimeOut, tvStatus;
        public ImageView ivLicensePlate;
        private long lastClickTime = 0;
        private static final long DOUBLE_CLICK_TIME_DELTA = 300; // milliseconds

        public ViewHolder(View itemView) {
            super(itemView);
            tvLicensePlate = itemView.findViewById(R.id.tvLicensePlate);
            tvSlot = itemView.findViewById(R.id.tvSlot);
            tvTimeIn = itemView.findViewById(R.id.tvTimeIn);
            tvTimeOut = itemView.findViewById(R.id.tvTimeOut);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            ivLicensePlate = itemView.findViewById(R.id.ivLicensePlate);

            // Add click listener for license plate text
            tvLicensePlate.setOnClickListener(v -> {
                long clickTime = System.currentTimeMillis();
                if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                    // Double click detected
                    String licensePlate = tvLicensePlate.getText().toString();
                    if (licensePlate.startsWith("Biển số: ")) {
                        licensePlate = licensePlate.substring("Biển số: ".length());
                    }
                    copyToClipboard(v.getContext(), licensePlate);
                    Toast.makeText(v.getContext(), "Đã sao chép biển số: " + licensePlate, Toast.LENGTH_SHORT).show();
                }
                lastClickTime = clickTime;
            });
        }
    }

    private void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Biển số xe", text);
        clipboard.setPrimaryClip(clip);
    }
}
