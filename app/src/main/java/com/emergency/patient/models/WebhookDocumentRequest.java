package com.emergency.patient.models;

import com.google.gson.annotations.SerializedName;

public class WebhookDocumentRequest {
    @SerializedName("patient_uuid")
    public String patientUuid;

    @SerializedName("file_name")
    public String fileName;

    @SerializedName("file_mime_type")
    public String fileMimeType;

    @SerializedName("file_base64")
    public String fileBase64;

    public WebhookDocumentRequest(String patientUuid, String fileName, String fileMimeType, String fileBase64) {
        this.patientUuid = patientUuid;
        this.fileName = fileName;
        this.fileMimeType = fileMimeType;
        this.fileBase64 = fileBase64;
    }
}
