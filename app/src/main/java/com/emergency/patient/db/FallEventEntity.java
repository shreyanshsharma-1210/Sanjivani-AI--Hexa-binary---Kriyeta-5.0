package com.emergency.patient.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * FallEventEntity — Black Box log for fall detection events.
 *
 * Each row is written when the fall detection state machine reaches the
 * QUIESCENCE_CHECK phase. The outcome field records whether the user
 * cancelled (FALSE_POSITIVE) or the alert was dispatched (ALERT_DISPATCHED).
 *
 * This data is critical for threshold calibration. Analyse false positives
 * across users at different sensitivity presets to tune gForceThreshold.
 */
@Entity(tableName = "fall_events")
public class FallEventEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Unix timestamp (ms) when the free-fall phase began. */
    public long timestamp;

    /** Peak G-force magnitude recorded at impact (in m/s²). */
    public float peakGForce;

    /** Duration of the free-fall phase in milliseconds. */
    public long freeFallDurationMs;

    /** Post-impact acceleration variance σ² confirming quiescence. */
    public float postImpactVariance;

    /** Tilt angle (degrees) of device post-impact (≈90 = phone lying flat). */
    public float postImpactTiltAngle;

    /** User activity when fall began (e.g., WALKING, STILL). */
    public String preImpactActivity;

    /** Sensitivity preset active at time of event: HIGH / NORMAL / RUGGED. */
    public String sensitivityPreset;

    /** GPS latitude at dispatch time. 0.0 if unavailable. */
    public double gpsLat;

    /** GPS longitude at dispatch time. 0.0 if unavailable. */
    public double gpsLng;

    /** Outcome: FALSE_POSITIVE (user cancelled) or ALERT_DISPATCHED. */
    public String outcome;
}
