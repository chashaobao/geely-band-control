package com.garage.watchcontrol;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WatchProtocol {
    static final int PROTOCOL = 1;
    static final int HOME_ACTIONS = 4;
    static final String MIN_PHONE_APP = "0.1.14";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

    private static final Map<String, String> ACTION_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> SWITCH_LABELS = new LinkedHashMap<>();

    static {
        ACTION_LABELS.put("lock", "车锁");
        ACTION_LABELS.put("climate", "空调");
        ACTION_LABELS.put("window_vent", "窗微开");
        ACTION_LABELS.put("trunk", "后备箱");
        ACTION_LABELS.put("find", "寻车");
        ACTION_LABELS.put("window_open", "窗全开");
        ACTION_LABELS.put("heat", "快升温");
        ACTION_LABELS.put("cool", "快降温");
        ACTION_LABELS.put("purify", "净化");
        ACTION_LABELS.put("defrost", "除霜");
        ACTION_LABELS.put("sentry", "哨兵");

        SWITCH_LABELS.put("climate", "空调");
        SWITCH_LABELS.put("heat", "升温");
        SWITCH_LABELS.put("cool", "降温");
        SWITCH_LABELS.put("purify", "净化");
        SWITCH_LABELS.put("defrost", "除霜");
        SWITCH_LABELS.put("sentry", "哨兵");
    }

    private WatchProtocol() {}

    static JSONObject buildRequest(String id, String type, String action, String token,
                                   String controlDirection, String watchVersion) throws JSONException {
        JSONObject request = new JSONObject()
                .put("v", PROTOCOL)
                .put("id", id)
                .put("type", type);
        if ("hello".equals(type)) {
            request.put("bandApp", watchVersion).put("minPhoneApp", MIN_PHONE_APP);
        }
        if (action != null && !action.isEmpty()) request.put("action", action);
        if (token != null && !token.isEmpty()) request.put("token", token);
        if ("prepare".equals(type) && controlDirection != null && !controlDirection.isEmpty()) {
            request.put("controlDirection", controlDirection);
        }
        return request;
    }

    static String compatibilityMessage(JSONObject reply, String watchVersion) {
        if (reply.optInt("v") != PROTOCOL || reply.optInt("protocol") != PROTOCOL) {
            return "请将手机和手表更新到配套版本";
        }
        int[] phone = versionParts(reply.optString("phoneApp"));
        if (phone == null) return "";
        int[] watch = versionParts(watchVersion);
        int[] minimum = versionParts(MIN_PHONE_APP);
        if (watch != null) {
            if (phone[0] > watch[0]) return "请更新手表端到配套版本";
            if (phone[0] < watch[0]) return "请更新手机端到配套版本";
        }
        if (minimum != null) {
            for (int i = 0; i < minimum.length; i++) {
                if (phone[i] < minimum[i]) return "请更新手机端到配套版本";
                if (phone[i] > minimum[i]) break;
            }
        }
        return "";
    }

    static int[] versionParts(String value) {
        if (value == null) return null;
        Matcher matcher = VERSION_PATTERN.matcher(value);
        if (!matcher.matches()) return null;
        return new int[]{
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        };
    }

    static List<String> knownActions(JSONArray ids) {
        List<String> result = new ArrayList<>();
        if (ids == null) return result;
        for (int i = 0; i < ids.length(); i++) {
            String id = ids.optString(i);
            if (ACTION_LABELS.containsKey(id) && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    static boolean isWindow(String action) {
        return "window_vent".equals(action) || "window_open".equals(action);
    }

    static boolean needsDirection(String action, boolean hasDirectionMap) {
        if ("lock".equals(action)) return hasDirectionMap;
        return isWindow(action) || SWITCH_LABELS.containsKey(action);
    }

    static Map<String, String> readControlDirections(JSONObject directions, String lockState) {
        Map<String, String> result = new LinkedHashMap<>();
        if (directions != null) {
            for (String action : ACTION_LABELS.keySet()) {
                String direction = directions.optString(action);
                boolean valid = "lock".equals(action)
                        ? ("lock".equals(direction) || "unlock".equals(direction))
                        : (needsDirection(action, true) && ("open".equals(direction) || "close".equals(direction)));
                if (valid) result.put(action, direction);
            }
        } else if ("locked".equals(lockState) || "unlocked".equals(lockState)) {
            result.put("lock", "locked".equals(lockState) ? "unlock" : "lock");
        }
        return result;
    }

    static String controlLabel(String action, String direction) {
        if ("lock".equals(action)) {
            if ("lock".equals(direction)) return "上锁";
            if ("unlock".equals(direction)) return "解锁";
        }
        if (isWindow(action) && "close".equals(direction)) return "关窗";
        if (SWITCH_LABELS.containsKey(action) && direction != null && !direction.isEmpty()) {
            return ("open".equals(direction) ? "开" : "关") + SWITCH_LABELS.get(action);
        }
        String label = ACTION_LABELS.get(action);
        return label == null ? action : label;
    }

    static String iconAsset(String action, String direction) {
        if ("lock".equals(action) && "lock".equals(direction)) return "lock-open.png";
        return action + ".png";
    }

    static String rangeText(JSONObject vehicle, String key) {
        if (vehicle == null || !vehicle.has(key) || vehicle.isNull(key)) return "—";
        int value = vehicle.optInt(key, -1);
        return value >= 0 ? String.valueOf(value) : "—";
    }

    static int validPercent(JSONObject vehicle, String key) {
        if (vehicle == null || !vehicle.has(key) || vehicle.isNull(key)) return -1;
        int value = vehicle.optInt(key, -1);
        return value >= 0 && value <= 100 ? value : -1;
    }
}
