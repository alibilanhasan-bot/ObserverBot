package com.observerbot;

import android.graphics.Bitmap;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ColorSampler {

    // Key screen coordinates to sample colors at
    // These help detect button states without OCR
    private static final int[][] SAMPLE_POINTS = {
        // Format: {x_percent, y_percent} of screen size
        {50, 50},   // center
        {50, 90},   // bottom center (usually nav bar)
        {90, 10},   // top right
        {10, 10},   // top left
        {50, 70},   // lower center (common button area)
        {30, 70},   // lower left button area
        {70, 70},   // lower right button area
        {50, 30},   // upper center
    };

    public static class ColorSample {
        public String timestamp;
        public String screenType;
        public JSONArray samples;

        public ColorSample(String screenType, JSONArray samples) {
            this.screenType = screenType;
            this.samples = samples;
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                Locale.getDefault()).format(new Date());
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("timestamp", timestamp);
                obj.put("screen", screenType);
                obj.put("color_samples", samples);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    public ColorSample sample(Bitmap bitmap, String screenType) {
        try {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            JSONArray samples = new JSONArray();

            for (int[] point : SAMPLE_POINTS) {
                int x = (int)(w * point[0] / 100.0);
                int y = (int)(h * point[1] / 100.0);
                x = Math.min(x, w - 1);
                y = Math.min(y, h - 1);

                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                String hex = String.format("#%02X%02X%02X", r, g, b);
                String colorName = classifyColor(r, g, b);

                JSONObject pt = new JSONObject();
                pt.put("x", x);
                pt.put("y", y);
                pt.put("hex", hex);
                pt.put("r", r);
                pt.put("g", g);
                pt.put("b", b);
                pt.put("color_name", colorName);
                samples.put(pt);
            }

            return new ColorSample(screenType, samples);
        } catch (Exception e) {
            return null;
        }
    }

    // Also sample around a tap point for button color detection
    public JSONObject sampleAtTap(Bitmap bitmap, int tapX, int tapY) {
        try {
            int x = Math.min(tapX, bitmap.getWidth() - 1);
            int y = Math.min(tapY, bitmap.getHeight() - 1);
            int pixel = bitmap.getPixel(x, y);
            int r = Color.red(pixel);
            int g = Color.green(pixel);
            int b = Color.blue(pixel);

            JSONObject obj = new JSONObject();
            obj.put("tap_x", tapX);
            obj.put("tap_y", tapY);
            obj.put("hex", String.format("#%02X%02X%02X", r, g, b));
            obj.put("r", r);
            obj.put("g", g);
            obj.put("b", b);
            obj.put("color_name", classifyColor(r, g, b));
            obj.put("button_type", classifyButton(r, g, b));
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private String classifyColor(int r, int g, int b) {
        if (r > 180 && g < 80 && b < 80) return "RED";
        if (r > 180 && g > 150 && b < 80) return "YELLOW_GOLD";
        if (r < 80 && g > 150 && b < 80) return "GREEN";
        if (r < 80 && g < 80 && b > 150) return "BLUE";
        if (r > 120 && g < 80 && b > 120) return "PURPLE";
        if (r > 200 && g > 200 && b > 200) return "WHITE";
        if (r < 50 && g < 50 && b < 50) return "BLACK";
        if (r > 150 && g > 150 && b > 150) return "GREY_LIGHT";
        if (r > 80 && g > 80 && b > 80) return "GREY_DARK";
        return "MIXED";
    }

    private String classifyButton(int r, int g, int b) {
        // Purple = gem/purchase button - DANGER
        if (r > 100 && g < 80 && b > 100) return "PURPLE_GEM_BUTTON";
        // Gold/Yellow = safe action button
        if (r > 180 && g > 150 && b < 80) return "GOLD_SAFE_BUTTON";
        // Green = claim button
        if (r < 80 && g > 150 && b < 80) return "GREEN_CLAIM_BUTTON";
        // Grey = disabled button
        if (Math.abs(r - g) < 20 && Math.abs(g - b) < 20 && r > 100) return "GREY_DISABLED";
        return "UNKNOWN_BUTTON";
    }
}
