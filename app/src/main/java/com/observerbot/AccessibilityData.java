package com.observerbot;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AccessibilityData {

    public String timestamp;
    public String packageName;
    public String eventType;
    public String uiTree;

    public AccessibilityData(String packageName, String eventType, String uiTree) {
        this.packageName = packageName;
        this.eventType = eventType;
        this.uiTree = uiTree;
        this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault()).format(new Date());
    }

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("timestamp", timestamp);
            obj.put("package", packageName);
            obj.put("event_type", eventType);
            obj.put("ui_tree", uiTree);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
