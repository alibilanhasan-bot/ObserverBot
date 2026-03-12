package com.observerbot;

import android.os.Handler;
import android.os.Looper;

public class ObserverLoop {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ScreenCaptureManager captureManager;
    private final ScreenObserver screenObserver;
    private boolean running = false;

    private static final long SCAN_INTERVAL_MS = 3000; // scan every 3 seconds

    public ObserverLoop(ScreenCaptureManager captureManager, ScreenObserver screenObserver) {
        this.captureManager = captureManager;
        this.screenObserver = screenObserver;
    }

    public void start() {
        running = true;
        scheduleNext();
    }

    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void scheduleNext() {
        if (!running) return;
        handler.postDelayed(() -> {
            captureManager.capture();
            scheduleNext();
        }, SCAN_INTERVAL_MS);
    }
}
