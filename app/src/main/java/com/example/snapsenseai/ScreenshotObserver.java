package com.example.snapsenseai;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

public class ScreenshotObserver extends ContentObserver {
    private final Context context;
    private final OCRProcessor ocrProcessor;
    private long lastTriggerTime = 0;
    private static final long DEBOUNCE_DELAY = 3000;
    private String lastProcessedUri = "";

    public ScreenshotObserver(Handler handler, Context context) {
        super(handler);
        this.context = context;
        this.ocrProcessor = new OCRProcessor(context);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastTriggerTime < DEBOUNCE_DELAY) {
            return;
        }

        if (uri == null) {
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        if (uri.toString().equals(lastProcessedUri)) {
            return;
        }

        if (uri.toString().contains(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
            lastTriggerTime = currentTime;
            lastProcessedUri = uri.toString();
            handleNewScreenshot(uri);
        }
    }

    private void handleNewScreenshot(Uri uri) {
        // Use a Handler to poll the MediaStore until the file is no longer 'Pending'
        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            // We must query IS_PENDING to avoid the "Only owner" SecurityException
            String[] projection = {
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.IS_PENDING
            };

            try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {

                    int pendingColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_PENDING);
                    boolean isPending = cursor.getInt(pendingColumn) == 1;

                    if (isPending) {
                        Log.d("SnapSense", "Screenshot is still being written (Pending). Retrying...");
                        handleNewScreenshot(uri); // Retry after another delay
                        return;
                    }

                    String fileName = cursor.getString(0);
                    if (fileName != null && fileName.toLowerCase().contains("screenshot")) {
                        Log.d("SnapSense", "Verified & Ready: " + fileName);
                        ocrProcessor.processScreenshot(uri);
                    }
                }
            } catch (Exception e) {
                // This catches the 'Only owner' error if we hit it before the file is released
                Log.e("SnapSense", "Error accessing MediaStore: " + e.getMessage());
            }
        }, 500); // 500ms is the "sweet spot" for modern Redmi storage speeds
    }
}