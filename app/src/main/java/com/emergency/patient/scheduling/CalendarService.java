package com.emergency.patient.scheduling;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * CalendarService - Handles integration with Android System Calendar.
 */
public class CalendarService {

    private static final String TAG = "CalendarService";

    public static void addMedicationToCalendar(Context context, String drugName, ScheduleData schedule) {
        try {
            long calendarId = getPrimaryCalendarId(context);
            if (calendarId == -1) {
                Log.e(TAG, "No calendar found to add events.");
                return;
            }

            for (String time : schedule.times) {
                String[] parts = time.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                Calendar beginTime = Calendar.getInstance();
                beginTime.set(Calendar.HOUR_OF_DAY, hour);
                beginTime.set(Calendar.MINUTE, minute);
                beginTime.set(Calendar.SECOND, 0);

                // If time already passed today, start from tomorrow
                if (beginTime.getTimeInMillis() < System.currentTimeMillis()) {
                    beginTime.add(Calendar.DAY_OF_YEAR, 1);
                }

                ContentResolver cr = context.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(CalendarContract.Events.DTSTART, beginTime.getTimeInMillis());
                values.put(CalendarContract.Events.DTEND, beginTime.getTimeInMillis() + (15 * 60 * 1000)); // 15 min duration
                values.put(CalendarContract.Events.TITLE, "Take " + drugName);
                values.put(CalendarContract.Events.DESCRIPTION, "Medication Reminder: " + schedule.quantity + " " + (schedule.instructions != null ? schedule.instructions : ""));
                values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
                values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

                // Recurrence Rule (RRule)
                if ("WEEKLY".equals(schedule.frequency)) {
                    values.put(CalendarContract.Events.RRULE, "FREQ=WEEKLY;BYDAY=" + getDayOfWeekString(beginTime));
                } else {
                    values.put(CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=30"); // Repeat for 30 days
                }

                values.put(CalendarContract.Events.HAS_ALARM, 1);

                Uri uri = cr.insert(CalendarContract.Events.CONTENT_URI, values);
                if (uri != null) {
                    long eventID = Long.parseLong(uri.getLastPathSegment());
                    addReminder(cr, eventID);
                    Log.d(TAG, "Added calendar event: " + drugName + " at " + time);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar permission not granted", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add calendar event", e);
        }
    }

    private static void addReminder(ContentResolver cr, long eventID) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Reminders.MINUTES, 5); // 5 minutes before
        values.put(CalendarContract.Reminders.EVENT_ID, eventID);
        values.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
        cr.insert(CalendarContract.Reminders.CONTENT_URI, values);
    }

    private static long getPrimaryCalendarId(Context context) {
        String[] projection = new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY};
        Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null, null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                int isPrimary = cursor.getInt(1);
                if (isPrimary == 1) {
                    cursor.close();
                    return id;
                }
            }
            cursor.close();
        }
        return -1;
    }

    private static String getDayOfWeekString(Calendar cal) {
        int day = cal.get(Calendar.DAY_OF_WEEK);
        switch (day) {
            case Calendar.SUNDAY: return "SU";
            case Calendar.MONDAY: return "MO";
            case Calendar.TUESDAY: return "TU";
            case Calendar.WEDNESDAY: return "WE";
            case Calendar.THURSDAY: return "TH";
            case Calendar.FRIDAY: return "FR";
            case Calendar.SATURDAY: return "SA";
            default: return "MO";
        }
    }
}
