package com.observerbot;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TouchHeatmap {

    public static class TapPoint {
        public int x, y;
        public String timestamp;
        public String screenType;

        public TapPoint(int x, int y, String screenType) {
            this.x = x;
            this.y = y;
            this.screenType = screenType;
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                Locale.getDefault()).format(new Date());
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("x", x);
                obj.put("y", y);
                obj.put("screen", screenType);
                obj.put("timestamp", timestamp);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    private final List<TapPoint> tapPoints = new ArrayList<>();
    private final int screenWidth;
    private final int screenHeight;
    private static final int GRID_COLS = 20;
    private static final int GRID_ROWS = 36;

    public TouchHeatmap(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public void recordTap(int x, int y, String screenType) {
        tapPoints.add(new TapPoint(x, y, screenType));
    }

    public JSONArray getTapPointsJson() {
        JSONArray arr = new JSONArray();
        for (TapPoint tp : tapPoints) arr.put(tp.toJson());
        return arr;
    }

    // Returns a grid showing tap density - useful for visualizing hotspots
    public JSONObject getHeatmapGrid() {
        int[][] grid = new int[GRID_ROWS][GRID_COLS];
        int cellW = screenWidth / GRID_COLS;
        int cellH = screenHeight / GRID_ROWS;

        for (TapPoint tp : tapPoints) {
            int col = Math.min(tp.x / cellW, GRID_COLS - 1);
            int row = Math.min(tp.y / cellH, GRID_ROWS - 1);
            grid[row][col]++;
        }

        try {
            JSONObject result = new JSONObject();
            result.put("grid_cols", GRID_COLS);
            result.put("grid_rows", GRID_ROWS);
            result.put("total_taps", tapPoints.size());
            result.put("screen_width", screenWidth);
            result.put("screen_height", screenHeight);

            JSONArray rows = new JSONArray();
            for (int r = 0; r < GRID_ROWS; r++) {
                JSONArray cols = new JSONArray();
                for (int c = 0; c < GRID_COLS; c++) {
                    cols.put(grid[r][c]);
                }
                rows.put(cols);
            }
            result.put("grid", rows);
            return result;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public int getTapCount() { return tapPoints.size(); }
    public void clear() { tapPoints.clear(); }
}
