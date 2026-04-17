package com.example.snapsenseai;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SnapSense";

    private FileOrganizer fileOrganizer;
    private Uri pendingUri;
    private String pendingCategory;
    private String pendingName;

    private final ActivityResultLauncher<IntentSenderRequest> intentSenderLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (pendingUri != null) {
                        // User granted permission! Retry the move.
                        fileOrganizer.moveAndRename(pendingUri, pendingCategory, pendingName);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        fileOrganizer = new FileOrganizer(this);

        Intent serviceIntent = new Intent(this, SnapSenseService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        handleIntent(getIntent());
        Log.d(TAG, "SnapSense UI: Ready.");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        // 1. Handle Calendar Notification Tap
        if (intent.getBooleanExtra("OPEN_CALENDAR_DIALOG", false)) {
            String title = intent.getStringExtra("EVENT_TITLE");
            String date = intent.getStringExtra("EVENT_DATE");
            String time = intent.getStringExtra("EVENT_TIME");
            showCalendarDialog(title, date, time);
        }

        // 2. Handle Scoped Storage Permission Notification Tap (Phase 4/5)
        if (intent.getBooleanExtra("REQUEST_PERMISSION", false)) {
            Log.d(TAG, "Handling Permission request from Notification...");
            String uriStr = intent.getStringExtra("PENDING_URI");
            String cat = intent.getStringExtra("PENDING_CAT");
            String name = intent.getStringExtra("PENDING_NAME");

            if (uriStr != null) {
                Uri uri = Uri.parse(uriStr);
                // Triggering this while Activity is in foreground will launch the system popup
                fileOrganizer.moveAndRename(uri, cat, name);
            }
        }
    }

    public void showCalendarDialog(String eventName, String dateStr, String timeStr) {
        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                    .setTitle("📅 Event Detected")
                    .setMessage("SnapSense found an event: " + eventName + "\nDate: " + dateStr + "\nAdd to your calendar?")
                    .setPositiveButton("Add", (dialog, which) -> {
                        addToCalendar(eventName, dateStr, timeStr);
                    })
                    .setNegativeButton("Ignore", null)
                    .show();
        });
    }

    private void addToCalendar(String title, String date, String time) {
        try {
            Calendar cal = Calendar.getInstance();
            String fullTime = (time != null && !time.equals("null")) ? time : "09:00";
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            cal.setTime(sdf.parse(date + " " + fullTime));

            Intent intent = new Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, "[SnapSense] " + title)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.getTimeInMillis())
                    .putExtra(CalendarContract.Events.DESCRIPTION, "Auto-detected by SnapSenseAI.");

            // Use the safer "startActivity" with a try-catch for the Query restriction fix
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "No Calendar app found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Calendar Error: " + e.getMessage());
        }
    }

    public void requestFilePermission(IntentSenderRequest request, Uri uri, String cat, String name) {
        this.pendingUri = uri;
        this.pendingCategory = cat;
        this.pendingName = name;
        intentSenderLauncher.launch(request);
    }
}