package com.observerbot;

import android.graphics.Bitmap;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FrameDifferencer {

    public static class FrameDiff {
        public String timestamp;
        public String screenType;
        public float changePercent;
        public JSONArray changedRegions;

        public FrameDiff(String screenType, float changePercent, JSONArray regions) {
            this.screenType = screenType;
            this.changePercent = changePercent;
            this.changedRegions = regions;
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                Locale.getDefault()).format(new Date());
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("timestamp", timestamp);
                obj.put("screen", screenType);
                obj.put("change_percent", changePercent);
                obj.put("changed_regions", changedRegions);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    private Bitmap lastFrame = null;
    private final List<FrameDiff> diffs = new ArrayList<>();
    private static final int BLOCK_SIZE = 40;
    private static final int PIXEL_THRESHOLD = 30;

    public FrameDiff diff(Bitmap current, String screenType) {
        if (lastFrame == null || lastFrame.getWidth() != current.getWidth()) {
            lastFrame = current.copy(current.getConfig(), false);
            return null;
        }

        int w = current.getWidth();
        int h = current.getHeight();
        int totalBlocks = 0;
        int changedBlocks = 0;
        JSONArray regions = new JSONArray();

        for (int bx = 0; bx < w; bx += BLOCK_SIZE) {
            for (int by = 0; by < h; by += BLOCK_SIZE) {
                int blockW = Math.min(BLOCK_SIZE, w - bx);
                int blockH = Math.min(BLOCK_SIZE, h - by);
                int changed = 0;
                int total = 0;

                for (int px = bx; px < bx + blockW; px += 4) {
                    for (int py = by; py < by + blockH; py += 4) {
                        int oldPixel = lastFrame.getPixel(px, py);
                        int newPixel = current.getPixel(px, py);
                        int diff = Math.abs(Color.red(oldPixel) - Color.red(newPixel))
                                 + Math.abs(Color.green(oldPixel) - Color.green(newPixel))
                                 + Math.abs(Color.blue(oldPixel) - Color.blue(newPixel));
                        if (diff > PIXEL_THRESHOLD) changed++;
                        total++;
                    }
                }

                totalBlocks++;
                if (total > 0 && (float) changed / total > 0.3f) {
                    changedBlocks++;
                    try {
                        JSONObject region = new JSONObject();
                        region.put("x", bx);
                        region.put("y", by);
                        region.put("w", blockW);
                        region.put("h", blockH);
                        regions.put(region);
                    } catch (Exception ignored) {}
                }
            }
        }

        float changePercent = totalBlocks > 0 ? (float) changedBlocks / totalBlocks * 100f : 0f;
        lastFrame = current.copy(current.getConfig(), false);

        FrameDiff frameDiff = new FrameDiff(screenType, changePercent, regions);
        if (changePercent > 1f) diffs.add(frameDiff);
        return frameDiff;
    }

    public JSONArray getDiffsJson() {
        JSONArray arr = new JSONArray();
        for (FrameDiff d : diffs) arr.put(d.toJson());
        return arr;
    }

    public int getDiffCount() { return diffs.size(); }
    public void reset() { lastFrame = null; diffs.clear(); }
}
