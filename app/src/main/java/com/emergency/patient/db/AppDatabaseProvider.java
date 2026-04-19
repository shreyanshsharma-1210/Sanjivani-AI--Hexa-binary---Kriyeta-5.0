package com.emergency.patient.db;

import android.content.Context;

import androidx.room.Room;

public class AppDatabaseProvider {
    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    "emergency_patient_db"
            )
            .addMigrations(AppDatabase.MIGRATION_16_17)
            .fallbackToDestructiveMigration() // Safety net during development
            .allowMainThreadQueries()
            .build();
        }
        return instance;
    }
}
