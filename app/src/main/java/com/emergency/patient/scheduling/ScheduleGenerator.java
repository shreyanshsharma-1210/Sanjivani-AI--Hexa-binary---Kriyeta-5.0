package com.emergency.patient.scheduling;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ScheduleGenerator {

    /**
     * Generates a list of time slots (in milliseconds) for today based on frequency.
     * Note: In a production system, this would be expanded to handle multi-day scheduling.
     */
    public static List<Long> generateTimes(int frequency) {
        List<Long> times = new ArrayList<>();
        Calendar cal = Calendar.getInstance();

        if (frequency == 1) {
            cal.set(Calendar.HOUR_OF_DAY, 9);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            times.add(cal.getTimeInMillis());
        } else if (frequency == 2) {
            cal.set(Calendar.HOUR_OF_DAY, 9);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            times.add(cal.getTimeInMillis());

            cal.set(Calendar.HOUR_OF_DAY, 21);
            times.add(cal.getTimeInMillis());
        } else if (frequency == 3) {
            int[] hours = {8, 14, 20};
            for (int h : hours) {
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                times.add(cal.getTimeInMillis());
            }
        } else if (frequency == 4) {
            int[] hours = {6, 12, 18, 22};
            for (int h : hours) {
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                times.add(cal.getTimeInMillis());
            }
        }

        return times;
    }
}
