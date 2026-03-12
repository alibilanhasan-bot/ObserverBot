package com.observerbot;

import android.os.Handler;
import android.os.Looper;

public class ObserverLoop {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ScreenCaptureManager captureManager;
    private final ScreenObserver screenObserver;
    private boolean running = false;
    private boolean paused = false;
    private int scanCount = 0;
    private int identicalCount = 0;
    private String lastScreenType = "";

    private static final long SCAN_INTERVAL_MS = 500;
    private static final int IDENTICAL_LIMIT = 5;

    public ObserverLoop(ScreenCaptureManager captureManager, ScreenObserver screenObserver) {
        this.captureManager = captureManager;
        this.screenObserver = screenObserver;
    }

    public void start() {
        if (running) return;
        running = true;
        paused = false;
        scheduleNext();
    }

    public void stop() {
        running = false;
        paused = false;
        handler.removeCallbacksAndMessages(null);
    }

    // Called by TapObserver every time user taps - wakes up the scanner
    public void wakeOnTap() {
        if (running && paused) {
            paused = false;
            identicalCount = 0;
            lastScreenType = "";
            scheduleNext();
        }
    }

    public boolean isRunning() { return running; }
    public boolean isPaused() { return paused; }
    public int getScanCount() { return scanCount; }

    // Called by ScreenObserver after each full scan with the detected screen type
    public void onScreenDetected(String screenType) {
        if (screenType.equals(lastScreenType)) {
            identicalCount++;
            if (identicalCount >= IDENTICAL_LIMIT) {
                paused = true;
                screenObserver.service.updateStatus(
                    "💤 Paused - screen unchanged\n" +
                    "Screen: " + screenType + "\n" +
                    "Total scans: " + scanCount + "\n" +
                    "Tap screen to resume"
                );
            }
        } else {
            identicalCount = 0;
            lastScreenType = screenType;
        }
    }

    private void scheduleNext() {
        if (!running || paused) return;
        handler.postDelayed(() -> {
            captureManager.captureForTap(0, 0, bitmap -> {
                screenObserver.analyzeFullScreen(bitmap);
                scanCount++;
            });
            scheduleNext();
        }, SCAN_INTERVAL_MS);
    }
}
