package com.observerbot;

import android.graphics.Bitmap;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class TapObserver {

    private final TextRecognizer recognizer;
    private final OverlayService service;

    // How big the crop area is around the tap point (in pixels)
    private static final int CROP_RADIUS = 150;

    public TapObserver(OverlayService service) {
        this.service = service;
        this.recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public void analyzeAtTap(Bitmap fullScreen, int tapX, int tapY) {

        // Step 1: Calculate crop boundaries around tap point
        int screenW = fullScreen.getWidth();
        int screenH = fullScreen.getHeight();

        int left   = Math.max(0, tapX - CROP_RADIUS);
        int top    = Math.max(0, tapY - CROP_RADIUS);
        int right  = Math.min(screenW, tapX + CROP_RADIUS);
        int bottom = Math.min(screenH, tapY + CROP_RADIUS);

        int cropW = right - left;
        int cropH = bottom - top;

        // Step 2: Crop the region around the tap
        Bitmap cropped = Bitmap.createBitmap(fullScreen, left, top, cropW, cropH);

        // Step 3: Run OCR on the cropped region
        InputImage image = InputImage.fromBitmap(cropped, 0);

        recognizer.process(image)
            .addOnSuccessListener(result -> {
                String text = result.getText().trim();

                // Step 4: Build observation for this tap
                ObservationData obs = new ObservationData("TAP_REGION", text);
                obs.ui.coordinates = "X:" + tapX + " Y:" + tapY;
                UITextExtractor.extract(text, obs.ui);

                // Step 5: Show result on overlay
                service.updateStatus(
                    "📍 Tap: X:" + tapX + " Y:" + tapY + "\n" +
                    "📝 Text: " + (text.isEmpty() ? "(none)" : text) + "\n" +
                    "🔘 Claims: " + obs.ui.claimButtonCount +
                    "  Donations: " + (obs.ui.donationCount != null ? obs.ui.donationCount : "-")
                );

                // Step 6: Save observation
                service.saveObservation(obs, cropped);
            })
            .addOnFailureListener(e ->
                service.updateStatus("OCR Error at tap: " + e.getMessage())
            );
    }
}
