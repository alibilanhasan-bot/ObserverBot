package com.observerbot;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DeviceStateCollector {

    private final Context context;

    public DeviceStateCollector(Context context) {
        this.context = context;
    }

    public JSONObject collect() {
        try {
            JSONObject obj = new JSONObject();

            // Timestamps in multiple formats
            long now = System.currentTimeMillis();
            obj.put("unix_timestamp", now);
            obj.put("local_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                Locale.getDefault()).format(new Date(now)));
            obj.put("utc_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                Locale.getDefault()) {{
                    setTimeZone(TimeZone.getTimeZone("UTC"));
                }}.format(new Date(now)));

            // Battery
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent battery = context.registerReceiver(null, ifilter);
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                float pct = level * 100f / scale;
                obj.put("battery_percent", Math.round(pct));
                obj.put("battery_charging",
                    status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL);
            }

            // Network
            ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            obj.put("network_connected", netInfo != null && netInfo.isConnected());
            obj.put("network_type", netInfo != null ? netInfo.getTypeName() : "NONE");

            // Device info
            obj.put("device_model", Build.MODEL);
            obj.put("android_version", Build.VERSION.RELEASE);

            return obj;

        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
