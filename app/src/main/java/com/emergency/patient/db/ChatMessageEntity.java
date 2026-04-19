package com.emergency.patient.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "chat_messages",
    indices = {@Index("patientUuid")}
)
public class ChatMessageEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String patientUuid;
    public String sender; // "You" or "AI"
    public String content;
    public long timestamp;

    public ChatMessageEntity(String patientUuid, String sender, String content) {
        this.patientUuid = patientUuid;
        this.sender = sender;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }
}
