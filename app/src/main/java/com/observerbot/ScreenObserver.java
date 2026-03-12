package com.observerbot;

import android.graphics.Bitmap;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
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

    private TouchHeatmap touchHeatmap;
    private GestureRecorder gestureRecorder;
    private ScreenTransitionLogger transitionLogger;
    private ColorSampler colorSampler;
    private FrameDifferencer frameDifferencer;
    private SessionTimeline sessionTimeline;

    private String lastScreenType = "";

    public ScreenObserver(OverlayService service) {
        this.service = service;
        this.recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        int w = service.getResources().getDisplayMetrics().widthPixels;
        int h = service.getResources().getDisplayMetrics().heightPixels;
        touchHeatmap = new TouchHeatmap(w, h);
        gestureRecorder = new GestureRecorder();
        transitionLogger = new ScreenTransitionLogger();
        colorSampler = new ColorSampler();
        frameDifferencer = new FrameDifferencer();
        sessionTimeline = new SessionTimeline();
    }

    public void setObserverLoop(ObserverLoop loop) {
        this.observerLoop = loop;
    }

    public void analyzeFullScreen(Bitmap bitmap) {
        // bitmap is already a safe independent copy from ScreenCaptureManager
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        recognizer.process(image)
            .addOnSuccessListener(result -> {
                String text = result.getText();
                String screenType = identifyScreen(text);

                ObservationData obs = new ObservationData(screenType, text);
                obs.ui.coordinates = "FULL_SCREEN";
                UITextExtractor.extract(text, obs.ui);
                addConfidenceScores(result, obs);

                observations.add(obs);
                screenshots.add(bitmap);

                colorSampler.sample(bitmap, screenType);
                frameDifferencer.diff(bitmap, screenType);

                if (!screenType.equals(lastScreenType)) {
                    transitionLogger.onScreenDetected(screenType);
                    if (!lastScreenType.isEmpty()) {
                        sessionTimeline.logTransition(lastScreenType, screenType);
                    }
                    lastScreenType = screenType;
                }

                sessionTimeline.logScreen(screenType);
                gestureRecorder.setCurrentScreen(screenType);

                service.updateStatus(
                    "🔄 Scan #" + observations.size() + "\n" +
                    "Screen: " + screenType + "\n" +
                    "Taps: " + touchHeatmap.getTapCount() +
                    "  Trans: " + transitionLogger.getTransitionCount() + "\n" +
                    "OCR chars: " + text.length()
                );

                if (observerLoop != null) {
                    observerLoop.onScreenDetected(screenType);
                }
            })
            .addOnFailureListener(e -> {
                service.updateStatus("❌ OCR Error: " + e.getMessage());
                // Still recycle bitmap on failure
                bitmap.recycle();
            });
    }

    public void analyzeTapRegion(Bitmap cropped, int tapX, int tapY) {
        InputImage image = InputImage.fromBitmap(cropped, 0);

        recognizer.process(image)
            .addOnSuccessListener(result -> {
                String text = result.getText().trim();

                ObservationData obs = new ObservationData("TAP_REGION", text);
                obs.ui.coordinates = "X:" + tapX + " Y:" + tapY;
                UITextExtractor.extract(text, obs.ui);
                addConfidenceScores(result, obs);

                observations.add(obs);
                screenshots.add(cropped);

                touchHeatmap.recordTap(tapX, tapY, lastScreenType);
                gestureRecorder.onTouchDown(tapX, tapY);
                gestureRecorder.onTouchUp(tapX, tapY);
                sessionTimeline.logTap(tapX, tapY, lastScreenType);

                service.updateStatus(
                    "👆 Tap X:" + tapX + " Y:" + tapY + "\n" +
                    "Screen: " + lastScreenType + "\n" +
                    "Text: " + (text.isEmpty() ? "(none)" : text.substring(0, Math.min(40, text.length()))) + "\n" +
                    "Total taps: " + touchHeatmap.getTapCount()
                );
            })
            .addOnFailureListener(e -> {
                cropped.recycle();
            });
    }

    private void addConfidenceScores(Text result, ObservationData obs) {
        StringBuilder confident = new StringBuilder();
        StringBuilder lowConf = new StringBuilder();

        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    Float conf = element.getConfidence();
                    if (conf != null) {
                        if (conf >= 0.85f) {
                            confident.append(element.getText()).append(" ");
                        } else {
                            lowConf.append(element.getText())
                                .append("(").append(String.format("%.0f%%", conf * 100)).append(") ");
                        }
                    }
                }
            }
        }
        if (confident.length() > 0 || lowConf.length() > 0) {
            obs.rawOcrText += "\n[HIGH_CONF]: " + confident.toString().trim()
                           + "\n[LOW_CONF]: " + lowConf.toString().trim();
        }
    }

    private String identifyScreen(String text) {
        if (text == null || text.isEmpty()) return "UNKNOWN";

        // Donation screens
        if (text.contains("Donation Chances")) {
            if (text.contains("0/30") || text.contains("Insufficient")) return "DONATION_EXHAUSTED";
            return "DONATION_AVAILABLE";
        }
        // Alliance screens
        if (text.contains("TECHNOLOGY") || text.contains("Today Donations")) return "ALLIANCE_TECHNOLOGY";
        if (text.contains("Activity Rewards") || text.contains("Shop Rewards")) return "ALLIANCE_GIFT";
        if (text.contains("ALLIANCE") && text.contains("Technology")) return "ALLIANCE_HOME";
        // Mission screens
        if (text.contains("Daily Missions")) return "DAILY_MISSIONS";
        if (text.contains("Main Missions")) return "MAIN_MISSIONS";
        // VIP
        if (text.contains("Claim Free Daily") || text.contains("Today VIP Points")) return "VIP_SCREEN";
        // Search screens
        if (text.contains("Defeat Zombies")) return "ZOMBIE_SEARCH";
        if (text.contains("Deploy Engine Squad")) return "FIELD_SEARCH";
        // Popups
        if (text.contains("Auto collect AFK")) return "AFK_POPUP";
        // Map
        if (text.contains("X:") && text.contains("Y:")) return "WORLD_MAP";
        // Base/city — what we saw in the screenshots
        if (text.contains("Alliance Center") || text.contains("Engine Barracks") ||
            text.contains("Trading Post") || text.contains("Rider Barracks")) return "BASE_CITY";
        if (text.contains("General Notice") || text.contains("Optimized Mode")) return "BASE_CITY";
        if (text.contains("Campaign") && text.contains("Alliance") &&
            text.contains("Hero")) return "BASE_CITY";

        return "UNKNOWN";
    }

    public List<ObservationData> getObservations() { return observations; }
    public List<Bitmap> getScreenshots() { return screenshots; }
    public TouchHeatmap getTouchHeatmap() { return touchHeatmap; }
    public GestureRecorder getGestureRecorder() { return gestureRecorder; }
    public ScreenTransitionLogger getTransitionLogger() { return transitionLogger; }
    public ColorSampler getColorSampler() { return colorSampler; }
    public FrameDifferencer getFrameDifferencer() { return frameDifferencer; }
    public SessionTimeline getSessionTimeline() { return sessionTimeline; }
    public void clearAll() { observations.clear(); screenshots.clear(); }
                    }
