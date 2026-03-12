package com.observerbot;

import android.graphics.Bitmap;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

public class ScreenObserver {

    private final TextRecognizer recognizer;
    public final OverlayService service;
    private final List<ObservationData> observations = new ArrayList<>();
    private final List<Bitmap> screenshots = new ArrayList<>();
    private ObserverLoop observerLoop;

    public ScreenObserver(OverlayService service) {
        this.service = service;
        this.recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public void setObserverLoop(ObserverLoop loop) {
        this.observerLoop = loop;
    }

    // Called by ObserverLoop every 500ms - scans entire screen
    public void analyzeFullScreen(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(image)
            .addOnSuccessListener(result -> {
                String text = result.getText();
                String screenType = identifyScreen(text);

                ObservationData obs = new ObservationData(screenType, text);
                obs.ui.coordinates = "FULL_SCREEN";
                UITextExtractor.extract(text, obs.ui);

                observations.add(obs);
                screenshots.add(bitmap);

                service.updateStatus(
                    "🔄 Scan #" + observations.size() + "\n" +
                    "Screen: " + screenType + "\n" +
                    "Time: " + obs.timestamp + "\n" +
                    "Claims: " + obs.ui.claimButtonCount +
                    "  Donations: " + (obs.ui.donationCount != null ? obs.ui.donationCount : "-")
                );

                // Tell ObserverLoop what screen was found so it can track identical scans
                if (observerLoop != null) {
                    observerLoop.onScreenDetected(screenType);
                }
            })
            .addOnFailureListener(e ->
                service.updateStatus("OCR Error: " + e.getMessage())
            );
    }

    // Called by TapObserver when user taps
    public void analyzeTapRegion(Bitmap cropped, int tapX, int tapY) {
        InputImage image = InputImage.fromBitmap(cropped, 0);
        recognizer.process(image)
            .addOnSuccessListener(result -> {
                String text = result.getText().trim();

                ObservationData obs = new ObservationData("TAP_REGION", text);
                obs.ui.coordinates = "X:" + tapX + " Y:" + tapY;
                UITextExtractor.extract(text, obs.ui);

                observations.add(obs);
                screenshots.add(cropped);

                service.updateStatus(
                    "👆 Tap X:" + tapX + " Y:" + tapY + "\n" +
                    "Text: " + (text.isEmpty() ? "(none)" : text) + "\n" +
                    "Total logs: " + observations.size()
                );
            })
            .addOnFailureListener(e ->
                service.updateStatus("Tap OCR Error: " + e.getMessage())
            );
    }

    private String identifyScreen(String text) {
        if (text.contains("Donation Chances")) {
            if (text.contains("0/30") || text.contains("Insufficient")) return "DONATION_EXHAUSTED";
            return "DONATION_AVAILABLE";
        }
        if (text.contains("TECHNOLOGY") || text.contains("Today Donations")) return "ALLIANCE_TECHNOLOGY";
        if (text.contains("Activity Rewards") || text.contains("Shop Rewards")) return "ALLIANCE_GIFT";
        if (text.contains("ALLIANCE") && text.contains("Technology")) return "ALLIANCE_HOME";
        if (text.contains("Daily Missions")) return "DAILY_MISSIONS";
        if (text.contains("Main Missions")) return "MAIN_MISSIONS";
        if (text.contains("Claim Free Daily") || text.contains("Today VIP Points")) return "VIP_SCREEN";
        if (text.contains("Defeat Zombies")) return "ZOMBIE_SEARCH";
        if (text.contains("Deploy Engine Squad")) return "FIELD_SEARCH";
        if (text.contains("Auto collect AFK")) return "AFK_POPUP";
        if (text.contains("X:") && text.contains("Y:")) return "WORLD_MAP";
        return "UNKNOWN";
    }

    public List<ObservationData> getObservations() { return observations; }
    public List<Bitmap> getScreenshots() { return screenshots; }
    public void clearAll() { observations.clear(); screenshots.clear(); }
}
