package com.emergency.patient.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "medication_schedule")
public class MedicationScheduleEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String drugName;
    public long timeMillis;
    public String status; // PENDING, TAKEN, SKIPPED

    public MedicationScheduleEntity(String drugName, long timeMillis, String status) {
        this.drugName = drugName;
        this.timeMillis = timeMillis;
        this.status = status;
    }
}
