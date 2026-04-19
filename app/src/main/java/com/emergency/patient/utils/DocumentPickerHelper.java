package com.emergency.patient.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * DocumentPickerHelper — Storage Access Framework (SAF) wrapper.
 *
 * Filters for PDFs and images (JPEG, PNG) only. Copies the chosen file to
 * app-internal cacheDir so it can be read as a File for multipart upload.
 *
 * Usage:
 *   DocumentPickerHelper picker = new DocumentPickerHelper(activity, uri -> { ... });
 *   picker.launch();
 */
public class DocumentPickerHelper {

    public interface OnDocumentPicked {
        void onPicked(Uri uri, String displayName, File cachedCopy);
        void onCancelled();
    }

    private static final String[] SUPPORTED_MIME_TYPES = {
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/jpg"
    };

    private final ActivityResultLauncher<Intent> launcher;
    private final Context context;
    private final OnDocumentPicked callback;

    public DocumentPickerHelper(AppCompatActivity activity, OnDocumentPicked callback) {
        this.context  = activity.getApplicationContext();
        this.callback = callback;

        launcher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK
                            && result.getData() != null
                            && result.getData().getData() != null) {

                        Uri uri         = result.getData().getData();
                        String name     = queryDisplayName(uri);
                        File cached     = copyToCache(uri, name);
                        callback.onPicked(uri, name, cached);
                    } else {
                        callback.onCancelled();
                    }
                });
    }

    /** Opens the SAF picker, restricted to PDF and image MIME types. */
    public void launch() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_MIME_TYPES);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        launcher.launch(intent);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String queryDisplayName(Uri uri) {
        String result = "document";
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                result = cursor.getString(0);
            }
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * Copies the SAF URI content to a File in cacheDir so it can be used
     * with OkHttp's RequestBody / MultipartBody.
     */
    private File copyToCache(Uri uri, String displayName) {
        try {
            File dest = new File(context.getCacheDir(), "upload_" + displayName);
            try (InputStream is = context.getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(dest)) {
                if (is == null) return null;
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
            }
            return dest;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Returns the MIME type of a URI (e.g. "application/pdf"). */
    public static String getMimeType(Context context, Uri uri) {
        String type = context.getContentResolver().getType(uri);
        if (type == null) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type != null ? type : "application/octet-stream";
    }
}
