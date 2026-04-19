package com.emergency.patient.utils;

import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Base64Utils {

    public static String encodeFileToBase64(File file) {
        if (file == null || !file.exists()) return null;

        try (FileInputStream inputStream = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            inputStream.read(bytes);
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
