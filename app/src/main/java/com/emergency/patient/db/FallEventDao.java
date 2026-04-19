package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * FallEventDao — Room DAO for the fall detection black box log.
 */
@Dao
public interface FallEventDao {

    @Insert
    void insert(FallEventEntity event);

    /** Returns the N most recent events — used by UX to show event history. */
    @Query("SELECT * FROM fall_events ORDER BY timestamp DESC LIMIT :limit")
    List<FallEventEntity> getRecentEvents(int limit);

    /** Returns false positive count — used for threshold calibration analytics. */
    @Query("SELECT COUNT(*) FROM fall_events WHERE outcome = 'FALSE_POSITIVE'")
    int countFalsePositives();

    /** Returns dispatched alert count — key engagement metric. */
    @Query("SELECT COUNT(*) FROM fall_events WHERE outcome = 'ALERT_DISPATCHED'")
    int countDispatched();
}
