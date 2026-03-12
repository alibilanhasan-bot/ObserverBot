package com.observerbot;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class GameAccessibilityService extends AccessibilityService {

    private static AccessibilityDataStore dataStore;
    // Store package name so we can discover it automatically
    private static String detectedGamePackage = "";

    public static void setDataStore(AccessibilityDataStore store) {
        dataStore = store;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        int eventType = event.getEventType();
        String packageName = event.getPackageName() != null ?
            event.getPackageName().toString() : "unknown";

        // Skip our own overlay and pure system UI — record everything else
        if (packageName.equals("com.observerbot") ||
            packageName.equals("com.android.systemui") ||
            packageName.equals("android")) {
            return;
        }

        // Auto-detect the game package — it will be whatever app is in foreground
        // that isn't a launcher or system app
        if (!packageName.contains("launcher") &&
            !packageName.contains("settings") &&
            !packageName.contains("inputmethod") &&
            detectedGamePackage.isEmpty()) {
            detectedGamePackage = packageName;
            android.util.Log.d("ObserverBot", "Detected game package: " + packageName);
        }

        // Detect tap position from click events — replaces transparent overlay
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {

            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                Rect bounds = new Rect();
                source.getBoundsInScreen(bounds);
                int tapX = bounds.centerX();
                int tapY = bounds.centerY();
                source.recycle();

                if (OverlayService.instance != null) {
                    OverlayService.instance.onTapDetected(tapX, tapY);
                }
            }
        }

        // Wake the scanner on any touch
        if (eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            if (OverlayService.instance != null &&
                OverlayService.instance.observerLoop != null) {
                OverlayService.instance.observerLoop.wakeOnTap();
            }
        }

        // Record UI tree — for ALL apps except system (helps map full navigation)
        if (dataStore == null) return;

        // Only record content events, skip pure window focus changes etc
        if (eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            eventType != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            return;
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        String uiTree = rootNode != null ? buildUiTree(rootNode, 0) : "(no root)";
        if (rootNode != null) rootNode.recycle();

        String eventTypeName = AccessibilityEvent.eventTypeToString(eventType);
        dataStore.add(new AccessibilityData(packageName, eventTypeName, uiTree));
    }

    private String buildUiTree(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 8) return "";
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(depth);

        String className = node.getClassName() != null ?
            node.getClassName().toString() : "";
        String text = node.getText() != null ? node.getText().toString() : "";
        String desc = node.getContentDescription() != null ?
            node.getContentDescription().toString() : "";
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        sb.append(indent)
          .append(className.contains(".") ?
              className.substring(className.lastIndexOf('.') + 1) : className);
        if (!text.isEmpty()) sb.append(" text=\"").append(text).append("\"");
        if (!desc.isEmpty()) sb.append(" desc=\"").append(desc).append("\"");
        if (node.isClickable()) sb.append(" [clickable]");
        sb.append(" ").append(bounds.toShortString());
        sb.append("\n");

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                sb.append(buildUiTree(child, depth + 1));
                child.recycle();
            }
        }
        return sb.toString();
    }

    @Override
    public void onInterrupt() {}
            }
            
