package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PatientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPatient(PatientEntity patient);

    @Query("SELECT * FROM patients WHERE uuid = :uuid LIMIT 1")
    PatientEntity getPatient(String uuid);

    @Query("SELECT * FROM patients LIMIT 1")
    PatientEntity getFirstPatient();

    @Query("UPDATE patients SET isOnboardingComplete = :complete WHERE uuid = :uuid")
    void setOnboardingComplete(String uuid, boolean complete);

    @Query("DELETE FROM patients")
    void deleteAllPatients();
}
