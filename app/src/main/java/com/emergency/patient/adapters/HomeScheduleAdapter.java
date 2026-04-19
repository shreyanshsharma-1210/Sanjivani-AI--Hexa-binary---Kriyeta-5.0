package com.emergency.patient.adapters;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.emergency.patient.R;
import com.emergency.patient.db.MedicationScheduleEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HomeScheduleAdapter — Premium schedule list adapter for the Home Dashboard.
 *
 * Features:
 *  - Pulsing amber dot for PENDING items (looping scale animation)
 *  - Tap-to-done animation: dot flips to green check-mark
 *  - Staggered entrance animations per item
 */
public class HomeScheduleAdapter extends RecyclerView.Adapter<HomeScheduleAdapter.ViewHolder> {

    public interface OnDoneListener {
        void onMarkedDone(MedicationScheduleEntity schedule);
    }

    private final List<MedicationScheduleEntity> schedules;
    private final OnDoneListener doneListener;

    public HomeScheduleAdapter(List<MedicationScheduleEntity> schedules, OnDoneListener doneListener) {
        this.schedules = schedules;
        this.doneListener = doneListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_home_schedule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MedicationScheduleEntity schedule = schedules.get(position);

        // Time
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        holder.timeView.setText(sdf.format(new Date(schedule.timeMillis)));

        // Name + category
        holder.nameView.setText(schedule.drugName != null ? schedule.drugName : "Unknown");
        holder.categoryView.setText("Prescription");

        boolean isDone = "TAKEN".equalsIgnoreCase(schedule.status);

        bindStatus(holder, isDone, false);

        // Tap on pending item to mark done
        holder.root.setOnClickListener(v -> {
            if (!"TAKEN".equalsIgnoreCase(schedule.status)) {
                schedule.status = "TAKEN";
                bindStatus(holder, true, true);
                if (doneListener != null) doneListener.onMarkedDone(schedule);
            } else {
                // Press-scale feedback only if already done
                performPressScale(v);
            }
        });

        // Staggered entrance
        holder.itemView.setAlpha(0f);
        holder.itemView.setTranslationY(24f);
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(380)
            .setStartDelay(position * 60L)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    private void bindStatus(ViewHolder holder, boolean isDone, boolean animate) {
        if (isDone) {
            // Green dot
            holder.dot.setBackgroundColor(Color.parseColor("#1D9E75"));
            holder.pulseRing.setAlpha(0f);
            stopPulse(holder);

            // Status text → check circle
            holder.statusText.setVisibility(View.GONE);
            holder.doneCheck.setVisibility(View.VISIBLE);

            if (animate) {
                holder.doneCheck.setScaleX(0f);
                holder.doneCheck.setScaleY(0f);
                holder.doneCheck.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(new OvershootInterpolator(2.5f))
                    .start();
            }
        } else {
            // Amber pending dot
            holder.dot.setBackgroundColor(Color.parseColor("#BA7517"));
            holder.statusText.setVisibility(View.VISIBLE);
            holder.statusText.setText("Pending");
            holder.statusText.setTextColor(Color.parseColor("#BA7517"));
            holder.doneCheck.setVisibility(View.GONE);

            // Start pulsing animation on the ring
            startPulse(holder);
        }
    }

    // ─── Pulse animation ──────────────────────────────────────────────────────

    private void startPulse(ViewHolder holder) {
        if (holder.pulseAnimator != null && holder.pulseAnimator.isRunning()) return;

        holder.pulseRing.setBackgroundColor(Color.parseColor("#BA7517"));

        holder.pulseAnimator = ValueAnimator.ofFloat(1f, 1.8f);
        holder.pulseAnimator.setDuration(950);
        holder.pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        holder.pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        holder.pulseAnimator.addUpdateListener(anim -> {
            float scale = (float) anim.getAnimatedValue();
            float alpha = 0.55f - (scale - 1f) * 0.61f;  // fades as it expands
            holder.pulseRing.setScaleX(scale);
            holder.pulseRing.setScaleY(scale);
            holder.pulseRing.setAlpha(Math.max(0f, alpha));
        });
        holder.pulseAnimator.start();
    }

    private void stopPulse(ViewHolder holder) {
        if (holder.pulseAnimator != null) {
            holder.pulseAnimator.cancel();
            holder.pulseAnimator = null;
        }
    }

    private void performPressScale(View v) {
        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80)
            .withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(120)
                    .setInterpolator(new OvershootInterpolator()).start()
            ).start();
    }

    @Override
    public int getItemCount() {
        return schedules == null ? 0 : schedules.size();
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        stopPulse(holder);
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {
        View root, dot, pulseRing;
        TextView timeView, nameView, categoryView, statusText;
        FrameLayout doneCheck;
        ValueAnimator pulseAnimator;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.homeScheduleItemRoot);
            timeView = itemView.findViewById(R.id.homeScheduleTime);
            nameView = itemView.findViewById(R.id.homeScheduleMedName);
            categoryView = itemView.findViewById(R.id.homeScheduleCategory);
            dot = itemView.findViewById(R.id.homeScheduleDot);
            pulseRing = itemView.findViewById(R.id.homeSchedulePulseRing);
            statusText = itemView.findViewById(R.id.homeScheduleStatusText);
            doneCheck = itemView.findViewById(R.id.homeScheduleDoneCheck);
        }
    }
}
