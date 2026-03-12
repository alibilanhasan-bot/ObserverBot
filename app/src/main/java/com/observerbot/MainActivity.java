package com.observerbot;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int OVERLAY_PERMISSION_REQUEST = 1;
    private static final int SCREEN_CAPTURE_REQUEST = 2;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.tvStatus);
        Button startButton = findViewById(R.id.btnStart);
        Button stopButton = findViewById(R.id.btnStop);
        Button accessibilityBtn = findViewById(R.id.btnAccessibility);

        startButton.setOnClickListener(v -> checkAndStart());
        stopButton.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            updateStatus("❌ Stopped");
        });
        accessibilityBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Enable ObserverBot Accessibility Service", Toast.LENGTH_LONG).show();
        });

        updateStatus("Tap START to begin");
    }

    private void checkAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            updateStatus("⚠️ Need overlay permission...");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST);
        } else {
            requestScreenCapture();
        }
    }

    private void requestScreenCapture() {
        updateStatus("⚠️ Need screen capture permission...");
        MediaProjectionManager manager =
            (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                requestScreenCapture();
            } else {
                updateStatus("❌ Overlay permission denied");
            }
        }

        if (requestCode == SCREEN_CAPTURE_REQUEST) {
            if (resultCode == RESULT_OK) {
                // Start service and pass screen capture permission
                Intent serviceIntent = new Intent(this, OverlayService.class);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);
                startForegroundService(serviceIntent);
                updateStatus("✅ ObserverBot running!");
            } else {
                updateStatus("❌ Screen capture denied");
            }
        }
    }

    private void updateStatus(String msg) {
        if (statusText != null) statusText.setText(msg);
    }
}
