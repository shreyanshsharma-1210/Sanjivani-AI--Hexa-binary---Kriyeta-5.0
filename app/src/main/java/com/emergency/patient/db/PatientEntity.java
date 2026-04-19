package com.emergency.patient.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "patients")
public class PatientEntity {
    @PrimaryKey
    @NonNull
    public String uuid;

    public String fullName;
    public long dobMillis;
    public String gender;
    public String bloodGroup;
    public String profilePhotoUri;

    // Medical History fields (formerly Triage)
    public String pastMedicalDiagnosis;
    public String pharmacologicalStatus;
    public String clinicalAllergies;
    public String hereditaryConditions;
    public String lifestyleFactor;

    public boolean isOnboardingComplete;

    public PatientEntity(@NonNull String uuid) {
        this.uuid = uuid;
    }
}
