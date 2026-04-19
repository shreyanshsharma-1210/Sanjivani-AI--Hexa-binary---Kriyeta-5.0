package com.emergency.patient.utils;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class LocalStorageManager {

    private static final String DOCUMENTS_DIR = "health_documents";

    public static String saveFileToInternalStorage(Context context, Uri uri, String originalName) {
        try {
            File dir = new File(context.getFilesDir(), DOCUMENTS_DIR);
            if (!dir.exists()) dir.mkdirs();

            String fileName = UUID.randomUUID().toString() + "_" + originalName;
            File destinationFile = new File(dir, fileName);

            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            OutputStream outputStream = new FileOutputStream(destinationFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.close();
            inputStream.close();

            return destinationFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static File getFile(String absolutePath) {
        return new File(absolutePath);
    }
}
