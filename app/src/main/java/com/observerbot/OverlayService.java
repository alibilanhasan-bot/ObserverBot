package com.observerbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
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
    private View tapDetectorView;
    private TextView statusText;

    private ScreenObserver screenObserver;
    private TapObserver tapObserver;
    private ObserverLoop observerLoop;
    private ScreenCaptureManager captureManager;
    private ZipExporter zipExporter;
    private AccessibilityDataStore accessibilityDataStore;

    private static final String CHANNEL_ID = "ObserverBotChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());

        accessibilityDataStore = new AccessibilityDataStore();
        GameAccessibilityService.setDataStore(accessibilityDataStore);

        screenObserver = new ScreenObserver(this);
        tapObserver = new TapObserver(screenObserver);
        zipExporter = new ZipExporter(this);

        showOverlay();
        showTapDetector();
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
        updateStatus("👁 Observing everything...\nTap anywhere on game");
    }

    private void showTapDetector() {
        tapDetectorView = new View(this);
        tapDetectorView.setBackgroundColor(0x00000000);

        WindowManager.LayoutParams tapParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSPARENT
        );

        tapDetectorView.setOnTouchListener((v, event) -> {
            if (captureManager != null) {
                int tapX = (int) event.getRawX();
                int tapY = (int) event.getRawY();

                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    screenObserver.getGestureRecorder().onTouchDown(tapX, tapY);
                    captureManager.captureForTap(tapX, tapY, bitmap ->
                        tapObserver.analyzeAtTap(bitmap, tapX, tapY)
                    );
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    screenObserver.getGestureRecorder().onTouchUp(tapX, tapY);
                }
            }
            return false;
        });

        windowManager.addView(tapDetectorView, tapParams);
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
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

        Button stopBtn = overlayView.findViewById(R.id.emergencyStop);
        stopBtn.setOnClickListener(v -> stopSelf());

        makeDraggable(overlayView, params);
        windowManager.addView(overlayView, params);
    }

    private void exportData() {
        updateStatus("💾 Exporting all data...");
        new Thread(() -> {
            String path = zipExporter.export(screenObserver, accessibilityDataStore);
            if (path != null) {
                updateStatus(
                    "✅ Export done!\n" +
                    "📊 OCR: " + screenObserver.getObservations().size() + "\n" +
                    "👆 Taps: " + screenObserver.getTouchHeatmap().getTapCount() + "\n" +
                    "🔀 Transitions: " + screenObserver.getTransitionLogger().getTransitionCount() + "\n" +
                    "📁 Saved to Documents"
                );
            } else {
                updateStatus("❌ Export failed");
            }
        }).start();
    }

    private void makeDraggable(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
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
                        windowManager.updateViewLayout(view, params);
                        return true;
                }
                return false;
            }
        });
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
                .setContentText("Collecting all data...")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (observerLoop != null) observerLoop.stop();
        if (overlayView != null) windowManager.removeView(overlayView);
        if (tapDetectorView != null) windowManager.removeView(tapDetectorView);
    }
            }
