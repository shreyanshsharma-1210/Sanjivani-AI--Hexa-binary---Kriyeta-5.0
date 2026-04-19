package com.emergency.patient.scheduling;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.MedicationEntity;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class MedicationExpiryWorker extends Worker {

    public MedicationExpiryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        List<MedicationEntity> medications = AppDatabaseProvider.getInstance(getApplicationContext())
                .medicationDao().getAllMedications();

        long now = System.currentTimeMillis();
        long sevenDaysInMs = TimeUnit.DAYS.toMillis(7);

        for (MedicationEntity med : medications) {
            if (med.expiry == null || med.expiry.isEmpty()) continue;

            long expiryTimestamp = ExpiryDateParser.parseExpiry(med.expiry);
            if (expiryTimestamp == -1) continue;

            long diffMs = expiryTimestamp - now;
            long diffDays = TimeUnit.MILLISECONDS.toDays(diffMs);

            // Alert for 7 days before and every day after until expiry
            if (diffDays >= 0 && diffDays <= 7) {
                NotificationHelper.showExpiryNotification(getApplicationContext(), med.name, (int) diffDays);
            }
        }

        return Result.success();
    }
}
