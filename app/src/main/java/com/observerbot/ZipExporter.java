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

    public String export(
            List<ObservationData> observations,
            List<Bitmap> screenshots,
            AccessibilityDataStore accessibilityStore) {
        try {
            File outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            String timestamp = String.valueOf(System.currentTimeMillis());
            File zipFile = new File(outputDir, "observer_export_" + timestamp + ".zip");

            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));

            // 1. OCR observations JSON
            JSONArray ocrArray = new JSONArray();
            for (ObservationData obs : observations) {
                ocrArray.put(obs.toJson());
            }
            writeZipEntry(zos, "ocr_observations.json", ocrArray.toString(2));

            // 2. Accessibility UI tree JSON
            if (accessibilityStore != null) {
                JSONArray accArray = new JSONArray();
                for (AccessibilityData acc : accessibilityStore.getAll()) {
                    accArray.put(acc.toJson());
                }
                writeZipEntry(zos, "accessibility_log.json", accArray.toString(2));
            }

            // 3. Device state snapshot
            JSONObject deviceState = deviceStateCollector.collect();
            deviceState.put("total_ocr_observations", observations.size());
            deviceState.put("total_accessibility_events",
                accessibilityStore != null ? accessibilityStore.size() : 0);
            deviceState.put("total_screenshots", screenshots.size());
            writeZipEntry(zos, "device_state.json", deviceState.toString(2));

            // 4. Summary stats
            JSONObject summary = buildSummary(observations);
            writeZipEntry(zos, "summary.json", summary.toString(2));

            // 5. Screenshots
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

    private void writeZipEntry(ZipOutputStream zos, String name, String content) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes("UTF-8"));
        zos.closeEntry();
    }

    private JSONObject buildSummary(List<ObservationData> observations) throws Exception {
        JSONObject summary = new JSONObject();
        java.util.Map<String, Integer> screenCounts = new java.util.HashMap<>();

        for (ObservationData obs : observations) {
            screenCounts.merge(obs.screenType, 1, Integer::sum);
        }

        JSONObject screens = new JSONObject();
        for (java.util.Map.Entry<String, Integer> entry : screenCounts.entrySet()) {
            screens.put(entry.getKey(), entry.getValue());
        }

        summary.put("total_observations", observations.size());
        summary.put("screen_type_counts", screens);
        if (!observations.isEmpty()) {
            summary.put("first_observation", observations.get(0).timestamp);
            summary.put("last_observation", observations.get(observations.size()-1).timestamp);
        }

        return summary;
    }
                }
