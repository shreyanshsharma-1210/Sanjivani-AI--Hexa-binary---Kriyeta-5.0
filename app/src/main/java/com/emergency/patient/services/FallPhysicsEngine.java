package com.emergency.patient.services;

/**
 * FallPhysicsEngine — Pure DSP math utilities for fall detection.
 *
 * No Android dependencies. All methods are static and unit-testable.
 *
 * Mathematics Reference:
 *   • Magnitude:  √(x² + y² + z²)
 *   • Low-Pass:   G_t = α·G_(t-1) + (1-α)·Raw_t
 *   • Linear Acc: Raw - Gravity
 *   • Tilt:       arccos(g_z / |g|)  → degrees
 *   • Variance:   σ² = (1/N) Σ(x_i - μ)²
 */
public class FallPhysicsEngine {

    // ─── Sensitivity Presets ──────────────────────────────────────────────────

    /** Hyper-Sensitive: triggers on very light jerks (~1.5g). */
    public static final float THRESHOLD_HIGH_SENSITIVITY = 14.7f;  // m/s² ≈ 1.5g

    /** Hyper-Sensitive: triggers on very light jerks (~1.5g). */
    public static final float THRESHOLD_NORMAL = 14.7f;            // m/s² ≈ 1.5g

    /** Active users: requires moderate movement (~2.5g). */
    public static final float THRESHOLD_RUGGED = 24.5f;            // m/s² ≈ 2.5g

    /** High threshold (~0.85g) means almost any downward movement starts the detection. */
    public static final float FREE_FALL_THRESHOLD = 8.3f;          // m/s² ≈ 0.85g

    /** Post-impact variance: significantly relaxed to caught even shaky movement. */
    public static final float QUIESCENCE_VARIANCE_THRESHOLD = 0.45f;

    /** Near-instant trigger: only requires 20ms of downward acceleration. */
    public static final long MIN_FREE_FALL_MS = 20;

    /** Window for impact detection after free-fall ends (milliseconds). */
    public static final long IMPACT_WINDOW_MS = 500;

    /** Post-impact monitoring duration before dispatching alert (milliseconds). */
    public static final long QUIESCENCE_WINDOW_MS = 10_000;

    /** Low-pass filter alpha: 0.8 = smooth gravity, 0.2 = fast response. */
    public static final float ALPHA = 0.8f;

    /** Tilt angle threshold: above this means device is lying flat (degrees). */
    public static final float LYING_FLAT_ANGLE_DEG = 60f;

    // ─── DSP Methods ──────────────────────────────────────────────────────────

    /**
     * Euclidean norm of the 3-axis acceleration vector.
     * In resting state ≈ 9.8 m/s² (1g). During free-fall → 0. Impact → spike.
     *
     * @param x raw accelerometer x (m/s²)
     * @param y raw accelerometer y (m/s²)
     * @param z raw accelerometer z (m/s²)
     * @return total magnitude in m/s²
     */
    public static float magnitude(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Applies a low-pass filter in-place to isolate the gravity component.
     * Call repeatedly with each sensor sample to track slowly-varying gravity.
     *
     * @param gravity current gravity estimate (modified in-place)
     * @param raw     latest raw accelerometer reading [x, y, z]
     * @param alpha   smoothing factor (typically ALPHA = 0.8)
     */
    public static void applyLowPassFilter(float[] gravity, float[] raw, float alpha) {
        gravity[0] = alpha * gravity[0] + (1 - alpha) * raw[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * raw[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * raw[2];
    }

    /**
     * Computes the linear (non-gravitational) acceleration by subtracting
     * the smoothed gravity from the raw sensor reading.
     *
     * @param raw     raw accelerometer [x, y, z]
     * @param gravity smoothed gravity estimate [x, y, z]
     * @return linear acceleration [x, y, z]
     */
    public static float[] linearAcceleration(float[] raw, float[] gravity) {
        return new float[]{
            raw[0] - gravity[0],
            raw[1] - gravity[1],
            raw[2] - gravity[2]
        };
    }

    /**
     * Calculates the device tilt angle relative to vertical (Z-axis vs gravity).
     * 0° = fully upright; 90° = device lying flat on a surface.
     *
     * Logic: θ = arccos(g_z / |g|)
     *
     * @param gravity smoothed gravity vector [x, y, z]
     * @return tilt angle in degrees (0–180)
     */
    public static float tiltAngleDegrees(float[] gravity) {
        float gMag = magnitude(gravity[0], gravity[1], gravity[2]);
        if (gMag < 0.01f) return 0f; // Avoid divide-by-zero during free-fall
        double radians = Math.acos(Math.max(-1.0, Math.min(1.0, gravity[2] / gMag)));
        return (float) Math.toDegrees(radians);
    }

    /**
     * Calculates the variance σ² of the last N acceleration magnitudes.
     * Low variance (< QUIESCENCE_VARIANCE_THRESHOLD) indicates the phone is
     * completely still — a strong signal of post-fall unconsciousness.
     *
     * @param samples circular buffer of magnitude readings
     * @return variance σ²
     */
    public static float variance(float[] samples) {
        if (samples == null || samples.length == 0) return 0f;
        float sum = 0f;
        for (float s : samples) sum += s;
        float mean = sum / samples.length;

        float varianceSum = 0f;
        for (float s : samples) {
            float diff = s - mean;
            varianceSum += diff * diff;
        }
        return varianceSum / samples.length;
    }

    /**
     * Returns the G-force impact threshold (m/s²) for the given preset name.
     *
     * @param preset "HIGH" | "NORMAL" | "RUGGED"
     * @return threshold in m/s²
     */
    public static float getImpactThreshold(String preset) {
        if (preset == null) return THRESHOLD_NORMAL;
        switch (preset.toUpperCase()) {
            case "HIGH":   return THRESHOLD_HIGH_SENSITIVITY;
            case "RUGGED": return THRESHOLD_RUGGED;
            default:       return THRESHOLD_NORMAL;
        }
    }
}
