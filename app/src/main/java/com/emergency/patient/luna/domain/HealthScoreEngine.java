package com.emergency.patient.luna.domain;

import com.emergency.patient.luna.model.Contradiction;
import com.emergency.patient.luna.model.ContraSeverity;

import java.util.List;

/** Computes a composite 0–100 health score from cycle, vitals, symptoms, and medication data. */
public class HealthScoreEngine {

    public static class HealthScore {
        public final int total;
        public final int cycleScore;
        public final int vitalsScore;
        public final int symptomsScore;
        public final int medScore;
        public final String label;
        public final int trend; // positive = improving

        public HealthScore(int total, int cycleScore, int vitalsScore,
                           int symptomsScore, int medScore, String label, int trend) {
            this.total = total;
            this.cycleScore = cycleScore;
            this.vitalsScore = vitalsScore;
            this.symptomsScore = symptomsScore;
            this.medScore = medScore;
            this.label = label;
            this.trend = trend;
        }
    }

    /**
     * @param cycleRegular     true if last 3 cycles within normal length range
     * @param cycleEstimated   true if fewer than 2 cycles logged (less data = less confidence)
     * @param symptomsIntensity highest symptom intensity logged today (1–10, 0 if none)
     * @param contradictions   list of active contradictions from ContraDatabase
     */
    public static HealthScore calculate(boolean cycleRegular, boolean cycleEstimated,
                                        int symptomsIntensity,
                                        List<Contradiction> contradictions) {
        // ── Cycle score (0–30) ────────────────────────────────────────────────
        int cycleScore = 30;
        if (!cycleRegular) cycleScore -= 12;
        if (cycleEstimated) cycleScore -= 5;

        // ── Vitals score (0–25) — n/a until vitals input exists, default high ─
        int vitalsScore = 22;

        // ── Symptom score (0–20) — lower intensity = better ──────────────────
        int symptomsScore = 20;
        if (symptomsIntensity >= 8) symptomsScore -= 16;
        else if (symptomsIntensity >= 6) symptomsScore -= 10;
        else if (symptomsIntensity >= 4) symptomsScore -= 5;
        else if (symptomsIntensity >= 2) symptomsScore -= 2;

        // ── Med score (0–25) — penalise by contradiction severity ─────────────
        int critCount = 0, modCount = 0, infoCount = 0;
        if (contradictions != null) {
            for (Contradiction c : contradictions) {
                if (c.severity == ContraSeverity.CRITICAL) critCount++;
                else if (c.severity == ContraSeverity.MODERATE) modCount++;
                else infoCount++;
            }
        }
        int medScore = Math.max(0, 25 - (critCount * 12) - (modCount * 5) - (infoCount * 2));

        int total = Math.min(100, Math.max(0, cycleScore + vitalsScore + symptomsScore + medScore));
        String label;
        if (total >= 80) label = "Excellent";
        else if (total >= 65) label = "Good";
        else if (total >= 50) label = "Fair";
        else if (total >= 35) label = "Needs Attention";
        else label = "Review Recommended";

        return new HealthScore(total, cycleScore, vitalsScore, symptomsScore, medScore, label, +2);
    }
}
