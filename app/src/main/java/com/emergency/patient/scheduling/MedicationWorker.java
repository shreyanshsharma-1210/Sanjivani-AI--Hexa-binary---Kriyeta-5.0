package com.emergency.patient.scheduling;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class MedicationWorker extends Worker {

    public MedicationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String drugName = getInputData().getString("drug");
        int scheduleId = getInputData().getInt("scheduleId", -1);

        if (drugName != null && scheduleId != -1) {
            com.emergency.patient.db.AppDatabaseProvider.getInstance(getApplicationContext())
                .scheduleDao().updateStatus(scheduleId, "PENDING");
            NotificationHelper.showNotification(getApplicationContext(), drugName, scheduleId);
        }

        return Result.success();
    }
}
