package com.observerbot;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Scanner;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

public class ViewerActivity extends Activity {

    private TextView contentText;
    private String zipPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        zipPath = getIntent().getStringExtra("zipPath");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1a1a2e"));

        // Title bar
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setBackgroundColor(Color.parseColor("#0d1b2a"));
        titleBar.setPadding(24, 32, 24, 16);
        titleBar.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("👁 ObserverBot — Data Viewer");
        title.setTextColor(Color.parseColor("#f0c040"));
        title.setTextSize(17f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        titleBar.addView(title);

        if (zipPath != null) {
            TextView zipName = new TextView(this);
            zipName.setText("📦 " + new File(zipPath).getName());
            zipName.setTextColor(Color.parseColor("#aaaaaa"));
            zipName.setTextSize(10f);
            titleBar.addView(zipName);
        }
        root.addView(titleBar);

        // Tab buttons row 1
        LinearLayout row1 = new LinearLayout(this);
        row1.setPadding(8, 6, 8, 2);
        addTabBtn(row1, "📊 Summary",    "summary.json");
        addTabBtn(row1, "📋 OCR",        "ocr_observations.json");
        addTabBtn(row1, "🔥 Heatmap",    "touch_heatmap.json");
        root.addView(row1);

        // Tab buttons row 2
        LinearLayout row2 = new LinearLayout(this);
        row2.setPadding(8, 2, 8, 6);
        addTabBtn(row2, "👆 Gestures",   "gestures.json");
        addTabBtn(row2, "🔀 Transitions","screen_transitions.json");
        addTabBtn(row2, "⏱ Timeline",   "session_timeline.json");
        root.addView(row2);

        // Content
        ScrollView scroll = new ScrollView(this);
        contentText = new TextView(this);
        contentText.setTextColor(Color.parseColor("#e0e0e0"));
        contentText.setTextSize(10.5f);
        contentText.setPadding(20, 16, 20, 40);
        contentText.setTypeface(android.graphics.Typeface.MONOSPACE);
        scroll.addView(contentText);
        root.addView(scroll);

        setContentView(root);
        loadFile("summary.json");
    }

    private void addTabBtn(LinearLayout row, String label, String filename) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(10f);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#16213e"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(3, 3, 3, 3);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> loadFile(filename));
        row.addView(btn);
    }

    private void loadFile(String filename) {
        if (zipPath == null) {
            contentText.setText("❌ No session selected.\nGo back and tap 📊 View on a session.");
            return;
        }

        try {
            ZipFile zf = new ZipFile(new File(zipPath));
            ZipEntry entry = zf.getEntry(filename);

            if (entry == null) {
                contentText.setText("❌ '" + filename + "' not found in this session.");
                zf.close();
                return;
            }

            Scanner sc = new Scanner(zf.getInputStream(entry));
            StringBuilder sb = new StringBuilder();
            sb.append("📂 ").append(filename).append("\n\n");
            while (sc.hasNextLine()) sb.append(sc.nextLine()).append("\n");
            zf.close();

            contentText.setText(sb.toString());

        } catch (Exception e) {
            contentText.setText("❌ Error: " + e.getMessage());
        }
    }
                                               }
