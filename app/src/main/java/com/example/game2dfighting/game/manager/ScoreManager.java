package com.example.game2dfighting.game.manager;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScoreManager {
    private static final String PREF = "scores_pref";
    private static final String KEY = "scores_json";

    public static class ScoreEntry {
        public int score;
        public long durationMs;
        public long ts;
        public String levelName; // NEW: Easy / Normal / Hard (hoặc Level1/2/3)

        public ScoreEntry(int score, long durationMs, long ts, String levelName) {
            this.score = score;
            this.durationMs = durationMs;
            this.ts = ts;
            this.levelName = levelName;
        }
    }

    // Save điểm, truyền thêm tên level
    public static void saveRun(Context ctx, int score, long durationMs, long ts, String levelName) {
        List<ScoreEntry> list = loadAll(ctx);
        list.add(new ScoreEntry(score, durationMs, ts, levelName));
        persist(ctx, list);
    }

    public static List<ScoreEntry> loadAll(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String json = sp.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            List<ScoreEntry> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new ScoreEntry(
                        o.optInt("score", 0),
                        o.optLong("durationMs", 0),
                        o.optLong("ts", 0),
                        o.optString("levelName", "Level1") // default
                ));
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static void persist(Context ctx, List<ScoreEntry> list) {
        JSONArray arr = new JSONArray();
        try {
            for (ScoreEntry s : list) {
                JSONObject o = new JSONObject();
                o.put("score", s.score);
                o.put("durationMs", s.durationMs);
                o.put("ts", s.ts);
                o.put("levelName", s.levelName);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply();
    }

    /** Sắp xếp: điểm ↓; nếu bằng điểm → thời gian ↑; nếu còn bằng → ts ↓ */
    public static List<ScoreEntry> sorted(Context ctx) {
        List<ScoreEntry> list = loadAll(ctx);
        Collections.sort(list, (a, b) -> {
            if (b.score != a.score) return Integer.compare(b.score, a.score);
            if (a.durationMs != b.durationMs) return Long.compare(a.durationMs, b.durationMs);
            return Long.compare(b.ts, a.ts);
        });
        return list;
    }

    public static String formatDuration(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        long r = s % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", m, r);
    }

    public static String formatDate(long ts) {
        Date d = new Date(ts);
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(d);
    }
}
