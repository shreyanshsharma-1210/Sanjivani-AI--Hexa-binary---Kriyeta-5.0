package com.emergency.patient.ocr;

public class Interaction {
    public String drugA;
    public String drugB;
    public String severity;
    public String description;
    public String source;

    public Interaction(String a, String b, String s, String d, String src) {
        this.drugA = a;
        this.drugB = b;
        this.severity = s;
        this.description = d;
        this.source = src;
    }
}
