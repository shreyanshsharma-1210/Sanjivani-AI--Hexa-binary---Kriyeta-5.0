package com.emergency.patient.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.emergency.patient.R;
import com.emergency.patient.activities.DashboardActivity;
import com.emergency.patient.network.FcmTokenSyncManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * AppFirebaseMessagingService — Handles incoming FCM push notifications.
 *
 * Expected payload (from backend) when AI processing completes:
 * {
 * "data": {
 * "type": "extraction_complete",
 * "processing_id": "abc-123",
 * "status": "success",
 * "payload": "{...extracted JSON...}" // optional inline payload
 * }
 * }
 */
public class AppFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "emergency_extraction_channel";
    private static final int NOTIFICATION_ID = 9002;

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM token refreshed: " + token.substring(0, Math.min(token.length(), 10)) + "...");
        FcmTokenSyncManager.uploadTokenToBackend(this, token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);

        if (message.getData().isEmpty())
            return;

        String type = message.getData().get("type");
        String processingId = message.getData().get("processing_id");
        String status = message.getData().get("status");
        String payload = message.getData().get("payload");
        String title = message.getData().get("title");
        String body = message.getData().get("body");

        Log.d(TAG, "FCM received: type=" + type + " id=" + processingId + " status=" + status);

        // Fallback for non-extraction pushes (data payload based).
        if (!TextUtils.isEmpty(title) || !TextUtils.isEmpty(body)) {
            showGenericNotification(
                    TextUtils.isEmpty(title) ? "Emergency Response" : title,
                    TextUtils.isEmpty(body) ? "You have a new update." : body);
            return;
        }

        // Fallback for notification payload based messages.
        if (message.getNotification() != null) {
            String nTitle = message.getNotification().getTitle();
            String nBody = message.getNotification().getBody();
            showGenericNotification(
                    TextUtils.isEmpty(nTitle) ? "Emergency Response" : nTitle,
                    TextUtils.isEmpty(nBody) ? "You have a new update." : nBody);
        }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private void showGenericNotification(String title, String body) {
        ensureChannel();

        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sos_cross)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null)
            nm.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AI Extraction Results",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifies when your medical document has been processed");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null)
                nm.createNotificationChannel(channel);
        }
    }

}
