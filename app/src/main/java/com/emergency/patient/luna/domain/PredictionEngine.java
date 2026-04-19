package com.emergency.patient.luna.domain;

import com.emergency.patient.luna.model.CycleEntry;
import com.emergency.patient.luna.model.CyclePhase;

import java.util.Calendar;
import java.util.List;

/** Stateless engine that computes cycle predictions from historical CycleEntry data. */
public class PredictionEngine {

    public static class Predictions {
        public final int currentDay;
        public final int cycleLength;
        public final CyclePhase currentPhase;
        public final int daysToNextPeriod;
        public final String nextPeriodLabel;
        public final int daysToFertileStart;
        public final String fertileWindowLabel;
        public final String ovulationLabel;
        public final String pmsWindowLabel;
        public final String confidenceLabel;
        public final boolean isEstimated;

        public Predictions(int currentDay, int cycleLength, CyclePhase phase,
                           int daysToNext, String nextLabel,
                           int daysToFertile, String fertileLabel,
                           String ovLabel, String pmsLabel,
                           String confidence, boolean estimated) {
            this.currentDay = currentDay;
            this.cycleLength = cycleLength;
            this.currentPhase = phase;
            this.daysToNextPeriod = daysToNext;
            this.nextPeriodLabel = nextLabel;
            this.daysToFertileStart = daysToFertile;
            this.fertileWindowLabel = fertileLabel;
            this.ovulationLabel = ovLabel;
            this.pmsWindowLabel = pmsLabel;
            this.confidenceLabel = confidence;
            this.isEstimated = estimated;
        }
    }

    public static Predictions predict(List<CycleEntry> cycles) {
        long now = System.currentTimeMillis();
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        stripTime(today);

        // Average cycle length from last 3 cycles
        int avgLength = 28;
        boolean isEstimated = true;
        if (cycles != null && cycles.size() >= 2) {
            isEstimated = cycles.size() < 3;
            long sum = 0;
            int count = 0;
            List<CycleEntry> recent = cycles.subList(0, Math.min(3, cycles.size()));
            for (int i = 0; i < recent.size() - 1; i++) {
                long diff = recent.get(i).startDate - recent.get(i + 1).startDate;
                int days = (int) (diff / 86400000L);
                if (days > 10 && days < 60) { sum += days; count++; }
            }
            if (count > 0) avgLength = (int) (sum / count);
        }

        // Last period start
        long lastStart = (cycles != null && !cycles.isEmpty()) ? cycles.get(0).startDate : now;
        Calendar lastStartCal = Calendar.getInstance();
        lastStartCal.setTimeInMillis(lastStart);
        stripTime(lastStartCal);

        // Current cycle day
        long diffMs = today.getTimeInMillis() - lastStartCal.getTimeInMillis();
        int currentDay = Math.max(1, (int) (diffMs / 86400000L) + 1);
        CyclePhase phase = CyclePhase.fromCycleDay(currentDay, avgLength);

        // Next period
        Calendar nextPeriod = (Calendar) lastStartCal.clone();
        nextPeriod.add(Calendar.DAY_OF_YEAR, avgLength);
        long daysToNext = (nextPeriod.getTimeInMillis() - today.getTimeInMillis()) / 86400000L;

        // Ovulation (cycleLength - 14 days from last start)
        Calendar ovulation = (Calendar) lastStartCal.clone();
        ovulation.add(Calendar.DAY_OF_YEAR, avgLength - 14);

        // Fertile window: ovulation - 4 to ovulation + 2
        Calendar fertileStart = (Calendar) ovulation.clone();
        fertileStart.add(Calendar.DAY_OF_YEAR, -4);
        Calendar fertileEnd = (Calendar) ovulation.clone();
        fertileEnd.add(Calendar.DAY_OF_YEAR, 2);
        long daysToFertile = Math.max(0, (fertileStart.getTimeInMillis() - today.getTimeInMillis()) / 86400000L);

        // PMS window: nextPeriod - 7 to nextPeriod - 1
        Calendar pmsStart = (Calendar) nextPeriod.clone();
        pmsStart.add(Calendar.DAY_OF_YEAR, -7);
        Calendar pmsEnd = (Calendar) nextPeriod.clone();
        pmsEnd.add(Calendar.DAY_OF_YEAR, -1);

        String confidence = cycles != null && cycles.size() >= 3 ? "Based on your data" : "Estimated";

        return new Predictions(
            currentDay,
            avgLength,
            phase,
            (int) Math.max(0, daysToNext),
            formatDateShort(nextPeriod),
            (int) daysToFertile,
            formatDateShort(fertileStart) + " – " + formatDateShort(fertileEnd),
            formatDateShort(ovulation),
            formatDateShort(pmsStart) + " – " + formatDateShort(pmsEnd),
            confidence,
            isEstimated
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static void stripTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private static final String[] MONTHS =
        {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};

    private static String formatDateShort(Calendar cal) {
        return MONTHS[cal.get(Calendar.MONTH)] + " " + cal.get(Calendar.DAY_OF_MONTH);
    }
}
