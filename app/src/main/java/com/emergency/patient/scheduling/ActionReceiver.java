package com.emergency.patient.scheduling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationManagerCompat;
import com.emergency.patient.db.AppDatabaseProvider;

public class ActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getStringExtra("action");
        int scheduleId = intent.getIntExtra("scheduleId", -1);

        if (scheduleId != -1 && action != null) {
            new Thread(() -> {
                if ("TAKEN".equals(action)) {
                    AppDatabaseProvider.getInstance(context).scheduleDao().updateStatus(scheduleId, "TAKEN");
                } else if ("SKIPPED".equals(action)) {
                    AppDatabaseProvider.getInstance(context).scheduleDao().updateStatus(scheduleId, "SKIPPED");
                }
            }).start();
            
            // clear the notification
            NotificationManagerCompat.from(context).cancel(scheduleId);
        }
    }
}
