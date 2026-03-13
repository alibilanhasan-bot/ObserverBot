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

    private static final long FAST_SCAN_MS = 800;
    private static final long SLOW_SCAN_MS = 5000;
    private static final long STARTUP_DELAY_MS = 3000;
    private static final int SLOW_DOWN_AFTER = 5;

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
        screenObserver.service.updateStatus("⏳ Starting in 3s...\nOpen your game now!");
        handler.postDelayed(this::scan, STARTUP_DELAY_MS);
    }

    private void scan() {
        if (!running) return;

        captureManager.capture(bitmap -> {
            if (!running) { bitmap.recycle(); return; }

            // Give OverlayService a copy of this frame for tap inference
            if (OverlayService.instance != null) {
                OverlayService.instance.setLastFrame(bitmap);
            }

            screenObserver.analyzeFullScreen(bitmap);

            long nextDelay = isSlowMode ? SLOW_SCAN_MS : FAST_SCAN_MS;
            handler.postDelayed(this::scan, nextDelay);
        });
    }

    public void onScreenDetected(String screenType) {
        if (screenType == null) return;
        if (screenType.equals(lastScreenType)) {
            identicalCount++;
        } else {
            identicalCount = 0;
            isSlowMode = false;
            lastScreenType = screenType;
        }
        if (identicalCount >= SLOW_DOWN_AFTER && !isSlowMode) {
            isSlowMode = true;
            screenObserver.service.updateStatus(
                "💤 Slow scan — screen unchanged\n" +
                "Screen: " + screenType + "\n" +
                "Still watching...");
        }
    }

    public void wakeOnTap() {
        if (isSlowMode) {
            isSlowMode = false;
            identicalCount = 0;
        }
        // Trigger an immediate scan on next handler loop
        if (handler != null) handler.post(this::scan);
    }

    public void stop() {
        running = false;
        if (handlerThread != null) { handlerThread.quitSafely(); handlerThread = null; }
    }
}
