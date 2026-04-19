package com.emergency.patient.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;


@Entity(
    tableName = "emergency_contacts",
    foreignKeys = @ForeignKey(
        entity = PatientEntity.class,
        parentColumns = "uuid",
        childColumns = "patientUuid",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("patientUuid")}
)
public class EmergencyContactEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String patientUuid;
    public String name;
    public String phoneNumber;

    public EmergencyContactEntity(String patientUuid, String name, String phoneNumber) {
        this.patientUuid = patientUuid;
        this.name = name;
        this.phoneNumber = phoneNumber;
    }
}
