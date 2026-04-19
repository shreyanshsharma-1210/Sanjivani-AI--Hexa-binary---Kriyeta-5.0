package com.emergency.patient.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.emergency.patient.R;
import com.emergency.patient.db.MedicationEntity;
import com.emergency.patient.db.MedicationScheduleEntity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HomeMedicationAdapter — Horizontal scroll adapter for the new Home Dashboard.
 *
 * Renders each MedicationEntity as a glass-skeuomorphic card
 * (item_home_med_card.xml). Status and accent colour are derived from
 * the associated today's schedule entries passed in.
 */
public class HomeMedicationAdapter extends RecyclerView.Adapter<HomeMedicationAdapter.ViewHolder> {

    private static final int[] ACCENT_COLORS = {
        Color.parseColor("#1D9E75"),  // teal
        Color.parseColor("#534AB7"),  // purple
        Color.parseColor("#BA7517"),  // amber
        Color.parseColor("#185FA5"),  // blue
        Color.parseColor("#D85A30"),  // coral
    };

    private final List<MedicationEntity> medications;
    private final List<MedicationScheduleEntity> todaySchedules;
    private OnMedClickListener clickListener;

    public interface OnMedClickListener {
        void onClick(MedicationEntity med);
    }

    public HomeMedicationAdapter(List<MedicationEntity> medications,
                                  List<MedicationScheduleEntity> todaySchedules,
                                  OnMedClickListener clickListener) {
        this.medications = medications;
        this.todaySchedules = todaySchedules;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_home_med_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MedicationEntity med = medications.get(position);

        // Drug name + dose
        holder.medName.setText(med.name != null ? med.name : "Unknown");
        holder.medDose.setText(med.dosage != null ? med.dosage : "See instructions");

        // Accent colour — cycle through palette
        int accent = ACCENT_COLORS[position % ACCENT_COLORS.length];
        holder.accentDot.setBackgroundColor(accent);

        // Determine status from today's schedule
        String status = "Pending";
        String nextTime = "Today";
        int statusBg = Color.parseColor("#FAEEDA");
        int statusFg = Color.parseColor("#BA7517");

        if (todaySchedules != null) {
            for (MedicationScheduleEntity s : todaySchedules) {
                if (med.name != null && med.name.equalsIgnoreCase(s.drugName)) {
                    if ("TAKEN".equalsIgnoreCase(s.status)) {
                        status = "Taken";
                        statusBg = Color.parseColor("#EAF3DE");
                        statusFg = Color.parseColor("#3B6D11");
                    } else if ("SKIPPED".equalsIgnoreCase(s.status)) {
                        status = "Missed";
                        statusBg = Color.parseColor("#FCEBEB");
                        statusFg = Color.parseColor("#A32D2D");
                    }
                    // Format next dose time
                    SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                    nextTime = sdf.format(new Date(s.timeMillis));
                    break;
                }
            }
        }

        holder.medStatus.setText(status);
        holder.medStatus.setTextColor(statusFg);
        holder.medStatus.setBackgroundColor(statusBg);
        holder.medTime.setText(nextTime);

        // Frequency placeholder
        holder.medFrequency.setText("Daily");

        // Press scale animation + click
        holder.root.setOnClickListener(v -> {
            v.animate()
                .scaleX(0.96f).scaleY(0.96f)
                .setDuration(80)
                .withEndAction(() ->
                    v.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(new OvershootInterpolator(2f))
                        .start()
                )
                .start();
            if (clickListener != null) clickListener.onClick(med);
        });

        // Entrance animation — staggered slide-up
        holder.itemView.setAlpha(0f);
        holder.itemView.setTranslationY(20f);
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setStartDelay(position * 60L)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
    }

    @Override
    public int getItemCount() {
        return medications == null ? 0 : medications.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View root, accentDot;
        TextView medName, medDose, medStatus, medFrequency, medTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.homeMedCardRoot);
            medName = itemView.findViewById(R.id.homeMedName);
            medDose = itemView.findViewById(R.id.homeMedDose);
            medStatus = itemView.findViewById(R.id.homeMedStatus);
            medFrequency = itemView.findViewById(R.id.homeMedFrequency);
            medTime = itemView.findViewById(R.id.homeMedTime);
            accentDot = itemView.findViewById(R.id.homeMedAccentDot);
        }
    }
}
