package com.observerbot;

import android.graphics.Bitmap;

public class TapObserver {

    private final ScreenObserver screenObserver;
    private ObserverLoop observerLoop;
    private static final int CROP_RADIUS = 150;

    public TapObserver(ScreenObserver screenObserver) {
        this.screenObserver = screenObserver;
    }

    public void setObserverLoop(ObserverLoop loop) {
        this.observerLoop = loop;
    }

    public void analyzeAtTap(Bitmap fullScreen, int tapX, int tapY) {
        // Wake up the continuous scanner if it was paused
        if (observerLoop != null) {
            observerLoop.wakeOnTap();
        }

        // Crop and analyze the tapped area
        int screenW = fullScreen.getWidth();
        int screenH = fullScreen.getHeight();
        int left  = Math.max(0, tapX - CROP_RADIUS);
        int top   = Math.max(0, tapY - CROP_RADIUS);
        int cropW = Math.min(screenW, tapX + CROP_RADIUS) - left;
        int cropH = Math.min(screenH, tapY + CROP_RADIUS) - top;

        Bitmap cropped = Bitmap.createBitmap(fullScreen, left, top, cropW, cropH);
        screenObserver.analyzeTapRegion(cropped, tapX, tapY);
    }
}
