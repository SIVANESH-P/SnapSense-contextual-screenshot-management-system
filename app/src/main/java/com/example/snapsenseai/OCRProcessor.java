package com.example.snapsenseai;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class OCRProcessor {
    private final Context context;
    private final TextRecognizer recognizer;
    private final PIIRedactor redactor;
    private final GeminiApiClient geminiClient;
    private final FileOrganizer fileOrganizer;

    public OCRProcessor(Context context) {
        this.context = context;
        this.recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        this.redactor = new PIIRedactor();
        this.geminiClient = new GeminiApiClient();
        this.fileOrganizer = new FileOrganizer(context);
    }

    public void processScreenshot(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String rawText = visionText.getText();
                        Log.d("SnapSense", "Raw OCR Extracted: " + rawText);

                        // 1. Local Privacy Scrubbing
                        String safeText = redactor.redact(rawText);
                        Log.d("SnapSense", "Privacy-Preserved Text: " + safeText);

                        // 2. Update Global Buffer
                        AnonymizedMetadataBuffer.getInstance().setBuffer(safeText);

                        // 3. Trigger Phase 3 & Phase 6: Semantic Classification + Date Extraction
                        Log.d("SnapSense", "Requesting AI Classification & Event Extraction...");

                        geminiClient.classifyContent(safeText, new GeminiApiClient.GeminiCallback() {
                            @Override
                            public void onSuccess(String category, int ttlHours, String suggestedName, String eventDate, String eventTime) {
                                // Phase 4: Move and Rename
                                fileOrganizer.moveAndRename(imageUri, category, suggestedName);

                                // --- PHASE 6: UPDATED CALENDAR NOTIFICATION LOGIC ---
                                if (eventDate != null && !eventDate.isEmpty() && !eventDate.equalsIgnoreCase("null")) {
                                    Log.d("SnapSense", "Event Detected: " + eventDate + " at " + eventTime);

                                    // Bridge: If we are in the Service, send a notification
                                    if (context instanceof SnapSenseService) {
                                        ((SnapSenseService) context).sendCalendarNotification(suggestedName, eventDate, eventTime);
                                    }
                                    // If we are in the Activity (rare during background use), show dialog directly
                                    else if (context instanceof MainActivity) {
                                        ((MainActivity) context).showCalendarDialog(suggestedName, eventDate, eventTime);
                                    }
                                }

                                // Phase 5: Schedule Deletion Lifecycle
                                androidx.work.Data inputData = new androidx.work.Data.Builder()
                                        .putString("image_uri", imageUri.toString())
                                        .putString("file_name", suggestedName)
                                        .build();

                                androidx.work.OneTimeWorkRequest deleteWorkRequest =
                                        new androidx.work.OneTimeWorkRequest.Builder(DeleteWorker.class)
                                                .setInitialDelay(ttlHours, java.util.concurrent.TimeUnit.HOURS)
                                                .setInputData(inputData)
                                                .addTag("SCREENSHOT_LIFECYCLE")
                                                .build();

                                androidx.work.WorkManager.getInstance(context).enqueue(deleteWorkRequest);
                            }

                            @Override
                            public void onFailure(Exception e) {
                                Log.e("SnapSense", "Gemini Classification Failed: " + e.getMessage());
                            }
                        });
                    })
                    .addOnFailureListener(e -> Log.e("SnapSense", "OCR Failed: " + e.getMessage()));
        } catch (Exception e) {
            Log.e("SnapSense", "Error loading image: " + e.getMessage());
        }
    }
}