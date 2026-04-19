package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HealthDocumentChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertChunks(List<HealthDocumentChunkEntity> chunks);

    @Query("SELECT * FROM health_document_chunks WHERE patientUuid = :patientUuid")
    List<HealthDocumentChunkEntity> getChunksForPatient(String patientUuid);

    @Query("DELETE FROM health_document_chunks WHERE documentId = :documentId")
    void deleteChunksForDocument(int documentId);
}
