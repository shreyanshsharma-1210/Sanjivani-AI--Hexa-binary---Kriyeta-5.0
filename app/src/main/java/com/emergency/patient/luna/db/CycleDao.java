package com.emergency.patient.luna.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.emergency.patient.luna.model.CycleEntry;

import java.util.List;

@Dao
public interface CycleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CycleEntry entry);

    @Query("SELECT * FROM luna_cycle_entries ORDER BY startDate DESC")
    List<CycleEntry> getAll();

    @Query("SELECT * FROM luna_cycle_entries ORDER BY startDate DESC LIMIT :n")
    List<CycleEntry> getRecent(int n);

    @Query("SELECT * FROM luna_cycle_entries ORDER BY startDate DESC LIMIT 1")
    CycleEntry getLatest();

    @Query("DELETE FROM luna_cycle_entries WHERE id = :id")
    void deleteById(long id);
}
