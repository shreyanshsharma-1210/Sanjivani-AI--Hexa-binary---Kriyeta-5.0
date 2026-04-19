package com.emergency.patient.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.emergency.patient.luna.db.CycleDao;
import com.emergency.patient.luna.db.LunaTypeConverters;
import com.emergency.patient.luna.db.SymptomDao;
import com.emergency.patient.luna.model.CycleEntry;
import com.emergency.patient.luna.model.SymptomEntry;

@Database(
    entities = {
        PatientEntity.class,
        EmergencyContactEntity.class,
        HealthDocumentEntity.class,
        HealthDocumentChunkEntity.class,
        ChatMessageEntity.class,
        MedicationEntity.class,         // ← OCR medication records
        MedicationScheduleEntity.class, // ← Scheduling tracking
        OcrCorrectionEntity.class,      // ← OCR self-improvement corrections
        FallEventEntity.class,          // ← Fall detection black box log
        CycleEntry.class,               // ← Luna: menstrual cycle entries
        SymptomEntry.class              // ← Luna: daily symptom logs
    },
    version = 17,
    exportSchema = false
)
@TypeConverters({LunaTypeConverters.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract PatientDao patientDao();
    public abstract EmergencyContactDao emergencyContactDao();
    public abstract HealthDocumentDao healthDocumentDao();
    public abstract HealthDocumentChunkDao healthDocumentChunkDao();
    public abstract ChatMessageDao chatMessageDao();
    public abstract MedicationDao medicationDao();
    public abstract ScheduleDao scheduleDao();
    public abstract OcrCorrectionDao ocrCorrectionDao();
    public abstract FallEventDao fallEventDao();

    // ── Luna DAOs ──────────────────────────────────────────────────────────
    public abstract CycleDao cycleDao();
    public abstract SymptomDao symptomDao();

    // ── Migration 16 → 17: Add Luna tables ────────────────────────────────
    public static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS luna_cycle_entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "startDate INTEGER NOT NULL, " +
                "length INTEGER NOT NULL, " +
                "notes TEXT)"
            );
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS luna_symptom_entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "date INTEGER NOT NULL, " +
                "symptomsJson TEXT, " +
                "intensity INTEGER NOT NULL, " +
                "cycleDay INTEGER NOT NULL, " +
                "phase TEXT, " +
                "notes TEXT)"
            );
        }
    };
}
