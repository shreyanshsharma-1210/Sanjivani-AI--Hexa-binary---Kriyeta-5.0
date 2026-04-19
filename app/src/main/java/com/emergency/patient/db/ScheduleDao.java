package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ScheduleDao {

    @Insert
    long insert(MedicationScheduleEntity entity);

    @Query("UPDATE medication_schedule SET status = :status WHERE id = :id")
    void updateStatus(int id, String status);

    @Query("SELECT * FROM medication_schedule")
    List<MedicationScheduleEntity> getAll();

    @androidx.room.Update
    void update(MedicationScheduleEntity entity);

    @Query("DELETE FROM medication_schedule WHERE drugName = :drugName")
    void deleteByDrugName(String drugName);
}
