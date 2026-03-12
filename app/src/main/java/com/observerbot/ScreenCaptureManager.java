package com.observerbot;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class ScreenCaptureManager {

    public interface CaptureCallback {
        void onBitmap(Bitmap bitmap);
    }

    private final Context context;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth, screenHeight, screenDensity;
    private HandlerThread handlerThread;
    private Handler handler;

    public ScreenCaptureManager(Context context) {
        this.context = context;
        DisplayMetrics metrics = new DisplayMetrics();
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
            .getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }

    public void setup(int resultCode, Intent data, Runnable onReady) {
        MediaProjectionManager manager = (MediaProjectionManager)
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = manager.getMediaProjection(resultCode, data);

        handlerThread = new HandlerThread("ScreenCapture");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ObserverBot",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(), null, handler);

        if (onReady != null) handler.post(onReady);
    }

    // Captures the current screen and returns a SAFE COPY of the bitmap
    // The copy is in ARGB_8888 format which ML Kit requires
    public void capture(CaptureCallback callback) {
        if (imageReader == null) return;

        handler.post(() -> {
            Image image = null;
            try {
                image = imageReader.acquireLatestImage();
                if (image == null) return;

                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * screenWidth;

                // Create raw bitmap from buffer
                Bitmap rawBitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888);
                rawBitmap.copyPixelsFromBuffer(buffer);

                // CRITICAL: Create a clean cropped copy in ARGB_8888
                // This copy is independent of the Image buffer — safe to use after image.close()
                Bitmap safeCopy = Bitmap.createBitmap(
                    rawBitmap, 0, 0, screenWidth, screenHeight);

                // Recycle the raw bitmap (we have the clean copy)
                rawBitmap.recycle();

                // Close the Image BEFORE calling callback
                // The safeCopy is fully independent — it won't be affected
                image.close();
                image = null;

                // Deliver the safe copy to caller
                callback.onBitmap(safeCopy);

            } catch (Exception e) {
                if (image != null) {
                    try { image.close(); } catch (Exception ignored) {}
                }
            }
        });
    }

    // Captures screen then crops a 300x300 region around the tap point
    public void captureForTap(int tapX, int tapY, CaptureCallback callback) {
        capture(fullBitmap -> {
            try {
                int cropSize = 300;
                int left = Math.max(0, tapX - cropSize / 2);
                int top = Math.max(0, tapY - cropSize / 2);
                int right = Math.min(fullBitmap.getWidth(), tapX + cropSize / 2);
                int bottom = Math.min(fullBitmap.getHeight(), tapY + cropSize / 2);

                int w = right - left;
                int h = bottom - top;

                if (w > 0 && h > 0) {
                    Bitmap crop = Bitmap.createBitmap(fullBitmap, left, top, w, h);
                    fullBitmap.recycle();
                    callback.onBitmap(crop);
                } else {
                    callback.onBitmap(fullBitmap);
                }
            } catch (Exception e) {
                callback.onBitmap(fullBitmap);
            }
        });
    }

    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }

    public void stop() {
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        if (handlerThread != null) { handlerThread.quitSafely(); handlerThread = null; }
    }
}
