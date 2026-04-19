package com.emergency.patient.luna.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Stores a daily symptom log entry.
 * The `symptoms` field is stored as a JSON array string via LunaTypeConverters.
 */
@Entity(tableName = "luna_symptom_entries")
public class SymptomEntry {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Epoch ms (midnight of the day) */
    public long date;

    /**
     * JSON-serialized list of symptom names, e.g. ["CRAMPS","FATIGUE"]
     * Converted by LunaTypeConverters.
     */
    public String symptomsJson;

    /** 1–10 intensity */
    public int intensity;

    /** Day of cycle when logged */
    public int cycleDay;

    /** Phase label at time of logging */
    public String phase;

    /** Optional free-text notes */
    public String notes;

    public SymptomEntry() {}
}
