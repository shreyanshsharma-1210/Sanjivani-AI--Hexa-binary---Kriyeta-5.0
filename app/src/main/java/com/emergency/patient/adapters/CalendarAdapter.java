package com.emergency.patient.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.emergency.patient.R;
import java.util.Calendar;
import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {

    private final List<Calendar> dates;
    private final OnDateSelectedListener listener;
    private int selectedPosition = -1;

    public interface OnDateSelectedListener {
        void onDateSelected(Calendar date);
    }

    public CalendarAdapter(List<Calendar> dates, OnDateSelectedListener listener) {
        this.dates = dates;
        this.listener = listener;
        
        // Default select today
        Calendar today = Calendar.getInstance();
        for (int i = 0; i < dates.size(); i++) {
            if (isSameDay(dates.get(i), today)) {
                selectedPosition = i;
                break;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Calendar date = dates.get(position);
        holder.dateNumberText.setText(String.valueOf(date.get(Calendar.DAY_OF_MONTH)));
        
        String[] days = {"", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        holder.dayNameText.setText(days[date.get(Calendar.DAY_OF_WEEK)]);

        com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) holder.itemView;

        if (position == selectedPosition) {
            card.setCardBackgroundColor(holder.itemView.getContext().getColor(R.color.color_primary_orange));
            card.setStrokeColor(holder.itemView.getContext().getColor(R.color.color_primary_orange));
            holder.dateNumberText.setTextColor(Color.WHITE);
            holder.dayNameText.setTextColor(Color.parseColor("#E0E0E0"));
            holder.indicatorDot.setVisibility(View.GONE);
        } else {
            card.setCardBackgroundColor(Color.WHITE);
            card.setStrokeColor(holder.itemView.getContext().getColor(R.color.color_divider));
            holder.dateNumberText.setTextColor(holder.itemView.getContext().getColor(R.color.color_text_primary));
            holder.dayNameText.setTextColor(holder.itemView.getContext().getColor(R.color.color_text_secondary));
            holder.indicatorDot.setVisibility(View.INVISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            int oldPos = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(oldPos);
            notifyItemChanged(selectedPosition);
            listener.onDateSelected(date);
        });
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    public int getItemCount() {
        return dates.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView dateNumberText;
        TextView dayNameText;
        View indicatorDot;

        ViewHolder(View view) {
            super(view);
            dateNumberText = view.findViewById(R.id.dateNumberText);
            dayNameText = view.findViewById(R.id.dayNameText);
            indicatorDot = view.findViewById(R.id.indicatorDot);
        }
    }
}
