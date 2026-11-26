package com.garage.bandcontrol;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.regex.Pattern;

record BandMessage(String id, String type, CarAction action, String token, ControlDirection direction,
                   String imageHash, int imageIndex, String brand) {
    BandMessage(String id, String type, CarAction action, String token, ControlDirection direction) {
        this(id, type, action, token, direction, "", -1, "");
    }

    static final int MAX_BYTES = 2048;
    private static final Pattern ID = Pattern.compile("[a-zA-Z0-9_-]{6,48}");
    private static final Pattern HASH = Pattern.compile("[a-zA-Z0-9_-]{6,32}");
    private static final int VERSION = 1;

    interface ReplyBuilder {
        JSONObject build() throws JSONException;
    }

    static BandMessage decode(byte[] data) throws JSONException {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) return null;
        JSONObject input = new JSONObject(new String(data, StandardCharsets.UTF_8));
        String id = input.optString("id");
        if (input.optInt("v") != VERSION || !ID.matcher(id).matches()) return null;

        String type = input.optString("type");
        CarAction action = CarAction.fromId(input.optString("action"));
        String token = input.optString("token");
        switch (type) {
            case "hello", "status", "refresh": return new BandMessage(id, type, null, "", null);
            case "prepare":
                if (action == null) return null;
                String desired = input.optString("controlDirection");
                String lockState = input.optString("lockState");
                String windowDirection = input.optString("windowDirection");
                if (!lockState.isEmpty()) {
                    ControlDirection legacy = ControlDirection.forLockState(lockState);
                    if (action != CarAction.LOCK || legacy == null
                            || (!desired.isEmpty() && !legacy.id.equals(desired))) return null;
                    desired = legacy.id;
                }
                if (!windowDirection.isEmpty()) {
                    if (!action.isWindow() || (!desired.isEmpty() && !windowDirection.equals(desired))) return null;
                    desired = windowDirection;
                }
                ControlDirection direction = ControlDirection.fromId(desired);
                if (!desired.isEmpty() && !action.acceptsDirection(direction)) return null;
                return new BandMessage(id, type, action, "", direction, "", -1, input.optString("brand"));
            case "execute":
                return action == null || token.length() != 36 ? null : new BandMessage(id, type, action, token, null);
            case "cancel": return token.length() != 36 ? null : new BandMessage(id, type, null, token, null);
            case "car_image":
                String hash = input.optString("hash");
                int index = input.optInt("index", -1);
                if (!HASH.matcher(hash).matches() || index < 0 || index > 200) return null;
                return new BandMessage(id, type, null, "", null, hash, index, "");
            default: return null;
        }
    }

    static JSONObject reply(String id, String status, String message) throws JSONException {
        return envelope(id, "reply")
                .put("status", status).put("message", message);
    }

    static JSONObject carImageReply(String id, String hash, int index, int total, String data) throws JSONException {
        return envelope(id, "car_image_reply")
                .put("hash", hash).put("index", index).put("total", total).put("data", data);
    }

    static JSONObject helloReply(String id, String phoneApp) throws JSONException {
        return envelope(id, "hello-reply").put("phoneApp", phoneApp).put("protocol", VERSION)
                .put("features", new JSONArray().put("confirm").put("cancel").put("refresh").put("car_image"));
    }

    static String encodeReply(String id, ReplyBuilder builder, Consumer<Exception> onError) {
        try {
            String text = builder.build().toString();
            return text.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES ? text : errorReply(id, "too_large");
        } catch (JSONException | RuntimeException invalid) {
            onError.accept(invalid);
            return errorReply(id, "internal");
        }
    }

    private static String errorReply(String id, String error) {
        return "{\"v\":" + VERSION + ",\"type\":\"reply\",\"id\":" + JSONObject.quote(id)
                + ",\"error\":" + JSONObject.quote(error) + "}";
    }

    private static JSONObject envelope(String id, String type) throws JSONException {
        return new JSONObject().put("v", VERSION).put("type", type).put("id", id);
    }
}
