package com.observerbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
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
    private TextView statusText;

    private static final String CHANNEL_ID = "ObserverBotChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());
        showOverlay();
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
        Button stopBtn = overlayView.findViewById(R.id.emergencyStop);

        stopBtn.setOnClickListener(v -> stopSelf());

        makeDraggable(overlayView, params);

        windowManager.addView(overlayView, params);
        updateStatus("ObserverBot Ready");
    }

    private void makeDraggable(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
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
                .setContentText("Screen observer is active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null) windowManager.removeView(overlayView);
    }
          }
