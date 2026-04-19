package com.emergency.patient.activities;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * AdherenceRingView — Custom animated arc ring that displays dose adherence %.
 *
 * Renders: a background track ring (faint teal) + animated foreground arc
 * with a sweep gradient, plus a centred percentage text.
 * The sweep animates from 0° on attach using a ValueAnimator.
 */
public class AdherenceRingView extends View {

    private Paint trackPaint;
    private Paint ringPaint;
    private Paint textPaint;
    private RectF arcRect;

    private float targetSweep = 0f;      // 0–360
    private float currentSweep = 0f;
    private int percentage = 0;
    private int currentDisplayPct = 0;

    private ValueAnimator sweepAnimator;
    private ValueAnimator pctAnimator;

    public AdherenceRingView(Context context) {
        super(context);
        init();
    }

    public AdherenceRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AdherenceRingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Track (background) paint — faint teal
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(Color.parseColor("#201D9E75"));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeWidth(dpToPx(9));

        // Foreground ring paint (colour set dynamically via SweepGradient)
        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setStrokeWidth(dpToPx(9));

        // Percentage text
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#1D9E75"));
        textPaint.setTextSize(spToPx(13));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        arcRect = new RectF();
    }

    /**
     * Set adherence data and animate the ring in.
     *
     * @param taken     Number of doses taken this week
     * @param total     Total doses scheduled this week
     */
    public void setAdherence(int taken, int total) {
        percentage = (total > 0) ? Math.round((taken * 100f) / total) : 0;
        targetSweep = (total > 0) ? ((taken * 360f) / total) : 0f;
        animateIn();
    }

    private void animateIn() {
        // Cancel any running animations
        if (sweepAnimator != null) sweepAnimator.cancel();
        if (pctAnimator != null) pctAnimator.cancel();

        // Arc sweep animation
        sweepAnimator = ValueAnimator.ofFloat(0f, targetSweep);
        sweepAnimator.setDuration(900);
        sweepAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        sweepAnimator.addUpdateListener(anim -> {
            currentSweep = (float) anim.getAnimatedValue();
            invalidate();
        });
        sweepAnimator.start();

        // Counter animation
        pctAnimator = ValueAnimator.ofInt(0, percentage);
        pctAnimator.setDuration(900);
        pctAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        pctAnimator.addUpdateListener(anim -> {
            currentDisplayPct = (int) anim.getAnimatedValue();
            invalidate();
        });
        pctAnimator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float strokeWidth = dpToPx(9);
        float halfStroke = strokeWidth / 2f;
        arcRect.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke);

        // Build sweep gradient for the ring
        int cx = w / 2;
        int cy = h / 2;
        SweepGradient sweepGradient = new SweepGradient(cx, cy,
            new int[]{
                Color.parseColor("#1D9E75"),
                Color.parseColor("#0F6E56"),
                Color.parseColor("#1D9E75")
            },
            null
        );
        ringPaint.setShader(sweepGradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (arcRect == null) return;

        // Draw track
        canvas.drawArc(arcRect, -90f, 360f, false, trackPaint);

        // Draw foreground arc
        if (currentSweep > 0f) {
            canvas.drawArc(arcRect, -90f, currentSweep, false, ringPaint);
        }

        // Draw percentage text in center
        float cx = arcRect.centerX();
        float cy = arcRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f);
        canvas.drawText(currentDisplayPct + "%", cx, cy, textPaint);
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
