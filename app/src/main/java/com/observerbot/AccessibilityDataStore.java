package com.observerbot;

import java.util.ArrayList;
import java.util.List;

public class AccessibilityDataStore {

    private final List<AccessibilityData> entries = new ArrayList<>();
    private static final int MAX_ENTRIES = 5000;

    public synchronized void add(AccessibilityData data) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.remove(0); // drop oldest if full
        }
        entries.add(data);
    }

    public synchronized List<AccessibilityData> getAll() {
        return new ArrayList<>(entries);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }
}
