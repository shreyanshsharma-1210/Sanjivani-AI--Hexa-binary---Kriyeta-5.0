package com.emergency.patient.luna.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Moon Ring — draws the 4-phase cycle arc ring with a current-day marker dot.
 * Phase colours match the HTML reference:
 *   Menstrual=#E8637A  Follicular=#C487B8  Ovulation=#D4A76A  Luteal=#7C3D6E
 */
public class MoonRingView extends View {

    private int cycleLength = 28;
    private int currentDay  = 1;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint menstrualPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint follicularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ovulationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lutealPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MoonRingView(Context context) { super(context); init(); }
    public MoonRingView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        float stroke = dpToPx(5);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(0x0DFFFFFF);
        trackPaint.setStrokeWidth(dpToPx(4));

        menstrualPaint.setStyle(Paint.Style.STROKE);
        menstrualPaint.setColor(0xFFE8637A);
        menstrualPaint.setStrokeWidth(stroke);
        menstrualPaint.setStrokeCap(Paint.Cap.ROUND);
        menstrualPaint.setAlpha(204);

        follicularPaint.setStyle(Paint.Style.STROKE);
        follicularPaint.setColor(0xFFC487B8);
        follicularPaint.setStrokeWidth(stroke);
        follicularPaint.setStrokeCap(Paint.Cap.ROUND);
        follicularPaint.setAlpha(154);

        ovulationPaint.setStyle(Paint.Style.STROKE);
        ovulationPaint.setColor(0xFFD4A76A);
        ovulationPaint.setStrokeWidth(dpToPx(6));
        ovulationPaint.setStrokeCap(Paint.Cap.ROUND);
        ovulationPaint.setAlpha(230);

        lutealPaint.setStyle(Paint.Style.STROKE);
        lutealPaint.setColor(0xFF7C3D6E);
        lutealPaint.setStrokeWidth(stroke);
        lutealPaint.setStrokeCap(Paint.Cap.ROUND);
        lutealPaint.setAlpha(127);

        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(0xFF7C3D6E);

        markerInnerPaint.setStyle(Paint.Style.FILL);
        markerInnerPaint.setColor(0xFFC487B8);

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setMaskFilter(new BlurMaskFilter(dpToPx(6), BlurMaskFilter.Blur.NORMAL));
        glowPaint.setColor(0x88C487B8);
    }

    public void setCycleData(int currentDay, int cycleLength) {
        this.currentDay  = Math.max(1, currentDay);
        this.cycleLength = Math.max(21, cycleLength);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - dpToPx(10);
        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);

        // Draw track
        canvas.drawCircle(cx, cy, radius, trackPaint);

        // Phase segments in degrees (total 360°)
        // Menstrual: days 1–5, Follicular: 6–13, Ovulation: 14–15, Luteal: 16–end
        float degPerDay = 360f / cycleLength;

        // Start from -90° (12 o'clock)
        float startAngle = -90f;

        // ── Menstrual (1–5) ─────────────────────────────────────
        float menstrualSweep = 5 * degPerDay;
        canvas.drawArc(oval, startAngle, menstrualSweep, false, menstrualPaint);

        // ── Follicular (6–13) ───────────────────────────────────
        float follStart = startAngle + 5 * degPerDay;
        float follSweep = 8 * degPerDay;
        canvas.drawArc(oval, follStart, follSweep, false, follicularPaint);

        // ── Ovulation (14–15) ───────────────────────────────────
        int ovDay = cycleLength - 14;
        float ovStart = startAngle + (ovDay - 1) * degPerDay;
        float ovSweep = 2 * degPerDay;
        canvas.drawArc(oval, ovStart, ovSweep, false, ovulationPaint);

        // ── Luteal (16–end) ─────────────────────────────────────
        float lutStart = startAngle + (ovDay + 1) * degPerDay;
        float lutSweep = Math.max(0, (cycleLength - ovDay - 1) * degPerDay);
        canvas.drawArc(oval, lutStart, lutSweep, false, lutealPaint);

        // ── Current day marker dot ──────────────────────────────
        float markerAngle = startAngle + (currentDay - 1) * degPerDay;
        double rad = Math.toRadians(markerAngle);
        float mx = cx + (float) (radius * Math.cos(rad));
        float my = cy + (float) (radius * Math.sin(rad));

        canvas.drawCircle(mx, my, dpToPx(8), glowPaint);
        canvas.drawCircle(mx, my, dpToPx(6), markerPaint);
        canvas.drawCircle(mx, my, dpToPx(3), markerInnerPaint);
    }

    private float dpToPx(float dp) {
        return dp * getContext().getResources().getDisplayMetrics().density;
    }
}
