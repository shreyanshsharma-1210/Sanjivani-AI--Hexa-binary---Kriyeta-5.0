package com.emergency.patient.scheduling;

import java.util.List;

/**
 * ScheduleData - Structured medication schedule information.
 */
public class ScheduleData {
    public String frequency; // DAILY, TWICE_DAILY, THRICE_DAILY, WEEKLY, AS_NEEDED
    public List<String> times; // HH:mm format
    public String quantity;
    public String instructions; // e.g., "After meal"
    
    public ScheduleData() {}
    
    public ScheduleData(String frequency, List<String> times, String quantity) {
        this.frequency = frequency;
        this.times = times;
        this.quantity = quantity;
    }
}
