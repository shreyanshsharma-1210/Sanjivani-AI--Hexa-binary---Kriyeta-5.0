package com.emergency.patient.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.TextView;
import android.net.Uri;
import android.widget.ImageView;
import com.emergency.patient.R;
import com.emergency.patient.db.MedicationEntity;

import java.util.List;

public class MedicationAdapter extends RecyclerView.Adapter<MedicationAdapter.ViewHolder> {

    private final List<MedicationEntity> medications;
    private boolean isEditMode = false;
    private OnMedicationDeleteListener deleteListener;
    private OnMedicationClickListener clickListener;

    public interface OnMedicationDeleteListener {
        void onDelete(MedicationEntity med);
    }

    public interface OnMedicationClickListener {
        void onClick(MedicationEntity med);
    }

    public MedicationAdapter(List<MedicationEntity> medications, OnMedicationDeleteListener deleteListener, OnMedicationClickListener clickListener) {
        this.medications = medications;
        this.deleteListener = deleteListener;
        this.clickListener = clickListener;
    }

    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_medication, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MedicationEntity item = medications.get(position);
        holder.drugNameText.setText(item.name);
        holder.brandNameText.setText(item.brandName != null && !item.brandName.isEmpty() ? item.brandName : "GENERIC MEDICINE");
        holder.dosageText.setText("Dosage: " + (item.dosage != null ? item.dosage : "See instructions"));
        
        String expiry = item.expiry != null ? item.expiry : "Not set";
        holder.expiryText.setText("Exp: " + expiry);

        // Edit Mode handling
        if (isEditMode) {
            holder.deleteButton.setVisibility(View.VISIBLE);
            holder.deleteButton.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.onDelete(item);
            });
            holder.itemView.setOnClickListener(null);
        } else {
            holder.deleteButton.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onClick(item);
            });
        }

        if (item.imageUri != null && !item.imageUri.isEmpty()) {
            // ... (rest of image loading logic)
            loadMedicationImage(holder, item.imageUri);
        } else {
            showPlaceholder(holder);
        }
    }

    private void loadMedicationImage(ViewHolder holder, String imageUri) {
        try {
            Uri uri = Uri.parse(imageUri);
            if (imageUri.startsWith("file://") || imageUri.startsWith("/")) {
                String path = uri.getPath();
                if (path != null) {
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(path);
                    if (bitmap != null) {
                        holder.medicationImageView.setImageBitmap(bitmap);
                        holder.medicationImageView.setPadding(0, 0, 0, 0);
                        holder.medicationImageView.setColorFilter(null);
                        holder.medicationImageView.setImageTintList(null);
                        holder.medicationImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        return;
                    }
                }
            }
            holder.medicationImageView.setImageURI(uri);
            holder.medicationImageView.setPadding(0, 0, 0, 0);
            holder.medicationImageView.setColorFilter(null);
            holder.medicationImageView.setImageTintList(null);
            holder.medicationImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } catch (Exception e) {
            showPlaceholder(holder);
        }
    }

    private void showPlaceholder(ViewHolder holder) {
        holder.medicationImageView.setImageResource(R.drawable.ic_nav_resume);
        int padding = (int) (20 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
        holder.medicationImageView.setPadding(padding, padding, padding, padding);
        holder.medicationImageView.setColorFilter(holder.itemView.getContext().getColor(R.color.color_primary_orange));
        holder.medicationImageView.setImageTintList(android.content.res.ColorStateList.valueOf(
            holder.itemView.getContext().getColor(R.color.color_primary_orange)));
        holder.medicationImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
    }

    @Override
    public int getItemCount() {
        return medications == null ? 0 : medications.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView brandNameText, drugNameText, dosageText, expiryText;
        ImageView medicationImageView, deleteButton;

        ViewHolder(View view) {
            super(view);
            brandNameText = view.findViewById(R.id.brandNameText);
            drugNameText = view.findViewById(R.id.drugNameText);
            dosageText = view.findViewById(R.id.dosageText);
            expiryText = view.findViewById(R.id.expiryText);
            medicationImageView = view.findViewById(R.id.medicationImageView);
            deleteButton = view.findViewById(R.id.deleteButton);
        }
    }
}
