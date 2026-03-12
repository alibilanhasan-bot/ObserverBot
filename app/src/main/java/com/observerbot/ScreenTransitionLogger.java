package com.observerbot;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScreenTransitionLogger {

    public static class ScreenVisit {
        public String screenName;
        public String enteredAt;
        public String leftAt;
        public long durationMs;
        public String previousScreen;
        public String nextScreen;

        public ScreenVisit(String screenName, String previousScreen) {
            this.screenName = screenName;
            this.previousScreen = previousScreen;
            this.enteredAt = now();
        }

        public void close(String nextScreen) {
            this.nextScreen = nextScreen;
            this.leftAt = now();
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
                this.durationMs = sdf.parse(leftAt).getTime() - sdf.parse(enteredAt).getTime();
            } catch (Exception e) {
                this.durationMs = 0;
            }
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("screen", screenName);
                obj.put("entered_at", enteredAt);
                obj.put("left_at", leftAt != null ? leftAt : "");
                obj.put("duration_ms", durationMs);
                obj.put("from", previousScreen != null ? previousScreen : "START");
                obj.put("to", nextScreen != null ? nextScreen : "CURRENT");
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    private final List<ScreenVisit> visits = new ArrayList<>();
    private ScreenVisit currentVisit = null;
    private String lastScreen = null;
    private final Map<String, Long> totalTimePerScreen = new LinkedHashMap<>();
    private final Map<String, Integer> visitCountPerScreen = new LinkedHashMap<>();

    public void onScreenDetected(String screenName) {
        if (screenName.equals(lastScreen)) return; // no change

        // Close previous visit
        if (currentVisit != null) {
            currentVisit.close(screenName);
            visits.add(currentVisit);

            // Update stats
            totalTimePerScreen.merge(currentVisit.screenName,
                currentVisit.durationMs, Long::sum);
            visitCountPerScreen.merge(currentVisit.screenName, 1, Integer::sum);
        }

        // Start new visit
        currentVisit = new ScreenVisit(screenName, lastScreen);
        lastScreen = screenName;
    }

    public JSONArray getTransitionsJson() {
        JSONArray arr = new JSONArray();
        for (ScreenVisit v : visits) arr.put(v.toJson());
        return arr;
    }

    public JSONObject getStatsJson() {
        try {
            JSONObject stats = new JSONObject();
            stats.put("total_transitions", visits.size());

            JSONObject timePerScreen = new JSONObject();
            for (Map.Entry<String, Long> e : totalTimePerScreen.entrySet()) {
                timePerScreen.put(e.getKey(), e.getValue());
            }
            stats.put("total_time_ms_per_screen", timePerScreen);

            JSONObject countPerScreen = new JSONObject();
            for (Map.Entry<String, Integer> e : visitCountPerScreen.entrySet()) {
                countPerScreen.put(e.getKey(), e.getValue());
            }
            stats.put("visit_count_per_screen", countPerScreen);

            return stats;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault()).format(new Date());
    }

    public int getTransitionCount() { return visits.size(); }
    public void clear() { visits.clear(); currentVisit = null; lastScreen = null; }
}
