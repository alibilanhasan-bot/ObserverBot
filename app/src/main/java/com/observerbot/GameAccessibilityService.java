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

        // Detect tap position from click events — replaces the transparent overlay
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {

            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                Rect bounds = new Rect();
                source.getBoundsInScreen(bounds);
                int tapX = bounds.centerX();
                int tapY = bounds.centerY();
                source.recycle();

                // Tell OverlayService a tap happened at these coordinates
                if (OverlayService.instance != null) {
                    OverlayService.instance.onTapDetected(tapX, tapY);
                }
            }
        }

        // Also detect touch exploration (taps that don't hit a specific view)
        if (eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            // We still capture the screen on this event but don't have exact coords
            // The ObserverLoop handles full-screen OCR anyway
            if (OverlayService.instance != null && OverlayService.instance.observerLoop != null) {
                OverlayService.instance.observerLoop.wakeOnTap();
            }
        }

        // Store full UI tree for all events
        if (dataStore == null) return;

        String packageName = event.getPackageName() != null ?
            event.getPackageName().toString() : "unknown";

        // Only record game and our own app
        if (!packageName.contains("doomsday") &&
            !packageName.contains("lastwar") &&
            !packageName.contains("observerbot") &&
            !packageName.contains("igg")) {
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

        String className = node.getClassName() != null ? node.getClassName().toString() : "";
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
