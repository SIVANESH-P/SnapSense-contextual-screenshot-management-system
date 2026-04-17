package com.example.snapsenseai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class SnapSenseService extends Service {
    private static final String TAG = "SnapSenseService";
    private static final String CHANNEL_ID = "SnapSense_Monitor_Channel";
    private ScreenshotObserver observer;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Starting: Monitoring Active");

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SnapSense AI is Active")
                .setContentText("Monitoring for new screenshots...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        startForeground(1, notification);

        if (observer == null) {
            observer = new ScreenshotObserver(new Handler(Looper.getMainLooper()), this);
            getContentResolver().registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer
            );
        }

        return START_STICKY;
    }

    // --- PHASE 6: Bridge to MainActivity for Calendar Dialog ---
    public void sendCalendarNotification(String title, String date, String time) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("OPEN_CALENDAR_DIALOG", true);
        intent.putExtra("EVENT_TITLE", title);
        intent.putExtra("EVENT_DATE", date);
        intent.putExtra("EVENT_TIME", time);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("📅 Event Detected!")
                .setContentText("Tap to add '" + title + "' to your calendar.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(2, notification);
        }
    }

    // --- PHASE 4/5: Bridge to MainActivity for Scoped Storage Permission ---
    // NEW: Handles the case where we can't move the file from the background
    public void sendPermissionNotification(Uri uri, String category, String name) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("REQUEST_PERMISSION", true);
        intent.putExtra("PENDING_URI", uri.toString());
        intent.putExtra("PENDING_CAT", category);
        intent.putExtra("PENDING_NAME", name);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                1, // Unique RequestCode to distinguish from Calendar
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("📂 Action Required")
                .setContentText("Tap to allow organizing your new screenshot into " + category)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(3, notification); // Use ID 3
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "SnapSense Background Monitor",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service Destroyed: Stopping Monitor");
        if (observer != null) {
            getContentResolver().unregisterContentObserver(observer);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}