package com.emergency.patient.luna.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Canvas-based multi-line hormone trend chart.
 * Draws temperature (rose), HR (gold dashed), HRV (aqua) lines
 * over phase-shaded background bands.
 */
public class HormoneChartView extends View {

    private List<Float> tempPoints = new ArrayList<>();
    private List<Float> hrPoints   = new ArrayList<>();
    private List<Float> hrvPoints  = new ArrayList<>();
    private int cycleLength = 28;
    private int currentDay  = 1;

    private final Paint tempPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hrPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hrvPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint phasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HormoneChartView(Context context) { super(context); init(); }
    public HormoneChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        tempPaint.setStyle(Paint.Style.STROKE);
        tempPaint.setColor(0xFFE8637A);
        tempPaint.setStrokeWidth(dpToPx(2));
        tempPaint.setStrokeCap(Paint.Cap.ROUND);
        tempPaint.setStrokeJoin(Paint.Join.ROUND);

        hrPaint.setStyle(Paint.Style.STROKE);
        hrPaint.setColor(0xFFD4A76A);
        hrPaint.setStrokeWidth(dpToPx(1.5f));
        hrPaint.setStrokeCap(Paint.Cap.ROUND);
        hrPaint.setAlpha(200);

        hrvPaint.setStyle(Paint.Style.STROKE);
        hrvPaint.setColor(0xFF5BBFB5);
        hrvPaint.setStrokeWidth(dpToPx(1.5f));
        hrvPaint.setAlpha(180);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setColor(0x0DFFFFFF);
        gridPaint.setStrokeWidth(dpToPx(1));

        phasePaint.setStyle(Paint.Style.FILL);

        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(0xFFC487B8);
    }

    public void setData(List<Float> temps, List<Float> hrs, List<Float> hrvs,
                        int cycleLen, int curDay) {
        tempPoints.clear(); tempPoints.addAll(temps);
        hrPoints.clear();   hrPoints.addAll(hrs);
        hrvPoints.clear();  hrvPoints.addAll(hrvs);
        cycleLength = Math.max(21, cycleLen);
        currentDay  = Math.max(1, curDay);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float padL = dpToPx(4), padR = dpToPx(4);
        float padT = dpToPx(8), padB = dpToPx(20);
        float cw = w - padL - padR;
        float ch = h - padT - padB;

        // Phase shading
        drawPhaseShading(canvas, padL, padT, cw, ch);

        // Grid lines
        for (int i = 1; i <= 3; i++) {
            float y = padT + ch * i / 4f;
            canvas.drawLine(padL, y, padL + cw, y, gridPaint);
        }

        // Draw lines
        drawLine(canvas, tempPoints, tempPaint, padL, padT, cw, ch, 36.2f, 38f);
        drawLine(canvas, hrPoints,   hrPaint,   padL, padT, cw, ch, 55f,  105f);
        drawLine(canvas, hrvPoints,  hrvPaint,  padL, padT, cw, ch, 10f,   80f);

        // Current day marker
        float cx = padL + ((currentDay - 1f) / cycleLength) * cw;
        Paint dayLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dayLinePaint.setStyle(Paint.Style.STROKE);
        dayLinePaint.setColor(0x44C487B8);
        dayLinePaint.setStrokeWidth(dpToPx(1));
        canvas.drawLine(cx, padT, cx, padT + ch, dayLinePaint);

        if (!tempPoints.isEmpty() && currentDay <= tempPoints.size()) {
            float y = normalise(tempPoints.get(currentDay - 1), 36.2f, 38f, padT, ch);
            canvas.drawCircle(cx, y, dpToPx(5), markerPaint);
        }

        // Phase x-labels
        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setTextSize(dpToPx(8));
        labelPaint.setColor(0x66FFFFFF);
        canvas.drawText("Period", padL + 2, padT + ch + dpToPx(14), labelPaint);
        int follStart = 5;
        canvas.drawText("Follicular", padL + (follStart / (float) cycleLength) * cw,
                padT + ch + dpToPx(14), labelPaint);
        int ovDay = cycleLength - 14;
        canvas.drawText("Ov", padL + ((ovDay - 1f) / cycleLength) * cw + 2,
                padT + ch + dpToPx(14), labelPaint);
        canvas.drawText("Luteal →", padL + ((ovDay + 2f) / cycleLength) * cw,
                padT + ch + dpToPx(14), labelPaint);
    }

    private void drawLine(Canvas canvas, List<Float> points, Paint paint,
                          float padL, float padT, float cw, float ch, float minV, float maxV) {
        if (points == null || points.size() < 2) return;
        Path path = new Path();
        for (int i = 0; i < points.size(); i++) {
            float x = padL + (i / (float) (points.size() - 1)) * cw;
            float y = normalise(points.get(i), minV, maxV, padT, ch);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
    }

    private float normalise(float v, float min, float max, float padT, float ch) {
        return padT + ch - ((v - min) / (max - min)) * ch;
    }

    private void drawPhaseShading(Canvas canvas, float padL, float padT, float cw, float ch) {
        int[][] phases = {
            {0, 5,  0x0DE8637A},
            {5, cycleLength - 14, 0x0DC487B8},
            {cycleLength - 14, cycleLength - 12, 0x14D4A76A},
            {cycleLength - 12, cycleLength, 0x0D7C3D6E}
        };
        for (int[] p : phases) {
            float x1 = padL + (p[0] / (float) cycleLength) * cw;
            float x2 = padL + (p[1] / (float) cycleLength) * cw;
            phasePaint.setColor(p[2]);
            canvas.drawRect(x1, padT, x2, padT + ch, phasePaint);
        }
    }

    private float dpToPx(float dp) {
        return dp * getContext().getResources().getDisplayMetrics().density;
    }
}
