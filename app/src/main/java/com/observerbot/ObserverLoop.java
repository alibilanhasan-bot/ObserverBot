package com.observerbot;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;

public class ObserverLoop {

    private final ScreenCaptureManager captureManager;
    private final ScreenObserver screenObserver;
    private HandlerThread handlerThread;
    private Handler handler;
    private boolean running = false;

    // Scan intervals
    private static final long FAST_SCAN_MS = 800;    // normal scanning - every 800ms
    private static final long SLOW_SCAN_MS = 5000;   // slow scan when screen unchanged
    private static final long STARTUP_DELAY_MS = 3000; // wait 3s for user to open game

    // Identical screen tracking - slow down but NEVER stop
    private static final int SLOW_DOWN_AFTER = 5;    // slow down after 5 identical screens
    private String lastScreenType = "";
    private int identicalCount = 0;
    private boolean isSlowMode = false;

    public ObserverLoop(ScreenCaptureManager captureManager, ScreenObserver screenObserver) {
        this.captureManager = captureManager;
        this.screenObserver = screenObserver;
    }

    public void start() {
        handlerThread = new HandlerThread("ObserverLoop");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        running = true;

        // 3 second startup delay — gives user time to open the game
        screenObserver.service.updateStatus(
            "⏳ Starting in 3 seconds...\nOpen your game now!"
        );

        handler.postDelayed(this::scan, STARTUP_DELAY_MS);
    }

    private void scan() {
        if (!running) return;

        captureManager.capture(bitmap -> {
            if (!running) {
                bitmap.recycle();
                return;
            }
            screenObserver.analyzeFullScreen(bitmap);

            // Schedule next scan — interval depends on mode
            long nextDelay = isSlowMode ? SLOW_SCAN_MS : FAST_SCAN_MS;
            handler.postDelayed(this::scan, nextDelay);
        });
    }

    // Called by ScreenObserver after OCR identifies the screen
    public void onScreenDetected(String screenType) {
        if (screenType == null) return;

        if (screenType.equals(lastScreenType)) {
            identicalCount++;
        } else {
            // Screen changed — back to fast mode
            identicalCount = 0;
            isSlowMode = false;
            lastScreenType = screenType;
        }

        // After 5 identical screens, slow down (but NEVER stop)
        if (identicalCount >= SLOW_DOWN_AFTER && !isSlowMode) {
            isSlowMode = true;
            screenObserver.service.updateStatus(
                "💤 Slow scan (5s) — screen unchanged\n" +
                "Screen: " + screenType + "\n" +
                "Still watching — tap game to speed up"
            );
        }
    }

    // Called when AccessibilityService detects any touch — switches back to fast mode
    public void wakeOnTap() {
        if (isSlowMode) {
            isSlowMode = false;
            identicalCount = 0;
            screenObserver.service.updateStatus("👆 Tap detected — fast scan resumed");
        }
    }

    public void stop() {
        running = false;
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }
    }

    public boolean isSlowMode() { return isSlowMode; }
}
