package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface OcrCorrectionDao {
    @Insert
    void insert(OcrCorrectionEntity correction);

    @Query("SELECT * FROM ocr_corrections")
    List<OcrCorrectionEntity> getAll();
}
