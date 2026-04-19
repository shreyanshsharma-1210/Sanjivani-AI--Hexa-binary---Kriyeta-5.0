package com.emergency.patient.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PatientProfile implements Serializable {

    // Demographic Data (Step 1)
    private String fullName;
    private long dobMillis;
    private String gender;
    private String bloodGroup;
    private String profilePhotoUri;

    // Medical History Quiz Data (Step 2 - formerly Triage)
    // Each field stores the chosen option label, e.g. "A", "B", or "Other: <text>"
    private String pastMedicalDiagnosis;
    private String pharmacologicalStatus;
    private String clinicalAllergies;
    private String hereditaryConditions;
    private String lifestyleFactor;

    // Emergency Contacts (Step 1b)
    private List<EmergencyContact> emergencyContacts = new ArrayList<>();

    public static class EmergencyContact implements Serializable {
        public String name;
        public String phoneNumber;

        public EmergencyContact(String name, String phoneNumber) {
            this.name = name;
            this.phoneNumber = phoneNumber;
        }
    }

    public PatientProfile() {
    }

    // Getters and Setters for Emergency Contacts
    public List<EmergencyContact> getEmergencyContacts() { return emergencyContacts; }
    public void setEmergencyContacts(List<EmergencyContact> contacts) { this.emergencyContacts = contacts; }
    public void addEmergencyContact(String name, String phone) {
        if (emergencyContacts.size() < 3) {
            emergencyContacts.add(new EmergencyContact(name, phone));
        }
    }

    // Demographics
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public long getDobMillis() { return dobMillis; }
    public void setDobMillis(long dobMillis) { this.dobMillis = dobMillis; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getProfilePhotoUri() { return profilePhotoUri; }
    public void setProfilePhotoUri(String profilePhotoUri) { this.profilePhotoUri = profilePhotoUri; }

    // Medical History
    public String getPastMedicalDiagnosis() { return pastMedicalDiagnosis; }
    public void setPastMedicalDiagnosis(String pastMedicalDiagnosis) { this.pastMedicalDiagnosis = pastMedicalDiagnosis; }

    public String getPharmacologicalStatus() { return pharmacologicalStatus; }
    public void setPharmacologicalStatus(String pharmacologicalStatus) { this.pharmacologicalStatus = pharmacologicalStatus; }

    public String getClinicalAllergies() { return clinicalAllergies; }
    public void setClinicalAllergies(String clinicalAllergies) { this.clinicalAllergies = clinicalAllergies; }

    public String getHereditaryConditions() { return hereditaryConditions; }
    public void setHereditaryConditions(String hereditaryConditions) { this.hereditaryConditions = hereditaryConditions; }

    public String getLifestyleFactor() { return lifestyleFactor; }
    public void setLifestyleFactor(String lifestyleFactor) { this.lifestyleFactor = lifestyleFactor; }

    // Utility: flat list for display
    public List<String> getActiveConditionsList() {
        List<String> conditions = new ArrayList<>();
        if (pastMedicalDiagnosis != null && !pastMedicalDiagnosis.isEmpty())
            conditions.add("Past Diagnosis: " + pastMedicalDiagnosis);

        if (clinicalAllergies != null && !clinicalAllergies.isEmpty())
            conditions.add("Allergies: " + clinicalAllergies);
        if (hereditaryConditions != null && !hereditaryConditions.isEmpty())
            conditions.add("Family History: " + hereditaryConditions);
        if (lifestyleFactor != null && !lifestyleFactor.isEmpty())
            conditions.add("Lifestyle: " + lifestyleFactor);
        return conditions;
    }
}
