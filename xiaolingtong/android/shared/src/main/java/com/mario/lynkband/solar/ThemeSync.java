package com.mario.lynkband.solar;

import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

/** A landscape choice keeps its original timestamp and author when relayed to the other device. */
public final class ThemeSync {
    private ThemeSync() {}

    private static boolean validScene(String scene) {
        return "SKY".equals(scene) || "RIDGE".equals(scene) || "CITY".equals(scene)
                || "COAST".equals(scene) || "ALPINE".equals(scene);
    }

    private static long revision(SharedPreferences prefs, String key) {
        // On upgrade a previously selected theme outranks a fresh installation's default.
        return prefs.getLong(key + ".revision", prefs.contains(key) ? 1L : 0L);
    }

    public static synchronized void select(SharedPreferences prefs, String key, String scene, boolean phone) {
        if (!validScene(scene)) return;
        long next = Math.max(System.currentTimeMillis(), revision(prefs, key) + 1);
        save(prefs, key, scene, next, phone);
    }

    private static void save(SharedPreferences prefs, String key, String scene, long time, boolean phone) {
        prefs.edit().putString(key, scene).putLong(key + ".revision", time)
                .putBoolean(key + ".fromPhone", phone).apply();
    }

    public static synchronized JSONObject snapshot(SharedPreferences prefs, String key, boolean phone) {
        String scene = prefs.getString(key, "SKY");
        try {
            return new JSONObject().put("scene", validScene(scene) ? scene : "SKY")
                    .put("revision", revision(prefs, key))
                    .put("fromPhone", prefs.getBoolean(key + ".fromPhone", phone));
        } catch (JSONException invalidConstantKey) { throw new AssertionError(invalidConstantKey); }
    }

    public static synchronized boolean merge(SharedPreferences prefs, String key, boolean phone, JSONObject peer) {
        if (peer == null || !(peer.opt("scene") instanceof String scene) || !validScene(scene)
                || !(peer.opt("fromPhone") instanceof Boolean fromPhone)) return false;
        Object rev = peer.opt("revision");
        if (!(rev instanceof Long || rev instanceof Integer)) return false;
        long time = ((Number) rev).longValue();
        if (time < 0 || time > 253402300799999L) return false;
        long local = revision(prefs, key);
        int order = Long.compare(time, local);
        if (order == 0) order = Boolean.compare(fromPhone, prefs.getBoolean(key + ".fromPhone", phone));
        if (order == 0) order = scene.compareTo(prefs.getString(key, "SKY"));
        if (order <= 0) return false;
        save(prefs, key, scene, time, fromPhone);
        return true;
    }
}
