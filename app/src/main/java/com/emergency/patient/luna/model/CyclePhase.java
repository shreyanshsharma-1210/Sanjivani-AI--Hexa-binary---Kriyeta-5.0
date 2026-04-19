package com.emergency.patient.luna.model;

public enum CyclePhase {
    MENSTRUAL("Menstrual"),
    FOLLICULAR("Follicular"),
    OVULATION("Ovulation"),
    LUTEAL("Luteal");

    public final String label;

    CyclePhase(String label) {
        this.label = label;
    }

    /** Returns the phase for a given cycle day (assuming standard 28-day cycle). */
    public static CyclePhase fromCycleDay(int day, int cycleLength) {
        if (day <= 0) return MENSTRUAL;
        if (day <= 5) return MENSTRUAL;
        // Ovulation window: cycleLength - 14 ± 1
        int ovDay = cycleLength - 14;
        if (day >= ovDay - 1 && day <= ovDay + 1) return OVULATION;
        if (day < ovDay - 1) return FOLLICULAR;
        return LUTEAL;
    }
}
