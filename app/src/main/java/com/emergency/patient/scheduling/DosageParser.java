package com.emergency.patient.scheduling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DosageParser {

    /**
     * Parses natural language dosage text into structured ScheduleData using deterministic rules.
     */
    public static ScheduleData parse(String dosageText) {
        if (dosageText == null || dosageText.isEmpty()) {
            return new ScheduleData("DAILY", Arrays.asList("09:00"), "1 tablet");
        }

        String text = dosageText.toLowerCase();
        ScheduleData result = new ScheduleData();
        result.times = new ArrayList<>();

        // 1. Frequency Matching
        if (text.contains("thrice") || text.matches(".*3\\s*(times|pills|tablets).*") || text.contains("t.i.d")) {
            result.frequency = "THRICE_DAILY";
            result.times.addAll(Arrays.asList("08:00", "14:00", "20:00"));
        } else if (text.contains("twice") || text.matches(".*2\\s*(times|pills|tablets).*") || text.contains("b.i.d")) {
            result.frequency = "TWICE_DAILY";
            result.times.addAll(Arrays.asList("09:00", "21:00"));
        } else if (text.contains("weekly") || text.contains("once a week")) {
            result.frequency = "WEEKLY";
            result.times.add("09:00");
        } else if (text.contains("every 6 hours") || text.contains("q6h")) {
            result.frequency = "EVERY_6_HOURS";
            result.times.addAll(Arrays.asList("06:00", "12:00", "18:00", "00:00"));
        } else if (text.contains("every 4 hours") || text.contains("q4h")) {
            result.frequency = "EVERY_4_HOURS";
            result.times.addAll(Arrays.asList("08:00", "12:00", "16:00", "20:00", "00:00", "04:00"));
        } else {
            result.frequency = "DAILY";
            result.times.add("09:00"); // Default
        }

        // 2. Timing Adjustments
        if (text.contains("morning")) {
            if (!result.times.isEmpty()) result.times.set(0, "08:00");
        }
        if (text.contains("night") || text.contains("bedtime")) {
            if (result.frequency.equals("DAILY")) {
                result.times.clear();
                result.times.add("21:00");
            }
        }
        if (text.contains("after meal") || text.contains("post meal")) {
            result.instructions = "After meal";
            // Adjust morning/day times if explicitly mentioned, or leave defaults
            if (result.times.contains("09:00")) {
                result.times.remove("09:00");
                result.times.add("13:00"); // Default lunch time for 'after meal' if ambiguous
            }
        }

        // 3. Quantity Extraction (Simple regex)
        Pattern qtyPattern = Pattern.compile("(\\d+)\\s*(tablet|pill|cap|ml|mg|dose)");
        Matcher matcher = qtyPattern.matcher(text);
        if (matcher.find()) {
            result.quantity = matcher.group(1) + " " + matcher.group(2);
        } else {
            result.quantity = "1 tablet"; // Safe default
        }

        return result;
    }

    /**
     * Legacy support for frequency int extraction
     */
    public static int getFrequency(String dosageText) {
        ScheduleData data = parse(dosageText);
        return data.times.size();
    }
}
