package com.observerbot;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class GameAccessibilityService extends AccessibilityService {

    private static AccessibilityDataStore dataStore;

    public static void setDataStore(AccessibilityDataStore store) {
        dataStore = store;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        int eventType = event.getEventType();
        String packageName = event.getPackageName() != null ?
            event.getPackageName().toString() : "unknown";

        if (packageName.equals("com.observerbot") || packageName.equals("android")) return;

        // Fires on ANY touch anywhere — including game canvas
        // No coordinates available but we infer them via frame diff in OverlayService
        if (eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            if (OverlayService.instance != null) {
                OverlayService.instance.onTouchDetected();
            }
        }

        // Fires on specific UI button taps — gives us exact coordinates
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                Rect bounds = new Rect();
                source.getBoundsInScreen(bounds);
                int x = bounds.centerX();
                int y = bounds.centerY();
                source.recycle();
                if (OverlayService.instance != null) {
                    OverlayService.instance.onTapDetected(x, y);
                }
            }
        }

        // Record UI tree
        if (dataStore == null) return;
        if (packageName.equals("com.android.systemui")) return;
        if (eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            eventType != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        String uiTree = root != null ? buildUiTree(root, 0) : "(no root)";
        if (root != null) root.recycle();
        dataStore.add(new AccessibilityData(packageName,
            AccessibilityEvent.eventTypeToString(eventType), uiTree));
    }

    private String buildUiTree(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 8) return "";
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(depth);
        String cls = node.getClassName() != null ? node.getClassName().toString() : "";
        String text = node.getText() != null ? node.getText().toString() : "";
        String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        sb.append(indent)
          .append(cls.contains(".") ? cls.substring(cls.lastIndexOf('.') + 1) : cls);
        if (!text.isEmpty()) sb.append(" text=\"").append(text).append("\"");
        if (!desc.isEmpty()) sb.append(" desc=\"").append(desc).append("\"");
        if (node.isClickable()) sb.append(" [clickable]");
        sb.append(" ").append(bounds.toShortString()).append("\n");
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { sb.append(buildUiTree(child, depth + 1)); child.recycle(); }
        }
        return sb.toString();
    }

    @Override
    public void onInterrupt() {}
            }
