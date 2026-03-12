package com.observerbot;

import org.json.JSONObject;

public class AccessibilityData {
    public String timestamp;
    public String eventType;
    public String packageName;
    public String uiTree;

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("timestamp", timestamp);
            obj.put("event_type", eventType);
            obj.put("package", packageName);
            obj.put("ui_tree", uiTree);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
