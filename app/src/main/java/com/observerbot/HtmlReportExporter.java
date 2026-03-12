package com.observerbot;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class HtmlReportExporter {

    public static String generateReport(
            List<ObservationData> observations,
            TouchHeatmap heatmap,
            ScreenTransitionLogger transitions,
            SessionTimeline timeline) {

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>")
            .append("<title>ObserverBot Report</title>")
            .append("<style>")
            .append("body{font-family:Arial,sans-serif;background:#1a1a2e;color:#eee;margin:0;padding:20px;}")
            .append("h1{color:#f0c040;text-align:center;border-bottom:2px solid #f0c040;padding-bottom:10px;}")
            .append("h2{color:#f0c040;margin-top:30px;}")
            .append(".card{background:#16213e;border:1px solid #333;border-radius:8px;padding:16px;margin:16px 0;}")
            .append(".stat{display:inline-block;background:#0f3460;border-radius:6px;padding:10px 20px;margin:6px;text-align:center;}")
            .append(".stat-val{font-size:2em;color:#f0c040;font-weight:bold;display:block;}")
            .append(".stat-lbl{font-size:0.8em;color:#aaa;}")
            .append("table{width:100%;border-collapse:collapse;margin:10px 0;}")
            .append("th{background:#f0c040;color:#1a1a2e;padding:8px;text-align:left;}")
            .append("td{border:1px solid #333;padding:7px;font-size:0.85em;}")
            .append("tr:nth-child(even){background:#0d1b2a;}")
            .append(".screen-badge{display:inline-block;background:#0f3460;border-radius:4px;")
            .append("padding:2px 8px;margin:2px;font-size:0.8em;color:#90caf9;}")
            .append(".timeline-event{border-left:3px solid #f0c040;padding:4px 12px;margin:4px 0;font-size:0.85em;}")
            .append(".grid-cell{display:inline-block;width:18px;height:18px;margin:1px;border-radius:2px;}")
            .append("</style></head><body>")
            .append("<h1>👁 ObserverBot — Session Report</h1>");

        // Stats overview
        html.append("<div class='card'>")
            .append("<h2>📊 Session Overview</h2>");

        html.append("<div class='stat'><span class='stat-val'>").append(observations.size())
            .append("</span><span class='stat-lbl'>OCR Scans</span></div>");
        html.append("<div class='stat'><span class='stat-val'>").append(heatmap.getTapCount())
            .append("</span><span class='stat-lbl'>Total Taps</span></div>");
        html.append("<div class='stat'><span class='stat-val'>").append(transitions.getTransitionCount())
            .append("</span><span class='stat-lbl'>Transitions</span></div>");
        html.append("<div class='stat'><span class='stat-val'>").append(timeline.getEventCount())
            .append("</span><span class='stat-lbl'>Timeline Events</span></div>");
        html.append("</div>");

        // Screen type breakdown
        html.append("<div class='card'><h2>📱 Screens Detected</h2>");
        java.util.Map<String, Integer> screenCounts = new java.util.LinkedHashMap<>();
        for (ObservationData obs : observations) {
            screenCounts.merge(obs.screenType, 1, Integer::sum);
        }
        for (java.util.Map.Entry<String, Integer> e : screenCounts.entrySet()) {
            html.append("<div class='screen-badge'>").append(e.getKey())
                .append(" × ").append(e.getValue()).append("</div>");
        }
        html.append("</div>");

        // Touch heatmap visual
        html.append("<div class='card'><h2>🔥 Touch Heatmap</h2>");
        try {
            JSONObject grid = heatmap.getHeatmapGrid();
            JSONArray rows = grid.getJSONArray("grid");
            int maxVal = 1;
            for (int r = 0; r < rows.length(); r++) {
                JSONArray cols = rows.getJSONArray(r);
                for (int c = 0; c < cols.length(); c++) {
                    maxVal = Math.max(maxVal, cols.getInt(c));
                }
            }
            for (int r = 0; r < rows.length(); r++) {
                JSONArray cols = rows.getJSONArray(r);
                for (int c = 0; c < cols.length(); c++) {
                    int val = cols.getInt(c);
                    int intensity = maxVal > 0 ? (int)(val * 255f / maxVal) : 0;
                    String color = String.format("#%02X%02X00", intensity, Math.max(0, 100 - intensity/2));
                    html.append("<div class='grid-cell' style='background:").append(color)
                        .append(";' title='").append(val).append(" taps'></div>");
                }
                html.append("<br>");
            }
        } catch (Exception e) {
            html.append("Heatmap data unavailable");
        }
        html.append("</div>");

        // Recent OCR observations
        html.append("<div class='card'><h2>📋 Recent OCR Observations</h2>")
            .append("<table><tr><th>Time</th><th>Screen</th><th>Claims</th><th>Donations</th><th>Text Preview</th></tr>");
        int start = Math.max(0, observations.size() - 50);
        for (int i = start; i < observations.size(); i++) {
            ObservationData obs = observations.get(i);
            String preview = obs.rawOcrText.length() > 60 ?
                obs.rawOcrText.substring(0, 60).replace("\n", " ") + "..." :
                obs.rawOcrText.replace("\n", " ");
            html.append("<tr>")
                .append("<td>").append(obs.timestamp).append("</td>")
                .append("<td>").append(obs.screenType).append("</td>")
                .append("<td>").append(obs.ui.claimButtonCount != null ? obs.ui.claimButtonCount : "-").append("</td>")
                .append("<td>").append(obs.ui.donationCount != null ? obs.ui.donationCount : "-").append("</td>")
                .append("<td>").append(preview).append("</td>")
                .append("</tr>");
        }
        html.append("</table></div>");

        // Screen transitions
        html.append("<div class='card'><h2>🔀 Screen Transitions</h2>");
        try {
            JSONObject stats = transitions.getStatsJson();
            JSONObject counts = stats.getJSONObject("visit_count_per_screen");
            html.append("<table><tr><th>Screen</th><th>Visits</th></tr>");
            java.util.Iterator<String> keys = counts.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                html.append("<tr><td>").append(key).append("</td><td>")
                    .append(counts.getInt(key)).append("</td></tr>");
            }
            html.append("</table>");
        } catch (Exception e) {
            html.append("Transition data unavailable");
        }
        html.append("</div>");

        // Session timeline (last 30 events)
        html.append("<div class='card'><h2>⏱ Session Timeline (last 30 events)</h2>");
        try {
            JSONArray events = timeline.toJson();
            int tStart = Math.max(0, events.length() - 30);
            for (int i = tStart; i < events.length(); i++) {
                JSONObject ev = events.getJSONObject(i);
                html.append("<div class='timeline-event'>")
                    .append("<strong>").append(ev.getString("timestamp")).append("</strong> — ")
                    .append(ev.getString("description"))
                    .append("</div>");
            }
        } catch (Exception e) {
            html.append("Timeline unavailable");
        }
        html.append("</div>");

        html.append("</body></html>");
        return html.toString();
    }
}
