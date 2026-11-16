package com.example.barcodefloating;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScanHistoryAdapter extends RecyclerView.Adapter<ScanHistoryAdapter.ViewHolder> {

    private final List<ScanItem> items;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault());
    private final OnItemClickListener clickListener;

    public interface OnItemClickListener {
        void onItemClick(ScanItem item);
    }

    public ScanHistoryAdapter(List<ScanItem> items, OnItemClickListener listener) {
        this.items = items;
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scan_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScanItem item = items.get(position);
        holder.tvContent.setText(item.getContent());
        String meta = item.getFormat() + " • " + dateFormat.format(new Date(item.getTimestamp()));
        holder.tvMeta.setText(meta);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onItemClick(item);
            }
        });

        holder.btnCopy.setOnClickListener(v -> {
            Context context = v.getContext();
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText("Barcode", item.getContent());
                cm.setPrimaryClip(clip);
                Toast.makeText(context, "Copied: " + item.getContent(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvContent;
        final TextView tvMeta;
        final ImageButton btnCopy;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_barcode_content);
            tvMeta = itemView.findViewById(R.id.tv_barcode_meta);
            btnCopy = itemView.findViewById(R.id.btn_copy_item);
        }
    }
}
