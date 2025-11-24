package com.example.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LichSuAdapter extends RecyclerView.Adapter<LichSuAdapter.ViewHolder> {
    private List<LichSu> lichSuList;
    private OnItemLongClickListener longClickListener;
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public interface OnItemLongClickListener {
        boolean onItemLongClick(LichSu lichSu, int position);
    }

    public LichSuAdapter(List<LichSu> lichSuList) {
        this.lichSuList = lichSuList != null ? lichSuList : new ArrayList<>();
        sortLichSuList();
    }

    private void sortLichSuList() {
        Collections.sort(lichSuList, (ls1, ls2) -> {
            // First, prioritize vehicles that are still in parking ("Đang đỗ")
            boolean isParking1 = ls1.getNgayRa() == null || ls1.getNgayRa().isEmpty();
            boolean isParking2 = ls2.getNgayRa() == null || ls2.getNgayRa().isEmpty();

            if (isParking1 && !isParking2) return -1;
            if (!isParking1 && isParking2) return 1;

            // Then sort by date and time
            try {
                Date date1 = dateTimeFormat.parse(ls1.getNgayVao() + " " + ls1.getGioVao());
                Date date2 = dateTimeFormat.parse(ls2.getNgayVao() + " " + ls2.getGioVao());

                // Sort in descending order (most recent first)
                return date2.compareTo(date1);
            } catch (ParseException e) {
                return 0;
            }
        });
        notifyDataSetChanged();
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lichsu, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LichSu lichSu = lichSuList.get(position);

        // Set basic info
        holder.tvViTri.setText(lichSu.getViTri());
        holder.tvVao.setText("Vào: " + lichSu.getNgayVao() + " " + lichSu.getGioVao());

        // Set exit time if available
        if (lichSu.getNgayRa() != null && !lichSu.getNgayRa().isEmpty()) {
            holder.tvRa.setText("Ra: " + lichSu.getNgayRa() + " " + lichSu.getGioRa());
        } else {
            holder.tvRa.setText("Đang đỗ");
        }

        // Load and display the image
        String imagePath = lichSu.getDuongDanAnh();
        if (imagePath != null && !imagePath.isEmpty()) {
            File imageFile = new File(imagePath);
            if (imageFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                if (bitmap != null) {
                    holder.imgBienSo.setImageBitmap(bitmap);
                } else {
                    holder.imgBienSo.setImageResource(android.R.drawable.ic_menu_camera);
                }
            } else {
                holder.imgBienSo.setImageResource(android.R.drawable.ic_menu_camera);
            }
        } else {
            holder.imgBienSo.setImageResource(android.R.drawable.ic_menu_camera);
        }

        // Set up long click listener with final position
        final int adapterPosition = holder.getAdapterPosition();
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null && adapterPosition != RecyclerView.NO_POSITION) {
                return longClickListener.onItemLongClick(lichSu, adapterPosition);
            }
            return false;
        });
    }

    public void deleteItem(int position) {
        if (position >= 0 && position < lichSuList.size()) {
            lichSuList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, lichSuList.size());
        }
    }

    public void updateData(List<LichSu> newList) {
        this.lichSuList = newList != null ? newList : new ArrayList<>();
        sortLichSuList();
    }

    @Override
    public int getItemCount() {
        return lichSuList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvViTri, tvVao, tvRa;
        ImageView imgBienSo;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvViTri = itemView.findViewById(R.id.tvViTri);
            tvVao = itemView.findViewById(R.id.tvVao);
            tvRa = itemView.findViewById(R.id.tvRa);
            imgBienSo = itemView.findViewById(R.id.imgBienSo);
        }
    }
}
