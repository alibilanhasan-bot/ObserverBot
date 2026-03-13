package com.observerbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class OverlayService extends Service {

    private WindowManager windowManager;
    private View overlayView;
    private View stopButtonView;
    private TextView statusText;

    private ScreenObserver screenObserver;
    private TapObserver tapObserver;
    ObserverLoop observerLoop;
    private ScreenCaptureManager captureManager;
    private ZipExporter zipExporter;
    private AccessibilityDataStore accessibilityDataStore;

    // Last captured frame — updated by ObserverLoop after every scan
    private Bitmap lastFrame = null;
    private final Object frameLock = new Object();

    private static final String CHANNEL_ID = "ObserverBotChannel";
    public static OverlayService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        startForeground(1, buildNotification());

        accessibilityDataStore = new AccessibilityDataStore();
        GameAccessibilityService.setDataStore(accessibilityDataStore);

        screenObserver = new ScreenObserver(this);
        tapObserver = new TapObserver(screenObserver);
        zipExporter = new ZipExporter(this);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showOverlay();
        showStopButton();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("resultCode")) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");
            ScreenCaptureManager manager = new ScreenCaptureManager(this);
            manager.setup(resultCode, data, null);
            setCaptureManager(manager);
        }
        return START_STICKY;
    }

    public void setCaptureManager(ScreenCaptureManager captureManager) {
        this.captureManager = captureManager;
        observerLoop = new ObserverLoop(captureManager, screenObserver);
        screenObserver.setObserverLoop(observerLoop);
        tapObserver.setObserverLoop(observerLoop);
        observerLoop.start();
    }

    // Called by ObserverLoop after every successful full scan
    // Keeps a copy of the latest frame for tap region inference
    public void setLastFrame(Bitmap frame) {
        synchronized (frameLock) {
            if (lastFrame != null && !lastFrame.isRecycled()) {
                lastFrame.recycle();
            }
            // Store a copy — the original will be used/recycled by ScreenObserver
            try {
                lastFrame = frame.copy(frame.getConfig(), false);
            } catch (Exception e) {
                lastFrame = null;
            }
        }
    }

    // Called by GameAccessibilityService on TYPE_TOUCH_INTERACTION_START
    // Fires on every touch including OpenGL game canvas
    public void onTouchDetected() {
        if (captureManager == null) return;

        // Wake scanner to fast mode immediately
        if (observerLoop != null) observerLoop.wakeOnTap();

        // Take a screenshot right now and find where the change happened
        captureManager.capture(newFrame -> {
            Bitmap before;
            synchronized (frameLock) {
                before = lastFrame;
            }

            int inferredX = -1;
            int inferredY = -1;

            if (before != null && !before.isRecycled()) {
                int[] coords = findChangedRegionCenter(before, newFrame);
                if (coords != null) {
                    inferredX = coords[0];
                    inferredY = coords[1];
                }
            }

            // Always record a tap — use inferred coords or screen center as fallback
            if (inferredX < 0) {
                inferredX = newFrame.getWidth() / 2;
                inferredY = newFrame.getHeight() / 2;
            }

            // Record gesture
            screenObserver.getGestureRecorder().onTouchDown(inferredX, inferredY);
            screenObserver.getGestureRecorder().onTouchUp(inferredX, inferredY);

            // Record in heatmap + run tap OCR
            final int finalX = inferredX;
            final int finalY = inferredY;
            tapObserver.analyzeAtTap(newFrame, finalX, finalY);

            updateStatus("👆 Tap ~X:" + finalX + " Y:" + finalY + "\n" +
                "Scans: " + screenObserver.getObservations().size() + "\n" +
                "Taps recorded: " + screenObserver.getTouchHeatmap().getTapCount());
        });
    }

    // Called by GameAccessibilityService when exact coords available (UI button taps)
    public void onTapDetected(int x, int y) {
        if (captureManager == null) return;
        if (observerLoop != null) observerLoop.wakeOnTap();

        screenObserver.getGestureRecorder().onTouchDown(x, y);
        screenObserver.getGestureRecorder().onTouchUp(x, y);
        captureManager.captureForTap(x, y, bitmap ->
            tapObserver.analyzeAtTap(bitmap, x, y)
        );
    }

    // Compares two frames in 60px blocks, returns center of most-changed region
    private int[] findChangedRegionCenter(Bitmap before, Bitmap after) {
        try {
            if (before.getWidth() != after.getWidth() ||
                before.getHeight() != after.getHeight()) return null;

            int w = before.getWidth();
            int h = before.getHeight();
            int blockSize = 60;
            int maxChange = 0;
            int bestX = -1, bestY = -1;

            for (int bx = 0; bx < w; bx += blockSize) {
                for (int by = 0; by < h; by += blockSize) {
                    int changed = 0;
                    int bw = Math.min(blockSize, w - bx);
                    int bh = Math.min(blockSize, h - by);
                    for (int px = bx; px < bx + bw; px += 6) {
                        for (int py = by; py < by + bh; py += 6) {
                            int p1 = before.getPixel(px, py);
                            int p2 = after.getPixel(px, py);
                            int diff = Math.abs(android.graphics.Color.red(p1) - android.graphics.Color.red(p2))
                                     + Math.abs(android.graphics.Color.green(p1) - android.graphics.Color.green(p2))
                                     + Math.abs(android.graphics.Color.blue(p1) - android.graphics.Color.blue(p2));
                            if (diff > 25) changed++;
                        }
                    }
                    if (changed > maxChange) {
                        maxChange = changed;
                        bestX = bx + bw / 2;
                        bestY = by + bh / 2;
                    }
                }
            }
            return (maxChange > 2 && bestX >= 0) ? new int[]{bestX, bestY} : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void showOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        statusText = overlayView.findViewById(R.id.statusText);
        Button exportBtn = overlayView.findViewById(R.id.btnExport);
        exportBtn.setOnClickListener(v -> exportData());

        View dragHandle = overlayView.findViewById(R.id.dragHandle);
        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            int initialX, initialY;
            float initialTouchX, initialTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int)(event.getRawX() - initialTouchX);
                        params.y = initialY + (int)(event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(overlayView, params);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(overlayView, params);
    }

    private void showStopButton() {
        Button stopBtn = new Button(this);
        stopButtonView = stopBtn;
        stopBtn.setText("⛔ STOP BOT");
        stopBtn.setTextColor(Color.WHITE);
        stopBtn.setTextSize(13f);
        stopBtn.setPadding(20, 10, 20, 10);
        stopBtn.setBackgroundColor(Color.parseColor("#cc0000"));
        stopBtn.setOnClickListener(v -> {
            stopBtn.setText("🛑 Stopping...");
            stopSelf();
        });

        WindowManager.LayoutParams stopParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        stopParams.gravity = Gravity.BOTTOM | Gravity.END;
        stopParams.x = 16;
        stopParams.y = 80;
        windowManager.addView(stopButtonView, stopParams);
    }

    private void exportData() {
        updateStatus("💾 Exporting...");
        new Thread(() -> {
            String path = zipExporter.export(screenObserver, accessibilityDataStore);
            updateStatus(path != null ?
                "✅ Done!\nOCR: " + screenObserver.getObservations().size() +
                "\nTaps: " + screenObserver.getTouchHeatmap().getTapCount() +
                "\nSaved to Documents"
                : "❌ Export failed");
        }).start();
    }

    public void updateStatus(String message) {
        if (statusText != null) statusText.post(() -> statusText.setText(message));
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "ObserverBot", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("ObserverBot Running")
                .setContentText("Tap ⛔ STOP BOT (bottom right) to stop")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        if (observerLoop != null) observerLoop.stop();
        if (captureManager != null) captureManager.stop();
        synchronized (frameLock) {
            if (lastFrame != null && !lastFrame.isRecycled()) lastFrame.recycle();
        }
        if (overlayView != null) try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
        if (stopButtonView != null) try { windowManager.removeView(stopButtonView); } catch (Exception ignored) {}
    }
                }
