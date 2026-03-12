package com.observerbot;

import android.graphics.Bitmap;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class ScreenObserver {

    private final TextRecognizer recognizer;
    private final OverlayService service;

    public ScreenObserver(OverlayService service) {
        this.service = service;
        this.recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public void analyzeScreen(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        recognizer.process(image)
            .addOnSuccessListener(result -> {
                String text = result.getText();
                String screen = identifyScreen(text);
                service.updateStatus("Screen: " + screen);
            })
            .addOnFailureListener(e -> {
                service.updateStatus("OCR Error: " + e.getMessage());
            });
    }

    private String identifyScreen(String text) {

        if (text.contains("Donation Chances")) {
            if (text.contains("0/30") || text.contains("Insufficient")) {
                return "DONATION EXHAUSTED";
            }
            return "DONATION AVAILABLE";
        }
        if (text.contains("TECHNOLOGY") || text.contains("Today Donations")) return "ALLIANCE TECHNOLOGY";
        if (text.contains("Activity Rewards") || text.contains("Shop Rewards")) return "ALLIANCE GIFT";
        if (text.contains("ALLIANCE") && text.contains("Technology")) return "ALLIANCE HOME";
        if (text.contains("Daily Missions")) return "DAILY MISSIONS";
        if (text.contains("Main Missions")) return "MAIN MISSIONS";
        if (text.contains("Claim Free Daily") || text.contains("Today VIP Points")) return "VIP SCREEN";
        if (text.contains("Defeat Zombies")) return "ZOMBIE SEARCH";
        if (text.contains("Deploy Engine Squad")) return "FIELD SEARCH";
        if (text.contains("Auto collect AFK")) return "AFK POPUP";
        if (text.contains("X:") && text.contains("Y:")) return "WORLD MAP";

        return "UNKNOWN - BOT STOPPED";
    }
}
