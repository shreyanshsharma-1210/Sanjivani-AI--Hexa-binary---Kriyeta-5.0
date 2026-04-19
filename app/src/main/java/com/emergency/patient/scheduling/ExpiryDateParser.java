package com.emergency.patient.scheduling;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpiryDateParser {

    /**
     * Parses medication expiry strings into a Unix timestamp (ms).
     * Supported formats: 
     * - MM/YY or MM/YYYY (e.g., 06/25) -> Last day of the month
     * - DD/MM/YYYY (e.g., 15/06/2026) -> Exact day
     * - Month YYYY (e.g., JUN 2026) -> Last day of the month
     * 
     * @return timestamp in ms, or -1 if unparseable
     */
    public static long parseExpiry(String expiry) {
        if (expiry == null || expiry.trim().isEmpty()) return -1;
        
        String input = expiry.toUpperCase().trim();
        
        try {
            // Pattern 1: DD/MM/YYYY or DD-MM-YYYY
            if (input.matches("\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}")) {
                SimpleDateFormat sdf = new SimpleDateFormat(input.contains("/") ? "dd/MM/yyyy" : "dd-MM-yyyy", Locale.getDefault());
                if (input.split("[/\\-]")[2].length() == 2) {
                    sdf = new SimpleDateFormat(input.contains("/") ? "dd/MM/yy" : "dd-MM-yy", Locale.getDefault());
                }
                return sdf.parse(input).getTime();
            }

            // Pattern 2: MM/YY or MM/YYYY
            if (input.matches("\\d{1,2}[/\\-]\\d{2,4}")) {
                String[] parts = input.split("[/\\-]");
                int month = Integer.parseInt(parts[0]);
                int year = Integer.parseInt(parts[1]);
                if (parts[1].length() == 2) year += 2000;
                
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.YEAR, year);
                cal.set(Calendar.MONTH, month - 1);
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                return cal.getTimeInMillis();
            }

            // Pattern 3: Month YYYY (e.g. JUN 2026)
            Pattern monthPattern = Pattern.compile("([A-Z]{3})\\s*(\\d{2,4})");
            Matcher m = monthPattern.matcher(input);
            if (m.find()) {
                String monStr = m.group(1);
                int year = Integer.parseInt(m.group(2));
                if (m.group(2).length() == 2) year += 2000;

                int month = getMonthIndex(monStr);
                if (month != -1) {
                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.YEAR, year);
                    cal.set(Calendar.MONTH, month);
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                    return cal.getTimeInMillis();
                }
            }

        } catch (Exception e) {
            return -1;
        }

        return -1;
    }

    private static int getMonthIndex(String mon) {
        String[] months = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        for (int i = 0; i < months.length; i++) {
            if (mon.startsWith(months[i])) return i;
        }
        return -1;
    }
}
