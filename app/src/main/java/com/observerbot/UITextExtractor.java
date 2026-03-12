package com.observerbot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UITextExtractor {

    public static void extract(String text, ObservationData.ImportantUI ui) {

        // Donation count e.g. "30/30" or "0/30"
        Matcher donationMatcher = Pattern.compile("(\\d+)/30").matcher(text);
        if (donationMatcher.find()) {
            ui.donationCount = donationMatcher.group(0);
        }

        // Donation timer e.g. "Next donation chance: 00:19:51"
        Matcher timerMatcher = Pattern.compile("Next donation chance[:\\s]+(\\d{2}:\\d{2}:\\d{2})").matcher(text);
        if (timerMatcher.find()) {
            ui.donationTimer = timerMatcher.group(1);
        }

        // VIP claim ready
        ui.vipClaimReady = text.contains("Claim Free Daily") ? "YES" : "NO";

        // Count CLAIM buttons
        int claimCount = 0;
        int index = 0;
        while ((index = text.indexOf("CLAIM", index)) != -1) {
            claimCount++;
            index += 5;
        }
        ui.claimButtonCount = String.valueOf(claimCount);

        // Activity Points e.g. "Current AP 40"
        Matcher apMatcher = Pattern.compile("Current AP[:\\s]*(\\d+)").matcher(text);
        if (apMatcher.find()) {
            ui.apPoints = apMatcher.group(1);
        }

        // World map coordinates e.g. "X:336 Y:302"
        Matcher coordMatcher = Pattern.compile("X:(\\d+)\\s+Y:(\\d+)").matcher(text);
        if (coordMatcher.find()) {
            ui.coordinates = "X:" + coordMatcher.group(1) + " Y:" + coordMatcher.group(2);
        }

        // Resource/zombie level e.g. "Level: 7"
        Matcher levelMatcher = Pattern.compile("Level[:\\s]+(\\d+)").matcher(text);
        if (levelMatcher.find()) {
            ui.resourceLevel = levelMatcher.group(1);
        }

        // Troop slots e.g. "0/2"
        Matcher troopMatcher = Pattern.compile("(\\d+/\\d+)\\s*troop",
            Pattern.CASE_INSENSITIVE).matcher(text);
        if (troopMatcher.find()) {
            ui.troopSlots = troopMatcher.group(1);
        }

        // AFK collect ready
        ui.afkReady = text.contains("Auto collect AFK") ? "YES" : "NO";

        // Collect All button present
        ui.collectAllReady = text.contains("Collect All") ? "YES" : "NO";
    }
}
