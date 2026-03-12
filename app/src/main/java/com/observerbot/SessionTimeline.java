package com.observerbot;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionTimeline {

    public enum EventType {
        SESSION_START,
        SCREEN_DETECTED,
        TAP,
        SWIPE,
        SCREEN_TRANSITION,
        OCR_SCAN,
        EXPORT,
        SCANNER_PAUSED,
        SCANNER_RESUMED,
        SESSION_END
    }

    public static class TimelineEvent {
        public String timestamp;
        public EventType type;
        public String description;
        public JSONObject data;

        public TimelineEvent(EventType type, String description, JSONObject data) {
            this.type = type;
            this.description = description;
            this.data = data;
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                Locale.getDefault()).format(new Date());
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("timestamp", timestamp);
                obj.put("event", type.name());
                obj.put("description", description);
                if (data != null) obj.put("data", data);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    private final List<TimelineEvent> events = new ArrayList<>();
    private final String sessionStartTime;

    public SessionTimeline() {
        sessionStartTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault()).format(new Date());
        log(EventType.SESSION_START, "ObserverBot session started", null);
    }

    public void log(EventType type, String description, JSONObject data) {
        events.add(new TimelineEvent(type, description, data));
    }

    public void logScreen(String screenName) {
        try {
            JSONObject d = new JSONObject();
            d.put("screen", screenName);
            log(EventType.SCREEN_DETECTED, "Screen: " + screenName, d);
        } catch (Exception ignored) {}
    }

    public void logTap(int x, int y, String screenName) {
        try {
            JSONObject d = new JSONObject();
            d.put("x", x);
            d.put("y", y);
            d.put("screen", screenName);
            log(EventType.TAP, "Tap at X:" + x + " Y:" + y + " on " + screenName, d);
        } catch (Exception ignored) {}
    }

    public void logTransition(String from, String to) {
        try {
            JSONObject d = new JSONObject();
            d.put("from", from);
            d.put("to", to);
            log(EventType.SCREEN_TRANSITION, from + " → " + to, d);
        } catch (Exception ignored) {}
    }

    public void logPaused(String screenName, int scanCount) {
        try {
            JSONObject d = new JSONObject();
            d.put("screen", screenName);
            d.put("scan_count", scanCount);
            log(EventType.SCANNER_PAUSED, "Scanner paused on " + screenName, d);
        } catch (Exception ignored) {}
    }

    public void logResumed(int x, int y) {
        try {
            JSONObject d = new JSONObject();
            d.put("wake_tap_x", x);
            d.put("wake_tap_y", y);
            log(EventType.SCANNER_RESUMED, "Scanner resumed by tap at X:" + x + " Y:" + y, d);
        } catch (Exception ignored) {}
    }

    public JSONArray toJson() {
        JSONArray arr = new JSONArray();
        for (TimelineEvent e : events) arr.put(e.toJson());
        return arr;
    }

    public JSONObject getSummary() {
        try {
            JSONObject s = new JSONObject();
            s.put("session_start", sessionStartTime);
            s.put("session_end", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                Locale.getDefault()).format(new Date()));
            s.put("total_events", events.size());

            // Count each event type
            JSONObject counts = new JSONObject();
            for (EventType type : EventType.values()) {
                int count = 0;
                for (TimelineEvent e : events) {
                    if (e.type == type) count++;
                }
                if (count > 0) counts.put(type.name(), count);
            }
            s.put("event_counts", counts);
            return s;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public int getEventCount() { return events.size(); }
}
