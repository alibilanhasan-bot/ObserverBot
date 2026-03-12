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

    // All new collectors
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

        // Initialize all collectors
        touchHeatmap = new TouchHeatmap(
            service.getResources().getDisplayMetrics().widthPixels,
            service.getResources().getDisplayMetrics().heightPixels
        );
        gestureRecorder = new GestureRecorder();
        transitionLogger = new ScreenTransitionLogger();
        colorSampler = new ColorSampler();
        frameDifferencer = new FrameDifferencer();
        sessionTimeline = new SessionTimeline();
    }

    public void setObserverLoop(ObserverLoop loop) {
        this.observerLoop = loop;
    }

    // Called by ObserverLoop every 500ms - full screen scan
    public void analyzeFullScreen(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(image)
            .addOnSuccessListener(result -> {
                String text = result.getText();
                String screenType = identifyScreen(text);

                // Build observation with confidence scores
                ObservationData obs = new ObservationData(screenType, text);
                obs.ui.coordinates = "FULL_SCREEN";
                UITextExtractor.extract(text, obs.ui);
                addConfidenceScores(result, obs);

                observations.add(obs);
                screenshots.add(bitmap);

                // Color sample
                colorSampler.sample(bitmap, screenType);

                // Frame diff
                frameDifferencer.diff(bitmap, screenType);

                // Screen transition
                if (!screenType.equals(lastScreenType)) {
                    transitionLogger.onScreenDetected(screenType);
                    if (!lastScreenType.isEmpty()) {
                        sessionTimeline.logTransition(lastScreenType, screenType);
                    }
                    lastScreenType = screenType;
                }

                // Session timeline
                sessionTimeline.logScreen(screenType);

                // Gesture recorder current screen
                gestureRecorder.setCurrentScreen(screenType);

                service.updateStatus(
                    "🔄 Scan #" + observations.size() + "\n" +
                    "Screen: " + screenType + "\n" +
                    "Taps: " + touchHeatmap.getTapCount() +
                    "  Transitions: " + transitionLogger.getTransitionCount()
                );

                if (observerLoop != null) {
                    observerLoop.onScreenDetected(screenType);
                }
            })
            .addOnFailureListener(e ->
                service.updateStatus("OCR Error: " + e.getMessage())
            );
    }

    // Called by TapObserver on tap
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

                // Record in heatmap
                touchHeatmap.recordTap(tapX, tapY, lastScreenType);

                // Record gesture
                gestureRecorder.onTouchDown(tapX, tapY);
                gestureRecorder.onTouchUp(tapX, tapY);

                // Color at tap
                colorSampler.sampleAtTap(cropped, 0, 0);

                // Timeline
                sessionTimeline.logTap(tapX, tapY, lastScreenType);

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

    // Extract ML Kit confidence scores for each word
    private void addConfidenceScores(Text result, ObservationData obs) {
        StringBuilder confident = new StringBuilder();
        StringBuilder lowConfidence = new StringBuilder();

        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    Float conf = element.getConfidence();
                    if (conf != null) {
                        if (conf >= 0.85f) {
                            confident.append(element.getText()).append(" ");
                        } else {
                            lowConfidence.append(element.getText())
                                .append("(").append(String.format("%.0f%%", conf * 100)).append(") ");
                        }
                    }
                }
            }
        }
        // Store in raw text field with markers
        if (confident.length() > 0 || lowConfidence.length() > 0) {
            obs.rawOcrText = obs.rawOcrText
                + "\n[HIGH_CONF]: " + confident.toString().trim()
                + "\n[LOW_CONF]: " + lowConfidence.toString().trim();
        }
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
    public TouchHeatmap getTouchHeatmap() { return touchHeatmap; }
    public GestureRecorder getGestureRecorder() { return gestureRecorder; }
    public ScreenTransitionLogger getTransitionLogger() { return transitionLogger; }
    public ColorSampler getColorSampler() { return colorSampler; }
    public FrameDifferencer getFrameDifferencer() { return frameDifferencer; }
    public SessionTimeline getSessionTimeline() { return sessionTimeline; }
    public void clearAll() { observations.clear(); screenshots.clear(); }
            }
