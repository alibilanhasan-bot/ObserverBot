package com.observerbot;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ObservationData {

    public String timestamp;
    public String screenType;
    public String rawOcrText;
    public ImportantUI ui;

    public static class ImportantUI {
        public String donationCount;
        public String vipClaimReady;
        public String claimButtonCount;
        public String apPoints;
        public String donationTimer;
        public String coordinates;
        public String resourceLevel;
        public String troopSlots;
        public String afkReady;
        public String collectAllReady;
    }

    public ObservationData(String screenType, String rawOcrText) {
        this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault()).format(new Date());
        this.screenType = screenType;
        this.rawOcrText = rawOcrText;
        this.ui = new ImportantUI();
    }

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("timestamp", timestamp);
            obj.put("screen", screenType);
            obj.put("ocr_text", rawOcrText);
            JSONObject uiObj = new JSONObject();
            uiObj.put("donation_count",    ui.donationCount    != null ? ui.donationCount    : "");
            uiObj.put("vip_claim_ready",   ui.vipClaimReady    != null ? ui.vipClaimReady    : "");
            uiObj.put("claim_buttons",     ui.claimButtonCount != null ? ui.claimButtonCount : "");
            uiObj.put("ap_points",         ui.apPoints         != null ? ui.apPoints         : "");
            uiObj.put("donation_timer",    ui.donationTimer    != null ? ui.donationTimer    : "");
            uiObj.put("coordinates",       ui.coordinates      != null ? ui.coordinates      : "");
            uiObj.put("resource_level",    ui.resourceLevel    != null ? ui.resourceLevel    : "");
            uiObj.put("troop_slots",       ui.troopSlots       != null ? ui.troopSlots       : "");
            uiObj.put("afk_ready",         ui.afkReady         != null ? ui.afkReady         : "");
            uiObj.put("collect_all_ready", ui.collectAllReady  != null ? ui.collectAllReady  : "");
            obj.put("ui", uiObj);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
