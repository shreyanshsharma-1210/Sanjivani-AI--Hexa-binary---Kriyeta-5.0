package com.emergency.patient.luna.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Symptom logger grid + intensity slider.
 * Call getSelectedSymptoms() and getIntensity() after user interaction.
 */
public class SymptomLoggerView extends LinearLayout {

    private static final String[][] SYMPTOMS = {
        {"😣", "Cramps",    "CRAMPS"},
        {"💢", "Headache",  "HEADACHE"},
        {"😔", "Fatigue",   "FATIGUE"},
        {"🫀", "Bloating",  "BLOATING"},
        {"😤", "Mood ↓",   "MOOD_LOW"},
        {"🌡", "Hot Flash","HOT_FLASH"},
        {"😴", "Insomnia",  "INSOMNIA"},
        {"✨", "Energised", "ENERGISED"},
        {"⚡", "Breast Pain","BREAST_PAIN"},
        {"🤢", "Nausea",    "NAUSEA"},
        {"💊", "Acne",      "ACNE"},
        {"🦴", "Back Pain", "BACK_PAIN"}
    };

    private final List<String> selected = new ArrayList<>();
    private int intensity = 5; // 1–10

    // Slider
    private View intensityTrack;
    private View intensityFill;
    private View intensityKnob;
    private TextView intensityLabel;

    public SymptomLoggerView(Context context) { super(context); init(); }
    public SymptomLoggerView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setOrientation(VERTICAL);
        setPadding(dp(4), dp(4), dp(4), dp(4));
        buildGrid();
        buildSlider();
    }

    private void buildGrid() {
        // 3-column grid
        LinearLayout row = null;
        for (int i = 0; i < SYMPTOMS.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(HORIZONTAL);
                LinearLayout.LayoutParams rp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                rp.bottomMargin = dp(8);
                row.setLayoutParams(rp);
                addView(row);
            }
            final String[] sym = SYMPTOMS[i];
            LinearLayout btn = makeSymBtn(sym[0], sym[1], sym[2]);
            LinearLayout.LayoutParams bp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            bp.setMarginEnd(i % 3 < 2 ? dp(8) : 0);
            btn.setLayoutParams(bp);
            if (row != null) row.addView(btn);
        }
    }

    private LinearLayout makeSymBtn(String emoji, String label, String key) {
        LinearLayout btn = new LinearLayout(getContext());
        btn.setOrientation(VERTICAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(6), dp(10), dp(6), dp(10));
        btn.setBackgroundColor(0x0AFFFFFF);
        btn.setClickable(true);
        btn.setFocusable(true);

        TextView emojiTv = new TextView(getContext());
        emojiTv.setText(emoji);
        emojiTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        emojiTv.setGravity(Gravity.CENTER);
        btn.addView(emojiTv);

        TextView labelTv = new TextView(getContext());
        labelTv.setText(label);
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        labelTv.setTextColor(0x73FFFFFF);
        labelTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        labelTv.setLayoutParams(lp);
        btn.addView(labelTv);

        btn.setOnClickListener(v -> {
            boolean isActive = selected.contains(key);
            if (isActive) {
                selected.remove(key);
                btn.setBackgroundColor(0x0AFFFFFF);
                labelTv.setTextColor(0x73FFFFFF);
            } else {
                selected.add(key);
                btn.setBackgroundColor(0x2EE8637A);
                labelTv.setTextColor(0xFFE8637A);
            }
        });
        return btn;
    }

    private void buildSlider() {
        // Label row
        LinearLayout labelRow = new LinearLayout(getContext());
        labelRow.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams lrp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lrp.topMargin = dp(14);
        labelRow.setLayoutParams(lrp);

        TextView lbl = new TextView(getContext());
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        lbl.setTextColor(0x66FFFFFF);
        lbl.setText("Intensity: ");
        labelRow.addView(lbl);

        intensityLabel = new TextView(getContext());
        intensityLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        intensityLabel.setTextColor(0xCCFFFFFF);
        intensityLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        intensityLabel.setText("Moderate (5/10)");
        labelRow.addView(intensityLabel);
        addView(labelRow);

        // Track container
        LinearLayout trackContainer = new LinearLayout(getContext());
        trackContainer.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams tcp = new LayoutParams(LayoutParams.MATCH_PARENT, dp(16));
        tcp.topMargin = dp(10);
        trackContainer.setLayoutParams(tcp);
        trackContainer.setGravity(Gravity.CENTER_VERTICAL);

        intensityTrack = new View(getContext());
        intensityTrack.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(6)));
        intensityTrack.setBackgroundColor(0x1AFFFFFF);
        trackContainer.addView(intensityTrack);
        addView(trackContainer);

        // Use a RelativeLayout-style approach via post
        intensityTrack.post(() -> updateSliderUI());

        // Touch handler
        intensityTrack.setOnTouchListener((v, event) -> {
            float pct = Math.max(0f, Math.min(1f, event.getX() / v.getWidth()));
            intensity = Math.max(1, Math.min(10, Math.round(pct * 10)));
            updateSliderUI();
            return true;
        });
    }

    private void updateSliderUI() {
        if (intensityLabel != null) {
            String[] labels = {"","Very Low","Low","Mild","Mild-Moderate","Moderate",
                               "Moderate","Notable","Strong","Severe","Extreme"};
            intensityLabel.setText(labels[Math.min(intensity, 10)] + " (" + intensity + "/10)");
        }
    }

    public List<String> getSelectedSymptoms() { return new ArrayList<>(selected); }
    public int getIntensity() { return intensity; }

    /** Reset selections */
    public void clearSelections() {
        selected.clear();
        intensity = 5;
        removeAllViews();
        buildGrid();
        buildSlider();
    }

    private int dp(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getContext().getResources().getDisplayMetrics());
    }
}
