package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ChatMessageDao {
    @Insert
    void insertMessage(ChatMessageEntity message);

    @Query("SELECT * FROM chat_messages WHERE patientUuid = :patientUuid ORDER BY timestamp ASC")
    List<ChatMessageEntity> getMessagesForPatient(String patientUuid);

    @Query("DELETE FROM chat_messages WHERE patientUuid = :patientUuid")
    void deleteMessagesForPatient(String patientUuid);

    @Query("DELETE FROM chat_messages")
    void deleteAllMessages();
}
