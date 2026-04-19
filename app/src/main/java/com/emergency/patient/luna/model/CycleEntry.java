package com.emergency.patient.luna.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "luna_cycle_entries")
public class CycleEntry {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Epoch ms of the first day of this period */
    public long startDate;

    /** Observed cycle length in days (0 = unknown until next period starts) */
    public int length;

    /** Optional free-text notes */
    public String notes;

    public CycleEntry() {}

    public CycleEntry(long startDate, int length) {
        this.startDate = startDate;
        this.length = length;
    }
}
