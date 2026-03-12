package com.observerbot;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GestureRecorder {

    public enum GestureType {
        TAP, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT, LONG_PRESS, DRAG
    }

    public static class GestureEvent {
        public GestureType type;
        public int startX, startY;
        public int endX, endY;
        public long durationMs;
        public float speedPxPerMs;
        public String timestamp;
        public String screenType;

        public GestureEvent(GestureType type, int startX, int startY,
                           int endX, int endY, long durationMs, String screenType) {
            this.type = type;
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.durationMs = durationMs;
            this.screenType = screenType;
            float dist = (float) Math.sqrt(
                Math.pow(endX - startX, 2) + Math.pow(endY - startY, 2));
            this.speedPxPerMs = durationMs > 0 ? dist / durationMs : 0;
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                Locale.getDefault()).format(new Date());
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("type", type.name());
                obj.put("start_x", startX);
                obj.put("start_y", startY);
                obj.put("end_x", endX);
                obj.put("end_y", endY);
                obj.put("duration_ms", durationMs);
                obj.put("speed_px_per_ms", speedPxPerMs);
                obj.put("screen", screenType);
                obj.put("timestamp", timestamp);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    private final List<GestureEvent> events = new ArrayList<>();
    private int downX, downY;
    private long downTime;
    private String currentScreen = "UNKNOWN";
    private static final int SWIPE_MIN_DISTANCE = 50;
    private static final long LONG_PRESS_MS = 500;

    public void setCurrentScreen(String screen) {
        this.currentScreen = screen;
    }

    public void onTouchDown(int x, int y) {
        downX = x;
        downY = y;
        downTime = System.currentTimeMillis();
    }

    public void onTouchUp(int x, int y) {
        long duration = System.currentTimeMillis() - downTime;
        int dx = x - downX;
        int dy = y - downY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        GestureType type;
        if (dist < SWIPE_MIN_DISTANCE) {
            type = duration >= LONG_PRESS_MS ? GestureType.LONG_PRESS : GestureType.TAP;
        } else {
            if (Math.abs(dx) > Math.abs(dy)) {
                type = dx > 0 ? GestureType.SWIPE_RIGHT : GestureType.SWIPE_LEFT;
            } else {
                type = dy > 0 ? GestureType.SWIPE_DOWN : GestureType.SWIPE_UP;
            }
        }

        events.add(new GestureEvent(type, downX, downY, x, y, duration, currentScreen));
    }

    public JSONArray toJson() {
        JSONArray arr = new JSONArray();
        for (GestureEvent e : events) arr.put(e.toJson());
        return arr;
    }

    public int getCount() { return events.size(); }
    public void clear() { events.clear(); }
}
