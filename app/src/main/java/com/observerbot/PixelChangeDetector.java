package com.observerbot;

import android.graphics.Bitmap;
import android.graphics.Color;

public class PixelChangeDetector {

    private Bitmap lastFrame = null;
    private static final float CHANGE_THRESHOLD = 0.02f; // 2% pixels changed = new screen
    private static final int SAMPLE_STEP = 20; // sample every 20px for speed

    // Returns true if screen has changed significantly since last check
    public boolean hasChanged(Bitmap current) {
        if (lastFrame == null || lastFrame.getWidth() != current.getWidth()) {
            lastFrame = current.copy(current.getConfig(), false);
            return true;
        }

        int width = current.getWidth();
        int height = current.getHeight();
        int totalSamples = 0;
        int changedSamples = 0;

        for (int x = 0; x < width; x += SAMPLE_STEP) {
            for (int y = 0; y < height; y += SAMPLE_STEP) {
                int oldPixel = lastFrame.getPixel(x, y);
                int newPixel = current.getPixel(x, y);

                int rDiff = Math.abs(Color.red(oldPixel) - Color.red(newPixel));
                int gDiff = Math.abs(Color.green(oldPixel) - Color.green(newPixel));
                int bDiff = Math.abs(Color.blue(oldPixel) - Color.blue(newPixel));

                if (rDiff + gDiff + bDiff > 30) {
                    changedSamples++;
                }
                totalSamples++;
            }
        }

        float changeRatio = (float) changedSamples / totalSamples;
        if (changeRatio >= CHANGE_THRESHOLD) {
            lastFrame = current.copy(current.getConfig(), false);
            return true;
        }
        return false;
    }

    public void reset() {
        lastFrame = null;
    }
}
