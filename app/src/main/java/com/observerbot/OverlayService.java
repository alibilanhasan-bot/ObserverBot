package com.observerbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
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
    private View overlayView;       // the small info widget top-left
    private View tapDetectorView;   // invisible full-screen — passes ALL touches through
    private View stopButtonView;    // dedicated stop button bottom-right

    private TextView statusText;

    private ScreenObserver screenObserver;
    private TapObserver tapObserver;
    ObserverLoop observerLoop;
    private ScreenCaptureManager captureManager;
    private ZipExporter zipExporter;
    private AccessibilityDataStore accessibilityDataStore;

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

        // Order matters: tap detector first (bottom layer), then overlay widget, then stop button
        showTapDetector();
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

    // Invisible full-screen view that sees tap coordinates but NEVER blocks touches
    private void showTapDetector() {
        tapDetectorView = new View(this);
        tapDetectorView.setBackgroundColor(0x00000000); // fully transparent

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // KEY FLAGS:
                // FLAG_NOT_FOCUSABLE — doesn't steal keyboard focus
                // FLAG_NOT_TOUCHABLE is NOT set — we need to receive touches
                // But we ALWAYS return false to pass touches through to the game
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSPARENT
        );

        tapDetectorView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                int tapX = (int) event.getRawX();
                int tapY = (int) event.getRawY();

                // Wake scanner and record tap
                if (observerLoop != null) observerLoop.wakeOnTap();

                if (captureManager != null) {
                    screenObserver.getGestureRecorder().onTouchDown(tapX, tapY);
                    captureManager.captureForTap(tapX, tapY, bitmap ->
                        tapObserver.analyzeAtTap(bitmap, tapX, tapY)
                    );
                }
            }

            if (event.getAction() == MotionEvent.ACTION_UP) {
                int tapX = (int) event.getRawX();
                int tapY = (int) event.getRawY();
                screenObserver.getGestureRecorder().onTouchUp(tapX, tapY);
            }

            // CRITICAL: always return false — touch passes straight through to game
            return false;
        });

        windowManager.addView(tapDetectorView, params);
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

        // Drag handle — only this part moves the widget
        View dragHandle = overlayView.findViewById(R.id.dragHandle);
        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            int initialX, initialY;
            float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true; // consume — this is intentional for dragging
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
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

    // Called by GameAccessibilityService as a backup for exact UI button coords
    public void onTapDetected(int x, int y) {
        if (captureManager != null && x >= 0 && y >= 0) {
            captureManager.captureForTap(x, y, bitmap ->
                tapObserver.analyzeAtTap(bitmap, x, y)
            );
        }
    }

    private void exportData() {
        updateStatus("💾 Exporting...");
        new Thread(() -> {
            String path = zipExporter.export(screenObserver, accessibilityDataStore);
            if (path != null) {
                updateStatus(
                    "✅ Done!\n" +
                    "OCR: " + screenObserver.getObservations().size() + "\n" +
                    "Taps: " + screenObserver.getTouchHeatmap().getTapCount() + "\n" +
                    "Saved to Documents"
                );
            } else {
                updateStatus("❌ Export failed");
            }
        }).start();
    }

    public void updateStatus(String message) {
        if (statusText != null) {
            statusText.post(() -> statusText.setText(message));
        }
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
        if (tapDetectorView != null) {
            try { windowManager.removeView(tapDetectorView); } catch (Exception ignored) {}
        }
        if (overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
        }
        if (stopButtonView != null) {
            try { windowManager.removeView(stopButtonView); } catch (Exception ignored) {}
        }
    }
                }
