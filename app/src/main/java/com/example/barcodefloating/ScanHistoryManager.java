package com.example.barcodefloating;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ScanHistoryManager {

    private static final String PREF_NAME = "scan_history_prefs";
    private static final String KEY_SCANS = "recent_scans";
    private static final int MAX_HISTORY_SIZE = 50;

    private final SharedPreferences prefs;

    public ScanHistoryManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void addScan(String content, String format) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        List<ScanItem> items = getHistory();
        // Insert new item at the top
        items.add(0, new ScanItem(content.trim(), format, System.currentTimeMillis()));

        // Trim if exceeds max size
        if (items.size() > MAX_HISTORY_SIZE) {
            items = items.subList(0, MAX_HISTORY_SIZE);
        }

        saveHistory(items);
    }

    public synchronized List<ScanItem> getHistory() {
        List<ScanItem> list = new ArrayList<>();
        String jsonString = prefs.getString(KEY_SCANS, null);
        if (jsonString != null) {
            try {
                JSONArray array = new JSONArray(jsonString);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    list.add(new ScanItem(
                            obj.optString("content", ""),
                            obj.optString("format", "BARCODE"),
                            obj.optLong("timestamp", System.currentTimeMillis())
                    ));
                }
            } catch (JSONException ignored) {
            }
        }
        return list;
    }

    public synchronized void clearHistory() {
        prefs.edit().remove(KEY_SCANS).apply();
    }

    private void saveHistory(List<ScanItem> items) {
        JSONArray array = new JSONArray();
        for (ScanItem item : items) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("content", item.getContent());
                obj.put("format", item.getFormat());
                obj.put("timestamp", item.getTimestamp());
                array.put(obj);
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(KEY_SCANS, array.toString()).apply();
    }
}
