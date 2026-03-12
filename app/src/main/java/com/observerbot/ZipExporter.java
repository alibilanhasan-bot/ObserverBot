package com.observerbot;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipExporter {

    private final Context context;
    private final DeviceStateCollector deviceStateCollector;

    public ZipExporter(Context context) {
        this.context = context;
        this.deviceStateCollector = new DeviceStateCollector(context);
    }

    public String export(ScreenObserver screenObserver, AccessibilityDataStore accessibilityStore) {
        try {
            File outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            String ts = String.valueOf(System.currentTimeMillis());
            File zipFile = new File(outputDir, "observer_export_" + ts + ".zip");
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));

            List<ObservationData> observations = screenObserver.getObservations();
            List<Bitmap> screenshots = screenObserver.getScreenshots();

            // 1. OCR observations
            JSONArray ocrArray = new JSONArray();
            for (ObservationData obs : observations) ocrArray.put(obs.toJson());
            writeEntry(zos, "ocr_observations.json", ocrArray.toString(2));

            // 2. Accessibility log
            if (accessibilityStore != null) {
                JSONArray accArray = new JSONArray();
                for (AccessibilityData acc : accessibilityStore.getAll()) accArray.put(acc.toJson());
                writeEntry(zos, "accessibility_log.json", accArray.toString(2));
            }

            // 3. Touch heatmap
            TouchHeatmap heatmap = screenObserver.getTouchHeatmap();
            JSONObject heatmapData = new JSONObject();
            heatmapData.put("tap_points", heatmap.getTapPointsJson());
            heatmapData.put("heatmap_grid", heatmap.getHeatmapGrid());
            writeEntry(zos, "touch_heatmap.json", heatmapData.toString(2));

            // 4. Gestures
            writeEntry(zos, "gestures.json",
                screenObserver.getGestureRecorder().toJson().toString(2));

            // 5. Screen transitions
            JSONObject transObj = new JSONObject();
            transObj.put("transitions", screenObserver.getTransitionLogger().getTransitionsJson());
            transObj.put("stats", screenObserver.getTransitionLogger().getStatsJson());
            writeEntry(zos, "screen_transitions.json", transObj.toString(2));

            // 6. Frame diffs
            writeEntry(zos, "frame_diffs.json",
                screenObserver.getFrameDifferencer().getDiffsJson().toString(2));

            // 7. Session timeline
            JSONObject timelineObj = new JSONObject();
            timelineObj.put("events", screenObserver.getSessionTimeline().toJson());
            timelineObj.put("summary", screenObserver.getSessionTimeline().getSummary());
            writeEntry(zos, "session_timeline.json", timelineObj.toString(2));

            // 8. Device state
            JSONObject deviceState = deviceStateCollector.collect();
            deviceState.put("total_ocr_observations", observations.size());
            deviceState.put("total_taps", heatmap.getTapCount());
            deviceState.put("total_gestures", screenObserver.getGestureRecorder().getCount());
            deviceState.put("total_transitions", screenObserver.getTransitionLogger().getTransitionCount());
            deviceState.put("total_frame_diffs", screenObserver.getFrameDifferencer().getDiffCount());
            deviceState.put("total_timeline_events", screenObserver.getSessionTimeline().getEventCount());
            writeEntry(zos, "device_state.json", deviceState.toString(2));

            // 9. Summary
            writeEntry(zos, "summary.json", buildSummary(screenObserver).toString(2));

            // 10. HTML report - open this in any browser
            String htmlReport = HtmlReportExporter.generateReport(
                observations,
                heatmap,
                screenObserver.getTransitionLogger(),
                screenObserver.getSessionTimeline()
            );
            writeEntry(zos, "report.html", htmlReport);

            // 11. Screenshots
            for (int i = 0; i < screenshots.size(); i++) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                screenshots.get(i).compress(Bitmap.CompressFormat.PNG, 85, bos);
                ZipEntry imgEntry = new ZipEntry("screenshots/screenshot_" + i + ".png");
                zos.putNextEntry(imgEntry);
                zos.write(bos.toByteArray());
                zos.closeEntry();
            }

            zos.close();
            return zipFile.getAbsolutePath();

        } catch (Exception e) {
            return null;
        }
    }

    private void writeEntry(ZipOutputStream zos, String name, String content) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes("UTF-8"));
        zos.closeEntry();
    }

    private JSONObject buildSummary(ScreenObserver obs) throws Exception {
        JSONObject s = new JSONObject();
        s.put("total_observations", obs.getObservations().size());
        s.put("total_screenshots", obs.getScreenshots().size());
        s.put("total_taps", obs.getTouchHeatmap().getTapCount());
        s.put("total_gestures", obs.getGestureRecorder().getCount());
        s.put("total_transitions", obs.getTransitionLogger().getTransitionCount());
        s.put("total_frame_diffs", obs.getFrameDifferencer().getDiffCount());
        s.put("total_timeline_events", obs.getSessionTimeline().getEventCount());
        s.put("session_summary", obs.getSessionTimeline().getSummary());

        java.util.Map<String, Integer> screenCounts = new java.util.HashMap<>();
        for (ObservationData observation : obs.getObservations()) {
            screenCounts.merge(observation.screenType, 1, Integer::sum);
        }
        JSONObject screens = new JSONObject();
        for (java.util.Map.Entry<String, Integer> entry : screenCounts.entrySet()) {
            screens.put(entry.getKey(), entry.getValue());
        }
        s.put("screen_type_counts", screens);
        return s;
    }
        }
