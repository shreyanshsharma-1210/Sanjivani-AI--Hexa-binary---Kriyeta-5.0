package com.emergency.patient.utils;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.BarcodeEncoder;

/**
 * QrGenerator — Generates a QR code Bitmap from the patient UUID.
 *
 * The QR encodes the UUID only — no PII is embedded.
 * The backend maps UUID → full patient data, requiring a valid JWT.
 */
public class QrGenerator {

    private static final int DEFAULT_SIZE_PX = 512;

    private QrGenerator() { /* static utility class */ }

    /**
     * Generates a square QR code bitmap for the given patient UID.
     *
     * @param patientUID The patient's unique identifier (encoded into the QR).
     * @param sizePx      Width and height of the output bitmap in pixels.
     * @return            Bitmap of the QR code, or null on failure.
     */
    public static Bitmap generate(String patientUID, int sizePx) {
        // Wrap the UID in a secure, custom protocol payload
        String securePayload = generateSecurePayload(patientUID);
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(securePayload, BarcodeFormat.QR_CODE, sizePx, sizePx);
            BarcodeEncoder encoder = new BarcodeEncoder();
            return encoder.createBitmap(bitMatrix);
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Creates an obfuscated payload that generic scanners won't understand.
     * Format: medlink://emergency/v1/[Base64(Signature + UID)]
     */
    private static String generateSecurePayload(String uid) {
        if (uid == null) return "invalid";
        String signature = "ER-PATIENT-AUTH-V1:";
        String combined = signature + uid;
        String encoded = android.util.Base64.encodeToString(combined.getBytes(), android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE);
        return "medlink://emergency/v1/" + encoded;
    }

    /**
     * Generates a QR bitmap at the default 512×512 size.
     *
     * @param patientUUID The patient's unique identifier.
     * @return            Bitmap of the QR code, or null on failure.
     */
    public static Bitmap generate(String patientUUID) {
        return generate(patientUUID, DEFAULT_SIZE_PX);
    }
}
