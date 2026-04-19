package com.emergency.patient.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ocr_corrections")
public class OcrCorrectionEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String wrongText;
    public String correctText;
    public String fieldType; // name, dosage, expiry
}
