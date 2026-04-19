package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HealthDocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertDocument(HealthDocumentEntity document);

    @Query("SELECT * FROM health_documents WHERE id = :id")
    HealthDocumentEntity getDocumentById(int id);

    @Query("SELECT * FROM health_documents WHERE patientUuid = :patientUuid ORDER BY uploadTimestamp DESC")
    List<HealthDocumentEntity> getDocumentsForPatient(String patientUuid);

    @Query("UPDATE health_documents SET extractionStatus = :status, extractedJson = :json WHERE id = :id")
    void updateExtractionResults(int id, String status, String json);

    @Query("UPDATE health_documents SET fullText = :text WHERE id = :id")
    void updateFullText(int id, String text);

    @Query("DELETE FROM health_documents WHERE id = :id")
    void deleteDocument(int id);

    @Query("DELETE FROM health_documents")
    void deleteAllDocuments();
}
