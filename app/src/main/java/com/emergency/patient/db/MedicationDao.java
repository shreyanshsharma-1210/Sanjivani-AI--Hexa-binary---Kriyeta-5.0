package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * MedicationDao — Data Access Object for the medications table.
 *
 * Used exclusively by MedicationOcrActivity after the patient has
 * approved the OCR-extracted data.
 */
@Dao
public interface MedicationDao {

    /**
     * Insert a new medication record. REPLACE strategy handles any
     * duplicate primary-key edge cases gracefully.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertMedication(MedicationEntity medication);

    /** Retrieve all medications for a specific patient, newest first. */
    @Query("SELECT * FROM medications WHERE patientUuid = :patientUuid ORDER BY createdAt DESC")
    List<MedicationEntity> getMedicationsForPatient(String patientUuid);

    /** Retrieve every medication row (admin / debug use). */
    @Query("SELECT * FROM medications ORDER BY createdAt DESC")
    List<MedicationEntity> getAllMedications();

    /** Delete a specific medication by primary key. */
    @Query("DELETE FROM medications WHERE id = :id")
    void deleteMedication(int id);

    /** Wipe all medication records (logout / profile-reset scenarios). */
    @Query("DELETE FROM medications")
    void deleteAllMedications();

    @androidx.room.Delete
    void delete(MedicationEntity medication);
}
