package com.emergency.patient.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.emergency.patient.R;
import com.emergency.patient.db.MedicationScheduleEntity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private final List<MedicationScheduleEntity> schedules;
    private boolean isEditMode = false;
    private OnScheduleEditListener editListener;

    public interface OnScheduleEditListener {
        void onEditTime(MedicationScheduleEntity schedule);
    }

    public ScheduleAdapter(List<MedicationScheduleEntity> schedules, OnScheduleEditListener listener) {
        this.schedules = schedules;
        this.editListener = listener;
    }

    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MedicationScheduleEntity item = schedules.get(position);
        
        holder.drugNameText.setText(item.drugName);
        
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(item.timeMillis);
        
        SimpleDateFormat timeSdf = new SimpleDateFormat("hh:mm", Locale.getDefault());
        SimpleDateFormat amPmSdf = new SimpleDateFormat("a", Locale.getDefault());
        
        holder.timeText.setText(timeSdf.format(cal.getTime()));

        String status = item.status != null ? item.status : "PENDING";
        
        // Edit Mode handling
        if (isEditMode) {
            holder.editTimeButton.setVisibility(View.VISIBLE);
            holder.statusContainer.setVisibility(View.GONE);
            holder.editTimeButton.setOnClickListener(v -> {
                if (editListener != null) editListener.onEditTime(item);
            });
        } else {
            holder.editTimeButton.setVisibility(View.GONE);
            holder.statusContainer.setVisibility(View.VISIBLE);
        }

        if (status.equalsIgnoreCase("TAKEN")) {
            holder.statusText.setText("Taken");
            holder.statusText.setTextColor(Color.WHITE);
            holder.statusContainer.setCardBackgroundColor(Color.parseColor("#4CAF50")); // Material Green
        } else if (status.equalsIgnoreCase("SKIPPED")) {
            holder.statusText.setText("Skipped");
            holder.statusText.setTextColor(Color.WHITE);
            holder.statusContainer.setCardBackgroundColor(Color.parseColor("#F44336")); // Material Red
        } else {
            holder.statusText.setText("Pending");
            holder.statusText.setTextColor(Color.parseColor("#1976D2")); // Material Blue
            holder.statusContainer.setCardBackgroundColor(Color.parseColor("#E3F2FD")); // Very Light Blue
        }
    }

    @Override
    public int getItemCount() {
        return schedules == null ? 0 : schedules.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView drugNameText, timeText, dosageText, timeRangeText, statusText;
        androidx.cardview.widget.CardView statusContainer;
        ImageView editTimeButton;

        ViewHolder(View view) {
            super(view);
            drugNameText = view.findViewById(R.id.drugNameText);
            timeText = view.findViewById(R.id.timeText);
            dosageText = view.findViewById(R.id.dosageText);
            timeRangeText = view.findViewById(R.id.timeRangeText);
            statusText = view.findViewById(R.id.statusText);
            statusContainer = view.findViewById(R.id.statusContainer);
            editTimeButton = view.findViewById(R.id.editTimeButton);
        }
    }
}
