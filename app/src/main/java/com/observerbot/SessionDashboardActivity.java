package com.observerbot;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Scanner;

public class SessionDashboardActivity extends Activity {

    private LinearLayout sessionListContainer;
    private TextView headerStats;
    private File docsDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        docsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);

        // Root layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1a1a2e"));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Color.parseColor("#0d1b2a"));
        header.setPadding(24, 40, 24, 20);

        TextView title = new TextView(this);
        title.setText("👁 ObserverBot");
        title.setTextColor(Color.parseColor("#f0c040"));
        title.setTextSize(22f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Session Dashboard");
        subtitle.setTextColor(Color.parseColor("#aaaaaa"));
        subtitle.setTextSize(13f);
        header.addView(subtitle);

        headerStats = new TextView(this);
        headerStats.setTextColor(Color.parseColor("#90caf9"));
        headerStats.setTextSize(11f);
        headerStats.setPadding(0, 8, 0, 0);
        header.addView(headerStats);

        root.addView(header);

        // Refresh button
        Button refreshBtn = new Button(this);
        refreshBtn.setText("🔄 Refresh Sessions");
        refreshBtn.setBackgroundColor(Color.parseColor("#16213e"));
        refreshBtn.setTextColor(Color.parseColor("#f0c040"));
        refreshBtn.setPadding(24, 16, 24, 16);
        refreshBtn.setOnClickListener(v -> loadSessions());
        root.addView(refreshBtn);

        // Session list
        ScrollView scroll = new ScrollView(this);
        sessionListContainer = new LinearLayout(this);
        sessionListContainer.setOrientation(LinearLayout.VERTICAL);
        sessionListContainer.setPadding(16, 8, 16, 32);
        scroll.addView(sessionListContainer);
        root.addView(scroll);

        setContentView(root);
        loadSessions();
    }

    private void loadSessions() {
        sessionListContainer.removeAllViews();

        if (docsDir == null || !docsDir.exists()) {
            showEmpty("No storage available");
            return;
        }

        File[] zips = docsDir.listFiles(f ->
            f.getName().startsWith("observer_export_") && f.getName().endsWith(".zip"));

        if (zips == null || zips.length == 0) {
            showEmpty("No sessions recorded yet.\n\nStart ObserverBot, play the game,\nthen tap 💾 EXPORT ZIP on the overlay.");
            headerStats.setText("0 sessions recorded");
            return;
        }

        // Sort newest first
        Arrays.sort(zips, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        long totalSize = 0;
        for (File f : zips) totalSize += f.length();

        headerStats.setText(zips.length + " sessions  •  " +
            formatSize(totalSize) + " total");

        for (int i = 0; i < zips.length; i++) {
            addSessionCard(zips[i], i + 1);
        }
    }

    private void addSessionCard(File zipFile, int index) {
        // Extract session number from filename timestamp
        String name = zipFile.getName();
        String ts = name.replace("observer_export_", "").replace(".zip", "");
        String dateStr = "Unknown date";
        try {
            long millis = Long.parseLong(ts);
            dateStr = new SimpleDateFormat("MMM dd, yyyy  HH:mm:ss",
                Locale.getDefault()).format(new Date(millis));
        } catch (Exception ignored) {}

        // Try to read summary from zip
        String[] summaryData = readSummaryFromZip(zipFile);

        // Card container
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#16213e"));
        card.setPadding(20, 16, 20, 16);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 8, 0, 8);
        card.setLayoutParams(cardParams);

        // Session number + date
        TextView sessionTitle = new TextView(this);
        sessionTitle.setText("📦 Session #" + index);
        sessionTitle.setTextColor(Color.parseColor("#f0c040"));
        sessionTitle.setTextSize(15f);
        sessionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(sessionTitle);

        TextView dateView = new TextView(this);
        dateView.setText("🗓 " + dateStr);
        dateView.setTextColor(Color.parseColor("#cccccc"));
        dateView.setTextSize(12f);
        dateView.setPadding(0, 4, 0, 4);
        card.addView(dateView);

        TextView sizeView = new TextView(this);
        sizeView.setText("💾 " + formatSize(zipFile.length()));
        sizeView.setTextColor(Color.parseColor("#aaaaaa"));
        sizeView.setTextSize(11f);
        card.addView(sizeView);

        // Summary stats from zip
        if (summaryData != null) {
            LinearLayout statsRow = new LinearLayout(this);
            statsRow.setOrientation(LinearLayout.HORIZONTAL);
            statsRow.setPadding(0, 10, 0, 10);

            addMiniStat(statsRow, summaryData[0], "OCR Scans");
            addMiniStat(statsRow, summaryData[1], "Taps");
            addMiniStat(statsRow, summaryData[2], "Transitions");
            addMiniStat(statsRow, summaryData[3], "Events");
            card.addView(statsRow);
        }

        // Button row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 8, 0, 0);

        Button viewBtn = makeCardButton("📊 View", "#3498db");
        Button htmlBtn = makeCardButton("🌐 Report", "#9b59b6");
        Button shareBtn = makeCardButton("📤 Share", "#2ecc71");
        Button deleteBtn = makeCardButton("🗑 Delete", "#e74c3c");

        viewBtn.setOnClickListener(v -> openInViewer(zipFile));
        htmlBtn.setOnClickListener(v -> openHtmlReport(zipFile));
        shareBtn.setOnClickListener(v -> shareZip(zipFile));
        deleteBtn.setOnClickListener(v -> deleteSession(zipFile, card));

        btnRow.addView(viewBtn);
        btnRow.addView(htmlBtn);
        btnRow.addView(shareBtn);
        btnRow.addView(deleteBtn);
        card.addView(btnRow);

        sessionListContainer.addView(card);
    }

    private void addMiniStat(LinearLayout row, String value, String label) {
        LinearLayout stat = new LinearLayout(this);
        stat.setOrientation(LinearLayout.VERTICAL);
        stat.setBackgroundColor(Color.parseColor("#0f3460"));
        stat.setPadding(12, 8, 12, 8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(3, 0, 3, 0);
        stat.setLayoutParams(lp);

        TextView val = new TextView(this);
        val.setText(value);
        val.setTextColor(Color.parseColor("#f0c040"));
        val.setTextSize(18f);
        val.setTypeface(null, android.graphics.Typeface.BOLD);
        val.setGravity(android.view.Gravity.CENTER);
        stat.addView(val);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Color.parseColor("#aaaaaa"));
        lbl.setTextSize(9f);
        lbl.setGravity(android.view.Gravity.CENTER);
        stat.addView(lbl);

        row.addView(stat);
    }

    private Button makeCardButton(String text, String color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(10f);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor(color));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(3, 0, 3, 0);
        btn.setLayoutParams(lp);
        btn.setPadding(4, 8, 4, 8);
        return btn;
    }

    private String[] readSummaryFromZip(File zipFile) {
        try {
            ZipFile zf = new ZipFile(zipFile);
            ZipEntry entry = zf.getEntry("summary.json");
            if (entry == null) { zf.close(); return null; }

            Scanner sc = new Scanner(zf.getInputStream(entry));
            StringBuilder sb = new StringBuilder();
            while (sc.hasNextLine()) sb.append(sc.nextLine());
            zf.close();

            org.json.JSONObject json = new org.json.JSONObject(sb.toString());
            return new String[] {
                String.valueOf(json.optInt("total_observations", 0)),
                String.valueOf(json.optInt("total_taps", 0)),
                String.valueOf(json.optInt("total_transitions", 0)),
                String.valueOf(json.optInt("total_timeline_events", 0))
            };
        } catch (Exception e) {
            return new String[]{"?", "?", "?", "?"};
        }
    }

    private void openInViewer(File zipFile) {
        Intent intent = new Intent(this, ViewerActivity.class);
        intent.putExtra("zipPath", zipFile.getAbsolutePath());
        startActivity(intent);
    }

    private void openHtmlReport(File zipFile) {
        try {
            ZipFile zf = new ZipFile(zipFile);
            ZipEntry entry = zf.getEntry("report.html");
            if (entry == null) {
                Toast.makeText(this, "No HTML report in this session", Toast.LENGTH_SHORT).show();
                zf.close();
                return;
            }

            // Write HTML to temp file and open in browser
            File tempHtml = new File(getCacheDir(), "report_temp.html");
            java.io.InputStream is = zf.getInputStream(entry);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempHtml);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
            fos.close();
            zf.close();

            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".provider", tempHtml);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW);
            browserIntent.setDataAndType(uri, "text/html");
            browserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(browserIntent);

        } catch (Exception e) {
            Toast.makeText(this, "Error opening report: " + e.getMessage(),
                Toast.LENGTH_SHORT).show();
        }
    }

    private void shareZip(File zipFile) {
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".provider", zipFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/zip");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "ObserverBot Session Export");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share session data"));
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing: " + e.getMessage(),
                Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteSession(File zipFile, View card) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Delete Session")
            .setMessage("Delete " + zipFile.getName() + "?\nThis cannot be undone.")
            .setPositiveButton("Delete", (d, w) -> {
                if (zipFile.delete()) {
                    sessionListContainer.removeView(card);
                    Toast.makeText(this, "✅ Deleted", Toast.LENGTH_SHORT).show();
                    loadSessions();
                } else {
                    Toast.makeText(this, "❌ Could not delete", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showEmpty(String message) {
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(Color.parseColor("#aaaaaa"));
        empty.setTextSize(14f);
        empty.setPadding(32, 64, 32, 32);
        empty.setGravity(android.view.Gravity.CENTER);
        sessionListContainer.addView(empty);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024f);
        return String.format("%.1f MB", bytes / (1024f * 1024f));
    }
}
