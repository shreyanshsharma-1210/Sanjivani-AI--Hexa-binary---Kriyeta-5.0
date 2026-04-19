package com.emergency.patient.luna.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.emergency.patient.luna.model.SymptomEntry;

import java.util.List;

@Dao
public interface SymptomDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SymptomEntry entry);

    @Query("SELECT * FROM luna_symptom_entries ORDER BY date DESC")
    List<SymptomEntry> getAll();

    /** Returns today's entries (pass midnight epoch ms as dayStart and dayStart+86400000-1 as dayEnd) */
    @Query("SELECT * FROM luna_symptom_entries WHERE date >= :dayStart AND date <= :dayEnd ORDER BY date DESC LIMIT 1")
    SymptomEntry getForDay(long dayStart, long dayEnd);

    @Query("SELECT * FROM luna_symptom_entries ORDER BY date DESC LIMIT :n")
    List<SymptomEntry> getRecent(int n);
}
