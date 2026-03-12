package com.observerbot;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipExporter {

    private final Context context;

    public ZipExporter(Context context) {
        this.context = context;
    }

    public String export(java.util.List<ScreenLog> logs, java.util.List<Bitmap> screenshots) {
        try {
            File outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            File zipFile = new File(outputDir, "observer_export_" + System.currentTimeMillis() + ".zip");

            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));

            // Write JSON log
            JSONArray jsonArray = new JSONArray();
            for (ScreenLog log : logs) {
                JSONObject obj = new JSONObject();
                obj.put("screen", log.screenName);
                obj.put("text", log.ocrText);
                obj.put("timestamp", log.timestamp);
                jsonArray.put(obj);
            }
            ZipEntry logEntry = new ZipEntry("screen_log.json");
            zos.putNextEntry(logEntry);
            zos.write(jsonArray.toString(2).getBytes());
            zos.closeEntry();

            // Write screenshots
            for (int i = 0; i < screenshots.size(); i++) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                screenshots.get(i).compress(Bitmap.CompressFormat.PNG, 90, bos);
                ZipEntry imgEntry = new ZipEntry("screenshot_" + i + ".png");
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
}
