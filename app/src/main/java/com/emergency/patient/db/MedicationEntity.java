package com.emergency.patient.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * MedicationEntity — Room entity for a single medication record.
 *
 * Populated by MedicationOcrActivity after the patient has reviewed and
 * approved OCR-extracted data. Linked to the patient via patientUuid so
 * that data can be queried per-profile.
 *
 * Table: medications
 */
@Entity(
    tableName = "medications",
    indices = {@Index("patientUuid")}
)
public class MedicationEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** FK — patient this medication belongs to (from TokenManager.getUUID). */
    @NonNull
    public String patientUuid = "";

    /** Brand name of the medication, e.g. "HKVitals", "Tylenol". */
    public String brandName;

    /** Drug / product name, e.g. "Paracetamol", "Amoxicillin 500". */
    public String name;

    /** Dosage string, e.g. "500 mg", "250mg/5ml". */
    public String dosage;

    /** Expiry as a display string, e.g. "06/27", "15/06/2027". */
    public String expiry;

    /**
     * Full raw OCR text captured from the label image.
     * Kept for audit / re-processing purposes.
     */
    public String rawOcrText;

    /** Source of final text: "mlkit" or "tesseract". */
    public String ocrSource;

    /** Path to locally saved image */
    public String imageUri;

    /** Unix timestamp (ms) when this record was created. */
    public long createdAt;

    public MedicationEntity() {
        this.createdAt = System.currentTimeMillis();
    }
}
