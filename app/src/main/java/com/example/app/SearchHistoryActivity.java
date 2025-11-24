package com.example.app;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import android.graphics.Bitmap;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;

public class SearchHistoryActivity extends AppCompatActivity {
    private Spinner spinnerSlot;
    private EditText edtStartDate, edtEndDate, edtTimeFrom, edtTimeTo, edtLicensePlate;
    private TextView tvTotalIn, tvTotalOut, tvAvailableSlots;
    private RecyclerView rvSearchResults;
    private ParkingHistoryAdapter adapter;
    private DatabaseReference mDatabase;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private SimpleDateFormat fullFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private FrameLayout fullscreenImageContainer;
    private ImageView fullscreenImageView;
    private List<LichSu> currentResults = new ArrayList<>();
    private TextRecognizer textRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_history);

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        mDatabase = FirebaseDatabase.getInstance().getReference();
        initializeViews();
        setupSpinner();
        setupDateTimePickers();

        adapter = new ParkingHistoryAdapter(new ArrayList<>());
        adapter.setOnImageClickListener(this::showFullscreenImage);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResults.setAdapter(adapter);

        setupButtons();
        setupFullscreenImage();
        setupExcelExport();
    }

    private void initializeViews() {
        spinnerSlot = findViewById(R.id.spinnerSlot);
        edtStartDate = findViewById(R.id.edtStartDate);
        edtEndDate = findViewById(R.id.edtEndDate);
        edtTimeFrom = findViewById(R.id.edtTimeFrom);
        edtTimeTo = findViewById(R.id.edtTimeTo);
        edtLicensePlate = findViewById(R.id.edtLicensePlate);
        tvTotalIn = findViewById(R.id.tvTotalIn);
        tvTotalOut = findViewById(R.id.tvTotalOut);
        rvSearchResults = findViewById(R.id.rvSearchResults);
        fullscreenImageContainer = findViewById(R.id.fullscreenImageContainer);
        fullscreenImageView = findViewById(R.id.fullscreenImageView);
    }

    private void setupSpinner() {
        List<String> slots = new ArrayList<>();
        slots.add("Tất cả vị trí");
        // Add positions 1 through 10
        for (int i = 1; i <= 10; i++) {
            slots.add("Vị trí " + i);
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, slots);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSlot.setAdapter(spinnerAdapter);
    }

    private void setupDateTimePickers() {
        edtStartDate.setOnClickListener(v -> showDatePicker(edtStartDate));
        edtEndDate.setOnClickListener(v -> showDatePicker(edtEndDate));
        edtTimeFrom.setOnClickListener(v -> showTimePicker(edtTimeFrom));
        edtTimeTo.setOnClickListener(v -> showTimePicker(edtTimeTo));
    }

    private void showDatePicker(final EditText editText) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
            (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                editText.setText(dateFormat.format(calendar.getTime()));
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void showTimePicker(final EditText editText) {
        Calendar calendar = Calendar.getInstance();
        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
            (view, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                editText.setText(timeFormat.format(calendar.getTime()));
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true);
        timePickerDialog.show();
    }

    private void setupButtons() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        Button btnSearch = findViewById(R.id.btnSearch);

        // Simple back button without animation
        btnBack.setOnClickListener(v -> finish());

        btnSearch.setOnClickListener(v -> performSearch());
    }

    private void performSearch() {
        try {
            String selectedSlot = spinnerSlot.getSelectedItem() != null ?
                spinnerSlot.getSelectedItem().toString() : "Tất cả vị trí";
            String startDate = edtStartDate.getText() != null ?
                edtStartDate.getText().toString().trim() : "";
            String endDate = edtEndDate.getText() != null ?
                edtEndDate.getText().toString().trim() : "";
            String timeFrom = edtTimeFrom.getText() != null ?
                edtTimeFrom.getText().toString().trim() : "";
            String timeTo = edtTimeTo.getText() != null ?
                edtTimeTo.getText().toString().trim() : "";

            // Validate date format if entered
            if (!startDate.isEmpty() && !isValidDate(startDate)) {
                Toast.makeText(this, "Ngày bắt đầu không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!endDate.isEmpty() && !isValidDate(endDate)) {
                Toast.makeText(this, "Ngày kết thúc không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validate time format if entered
            if (!timeFrom.isEmpty() && !isValidTime(timeFrom)) {
                Toast.makeText(this, "Thời gian bắt đầu không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!timeTo.isEmpty() && !isValidTime(timeTo)) {
                Toast.makeText(this, "Thời gian kết thúc không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }

            Query query = mDatabase.child("lichsu");

            if (!selectedSlot.equals("Tất cả vị trí")) {
                query = query.orderByChild("viTri").equalTo(selectedSlot);
            }

            query.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    List<LichSu> filteredResults = new ArrayList<>();
                    int totalIn = 0;
                    int totalOut = 0;

                    try {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            LichSu entry = snapshot.getValue(LichSu.class);
                            if (entry != null &&
                                entry.getNgayVao() != null &&
                                entry.getGioVao() != null &&
                                isMatchingFilter(entry, startDate, endDate, timeFrom, timeTo)) {
                                filteredResults.add(entry);
                                totalIn++;
                                if (entry.getNgayRa() != null && !entry.getNgayRa().isEmpty()) {
                                    totalOut++;
                                }
                            }
                        }
                        updateUI(filteredResults, totalIn, totalOut);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(SearchHistoryActivity.this,
                            "Lỗi xử lý dữ liệu: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(SearchHistoryActivity.this,
                        "Lỗi truy vấn: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi tìm kiếm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isMatchingFilter(LichSu entry, String startDate, String endDate,
                                   String timeFrom, String timeTo) {
        try {
            if (entry.getNgayVao() == null || entry.getGioVao() == null) {
                return false;
            }

            // Check license plate filter
            String licensePlate = edtLicensePlate.getText().toString().trim().toUpperCase();
            if (!licensePlate.isEmpty() &&
                (entry.getBienSo() == null ||
                !entry.getBienSo().toUpperCase().contains(licensePlate))) {
                return false;
            }

            // If no other filters are set and license plate matches (or no license plate filter),
            // return true
            if (startDate.isEmpty() && endDate.isEmpty() &&
                timeFrom.isEmpty() && timeTo.isEmpty()) {
                return true;
            }

            Date entryDate = dateFormat.parse(entry.getNgayVao());
            Date entryTime = timeFormat.parse(entry.getGioVao());

            if (entryDate == null || entryTime == null) {
                return false;
            }

            // Check date range
            if (!startDate.isEmpty()) {
                Date filterStartDate = dateFormat.parse(startDate);
                if (filterStartDate != null && entryDate.before(filterStartDate)) {
                    return false;
                }
            }

            if (!endDate.isEmpty()) {
                Date filterEndDate = dateFormat.parse(endDate);
                if (filterEndDate != null && entryDate.after(filterEndDate)) {
                    return false;
                }
            }

            // Check time range
            if (!timeFrom.isEmpty()) {
                Date filterTimeFrom = timeFormat.parse(timeFrom);
                if (filterTimeFrom != null && entryTime.before(filterTimeFrom)) {
                    return false;
                }
            }

            if (!timeTo.isEmpty()) {
                Date filterTimeTo = timeFormat.parse(timeTo);
                if (filterTimeTo != null && entryTime.after(filterTimeTo)) {
                    return false;
                }
            }

            return true;
        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isValidDate(String date) {
        try {
            Date parsedDate = dateFormat.parse(date);
            return parsedDate != null;
        } catch (ParseException e) {
            return false;
        }
    }

    private boolean isValidTime(String time) {
        try {
            Date parsedTime = timeFormat.parse(time);
            return parsedTime != null;
        } catch (ParseException e) {
            return false;
        }
    }

    private void showFullscreenImage(String imagePath) {
        if (imagePath != null && !imagePath.isEmpty()) {
            File imgFile = new File(imagePath);
            if (imgFile.exists()) {
                fullscreenImageView.setImageBitmap(BitmapFactory.decodeFile(imgFile.getAbsolutePath()));
                fullscreenImageContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupFullscreenImage() {
        fullscreenImageContainer.setOnClickListener(v -> {
            fullscreenImageContainer.setVisibility(View.GONE);
        });
    }

    private void setupExcelExport() {
        Button btnExportExcel = findViewById(R.id.btnExportExcel);
        btnExportExcel.setOnClickListener(v -> exportToExcel());
    }

    private void processImageForOCR(String imagePath, WritableSheet sheet, int row) {
        try {
            File imgFile = new File(imagePath);
            if (imgFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                if (bitmap != null) {
                    InputImage image = InputImage.fromBitmap(bitmap, 0);
                    textRecognizer.process(image)
                        .addOnSuccessListener(visionText -> {
                            String recognizedText = visionText.getText();
                            try {
                                // Add the recognized text to the Excel sheet
                                sheet.addCell(new Label(0, row, recognizedText));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        })
                        .addOnFailureListener(e -> {
                            e.printStackTrace();
                            try {
                                // If OCR fails, use the original license plate text
                                sheet.addCell(new Label(0, row, currentResults.get(row-1).getBienSo()));
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void exportToExcel() {
        if (currentResults.isEmpty()) {
            Toast.makeText(this, "Không có dữ liệu để xuất", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!checkStoragePermission()) {
            requestStoragePermission();
            return;
        }

        try {
            // Create dynamic filename based on search criteria
            String startDate = edtStartDate.getText().toString().trim().replace("/", "_");
            String endDate = edtEndDate.getText().toString().trim().replace("/", "_");
            String timeFrom = edtTimeFrom.getText().toString().trim().replace(":", "h");
            String timeTo = edtTimeTo.getText().toString().trim().replace(":", "h");

            String fileName;
            if (startDate.isEmpty() && endDate.isEmpty() && timeFrom.isEmpty() && timeTo.isEmpty()) {
                fileName = "lichsubaidoxe_tatca.xls";
            } else if (!startDate.isEmpty() && !endDate.isEmpty()) {
                fileName = String.format("lichsubaidoxe_%s_%s_%s_%s.xls",
                    startDate, timeFrom.isEmpty() ? "00h00" : timeFrom,
                    endDate, timeTo.isEmpty() ? "23h59" : timeTo);
            } else if (!startDate.isEmpty()) {
                fileName = String.format("lichsubaidoxe_tu_%s_%s.xls",
                    startDate, timeFrom.isEmpty() ? "00h00" : timeFrom);
            } else if (!endDate.isEmpty()) {
                fileName = String.format("lichsubaidoxe_den_%s_%s.xls",
                    endDate, timeTo.isEmpty() ? "23h59" : timeTo);
            } else {
                fileName = "lichsubaidoxe.xls";
            }

            File directory;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BaiDoXe");
            } else {
                directory = new File(Environment.getExternalStorageDirectory(), "BaiDoXe");
            }

            if (!directory.exists()) {
                directory.mkdirs();
            }
            File file = new File(directory, fileName);

            WorkbookSettings wbSettings = new WorkbookSettings();
            wbSettings.setLocale(new Locale("vi", "VN"));
            wbSettings.setUseTemporaryFileDuringWrite(true);
            WritableWorkbook workbook = Workbook.createWorkbook(file, wbSettings);
            WritableSheet sheet = workbook.createSheet("Lịch sử", 0);

            // Write headers with custom width
            sheet.setColumnView(0, 25); // Biển số xe
            sheet.setColumnView(1, 15); // Vị trí
            sheet.setColumnView(2, 15); // Ngày vào
            sheet.setColumnView(3, 10); // Giờ vào
            sheet.setColumnView(4, 15); // Ngày ra
            sheet.setColumnView(5, 10); // Giờ ra
            sheet.setColumnView(6, 15); // Trạng thái

            // Headers
            sheet.addCell(new Label(0, 0, "Biển số xe"));
            sheet.addCell(new Label(1, 0, "Vị trí"));
            sheet.addCell(new Label(2, 0, "Ngày vào"));
            sheet.addCell(new Label(3, 0, "Giờ vào"));
            sheet.addCell(new Label(4, 0, "Ngày ra"));
            sheet.addCell(new Label(5, 0, "Giờ ra"));
            sheet.addCell(new Label(6, 0, "Trạng thái"));

            // Write data
            for (int i = 0; i < currentResults.size(); i++) {
                LichSu entry = currentResults.get(i);
                int row = i + 1;

                sheet.addCell(new Label(0, row, entry.getBienSo()));
                sheet.addCell(new Label(1, row, entry.getViTri()));
                sheet.addCell(new Label(2, row, entry.getNgayVao()));
                sheet.addCell(new Label(3, row, entry.getGioVao()));

                // Handle exit date and time
                String ngayRa = entry.getNgayRa();
                String gioRa = entry.getGioRa();
                boolean hasExitInfo = ngayRa != null && !ngayRa.isEmpty() && gioRa != null && !gioRa.isEmpty();

                sheet.addCell(new Label(4, row, hasExitInfo ? ngayRa : ""));
                sheet.addCell(new Label(5, row, hasExitInfo ? gioRa : ""));

                // Set status based on exit information
                String status = hasExitInfo ? "Đã rời" : "Đang đỗ";
                sheet.addCell(new Label(6, row, status));
            }

            // Add summary at the bottom
            int summaryRow = currentResults.size() + 2;
            int totalParked = currentResults.size();
            int totalLeft = (int) currentResults.stream()
                    .filter(entry -> entry.getNgayRa() != null && !entry.getNgayRa().isEmpty())
                    .count();
            int currentlyParked = totalParked - totalLeft;

            sheet.addCell(new Label(0, summaryRow, "Tổng số xe:"));
            sheet.addCell(new Label(1, summaryRow, String.valueOf(totalParked)));
            sheet.addCell(new Label(2, summaryRow, "Đã rời:"));
            sheet.addCell(new Label(3, summaryRow, String.valueOf(totalLeft)));
            sheet.addCell(new Label(4, summaryRow, "Đang đỗ:"));
            sheet.addCell(new Label(5, summaryRow, String.valueOf(currentlyParked)));

            workbook.write();
            workbook.close();

            // Show success message with file path
            Toast.makeText(this, "Đã xuất file Excel thành công: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();

            // Share the file
            shareExcelFile(file);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi xuất file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int write = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            int read = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE);
            return write == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                   read == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else {
            requestPermissions(new String[]{
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }, 1);
        }
    }

    private void shareExcelFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/vnd.ms-excel");
            intent.putExtra(Intent.EXTRA_STREAM, uri);

            // Create subject with search period
            String startDate = edtStartDate.getText().toString().trim();
            String endDate = edtEndDate.getText().toString().trim();
            String timeFrom = edtTimeFrom.getText().toString().trim();
            String timeTo = edtTimeTo.getText().toString().trim();

            String subject;
            if (startDate.isEmpty() && endDate.isEmpty() && timeFrom.isEmpty() && timeTo.isEmpty()) {
                // If no date/time filters are set
                subject = "Lịch sử bãi đỗ xe (Tất cả)";
            } else {
                // Use available date/time information
                String fromPart = startDate.isEmpty() ? "" : startDate;
                if (!timeFrom.isEmpty()) {
                    fromPart += " " + timeFrom;
                }

                String toPart = endDate.isEmpty() ? "" : endDate;
                if (!timeTo.isEmpty()) {
                    toPart += " " + timeTo;
                }

                if (fromPart.isEmpty() && !toPart.isEmpty()) {
                    subject = String.format("Lịch sử bãi đỗ xe (đến %s)", toPart);
                } else if (!fromPart.isEmpty() && toPart.isEmpty()) {
                    subject = String.format("Lịch sử bãi đỗ xe (từ %s)", fromPart);
                } else if (!fromPart.isEmpty() && !toPart.isEmpty()) {
                    subject = String.format("Lịch sử bãi đỗ xe (%s - %s)", fromPart, toPart);
                } else {
                    subject = "Lịch sử bãi đỗ xe";
                }
            }

            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Chia sẻ file Excel"));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi chia sẻ file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI(List<LichSu> results, int totalIn, int totalOut) {
        try {
            currentResults = results != null ? results : new ArrayList<>();
            if (adapter != null) {
                adapter.updateData(currentResults);
            }
            if (tvTotalIn != null) {
                tvTotalIn.setText(String.valueOf(totalIn));
            }
            if (tvTotalOut != null) {
                tvTotalOut.setText(String.valueOf(totalOut));
            }

            if (results == null || results.isEmpty()) {
                Toast.makeText(this, "Không tìm thấy kết quả", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi cập nhật giao diện", Toast.LENGTH_SHORT).show();
        }
    }
}
