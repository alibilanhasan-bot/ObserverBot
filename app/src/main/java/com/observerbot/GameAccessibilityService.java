package com.observerbot;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GameAccessibilityService extends AccessibilityService {

    private static GameAccessibilityService instance;
    private static AccessibilityDataStore dataStore;

    public static GameAccessibilityService getInstance() { return instance; }

    public static void setDataStore(AccessibilityDataStore store) {
        dataStore = store;
    }

    @Override
    public void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (dataStore == null) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        AccessibilityData data = new AccessibilityData();
        data.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault()).format(new Date());
        data.eventType = AccessibilityEvent.eventTypeToString(event.getEventType());
        data.packageName = event.getPackageName() != null ?
            event.getPackageName().toString() : "";

        // Recursively extract all UI elements
        StringBuilder sb = new StringBuilder();
        extractNodeTree(root, sb, 0);
        data.uiTree = sb.toString();

        dataStore.add(data);
        root.recycle();
    }

    private void extractNodeTree(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null) return;

        String indent = new String(new char[depth * 2]).replace('\0', ' ');
        String text = node.getText() != null ? node.getText().toString() : "";
        String desc = node.getContentDescription() != null ?
            node.getContentDescription().toString() : "";
        String className = node.getClassName() != null ?
            node.getClassName().toString() : "";

        // Get bounds
        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);

        // Only record nodes that have useful content
        if (!text.isEmpty() || !desc.isEmpty()) {
            sb.append(indent)
              .append("[").append(className.contains(".") ?
                  className.substring(className.lastIndexOf('.') + 1) : className).append("]")
              .append(" text='").append(text).append("'")
              .append(" desc='").append(desc).append("'")
              .append(" bounds=").append(bounds.left).append(",").append(bounds.top)
              .append(",").append(bounds.right).append(",").append(bounds.bottom)
              .append(" clickable=").append(node.isClickable())
              .append("\n");
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            extractNodeTree(node.getChild(i), sb, depth + 1);
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }
}
