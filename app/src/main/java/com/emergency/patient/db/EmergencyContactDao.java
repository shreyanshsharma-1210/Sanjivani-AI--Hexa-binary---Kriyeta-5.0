package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EmergencyContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(EmergencyContactEntity contact);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<EmergencyContactEntity> contacts);

    @androidx.room.Delete
    void delete(EmergencyContactEntity contact);

    @Query("SELECT * FROM emergency_contacts WHERE patientUuid = :patientUuid")
    List<EmergencyContactEntity> getContactsForPatient(String patientUuid);

    @Query("DELETE FROM emergency_contacts WHERE patientUuid = :patientUuid")
    void deleteContactsForPatient(String patientUuid);

    @Query("DELETE FROM emergency_contacts")
    void deleteAllContacts();

    @Query("SELECT * FROM emergency_contacts")
    List<EmergencyContactEntity> getAllContacts();
}
