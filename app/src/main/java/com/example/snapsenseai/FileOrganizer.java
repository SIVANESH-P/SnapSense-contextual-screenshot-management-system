package com.example.snapsenseai;

import android.app.RecoverableSecurityException;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.activity.result.IntentSenderRequest;

import java.util.Arrays;
import java.util.List;

public class FileOrganizer {
    private Context context;
    private final List<String> validCategories = Arrays.asList("Finance", "Study", "Social", "Travel", "OTP");

    public FileOrganizer(Context context) {
        this.context = context;
    }

    public void moveAndRename(Uri sourceUri, String category, String newName) {
        try {
            String finalCategory = validCategories.contains(category) ? category : "Miscellaneous";
            ContentValues values = new ContentValues();

            // Rename the file
            values.put(MediaStore.Images.Media.DISPLAY_NAME, newName + "_" + (System.currentTimeMillis() / 1000));

            // Set the new path under Pictures/SnapSenseAI/
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapSenseAI/" + finalCategory);
            }

            int rowsUpdated = context.getContentResolver().update(sourceUri, values, null, null);

            if (rowsUpdated > 0) {
                Log.d("SnapSense", "Successfully moved to: " + finalCategory + " as " + newName);
            }

        } catch (SecurityException securityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    securityException instanceof RecoverableSecurityException) {

                RecoverableSecurityException recoverableException = (RecoverableSecurityException) securityException;
                Log.d("SnapSense", "Scoped Storage restriction hit. Determining context...");

                // 1. Case: App is in Foreground (MainActivity)
                if (context instanceof MainActivity) {
                    IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(
                            recoverableException.getUserAction().getActionIntent().getIntentSender()).build();

                    ((MainActivity) context).requestFilePermission(
                            intentSenderRequest,
                            sourceUri,
                            category,
                            newName
                    );
                }
                // 2. Case: App is in Background (SnapSenseService)
                else if (context instanceof SnapSenseService) {
                    Log.d("SnapSense", "In Service: Sending Permission Notification...");
                    ((SnapSenseService) context).sendPermissionNotification(sourceUri, category, newName);
                }
            } else {
                Log.e("SnapSense", "Persistent Security Error: " + securityException.getMessage());
            }
        } catch (Exception e) {
            Log.e("SnapSense", "General Error in FileOrganizer: " + e.getMessage());
        }
    }
}