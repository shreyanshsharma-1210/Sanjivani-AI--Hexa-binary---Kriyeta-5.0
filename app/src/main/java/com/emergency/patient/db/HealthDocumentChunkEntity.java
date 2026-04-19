package com.emergency.patient.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "health_document_chunks",
    foreignKeys = @ForeignKey(
        entity = HealthDocumentEntity.class,
        parentColumns = "id",
        childColumns = "documentId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("documentId")}
)
public class HealthDocumentChunkEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int documentId;
    public String patientUuid;
    public String content;
    public int chunkIndex;

    public HealthDocumentChunkEntity(int documentId, String patientUuid, String content, int chunkIndex) {
        this.documentId = documentId;
        this.patientUuid = patientUuid;
        this.content = content;
        this.chunkIndex = chunkIndex;
    }
}
