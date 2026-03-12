package com.observerbot;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.JSONArray;

public class ZipExporter {

    private final Context context;

    public ZipExporter(Context context) {
        this.context = context;
    }

    public String export(List<ObservationData> observations, List<Bitmap> screenshots) {
        try {
            File outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            File zipFile = new File(outputDir, "observer_export_" + System.currentTimeMillis() + ".zip");

            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));

            // Write JSON log
            JSONArray jsonArray = new JSONArray();
            for (ObservationData obs : observations) {
                jsonArray.put(obs.toJson());
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
