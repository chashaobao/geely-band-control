package com.garage.watchcontrol;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WatchActivity extends Activity implements BluetoothPhoneClient.Listener {
    private static final int BG = Color.rgb(11, 18, 30);
    private static final int PANEL = Color.rgb(20, 31, 48);
    private static final int META = Color.rgb(21, 34, 54);
    private static final int META_BORDER = Color.rgb(37, 59, 90);
    private static final int ORB_ACTIVE = Color.rgb(24, 98, 232);
    private static final int ORB_ACTIVE_BORDER = Color.rgb(79, 146, 255);
    private static final int ORB_READY = Color.rgb(29, 42, 61);
    private static final int ORB_READY_BORDER = Color.rgb(50, 73, 105);
    private static final int ORB_PAUSED = Color.rgb(20, 29, 43);
    private static final int ORB_PAUSED_BORDER = Color.rgb(33, 48, 68);
    private static final int ORB_UNASSIGNED = Color.rgb(18, 26, 38);
    private static final int ORB_UNASSIGNED_BORDER = Color.rgb(30, 42, 59);
    private static final int INK = Color.rgb(246, 249, 255);
    private static final int SUB_INK = Color.rgb(200, 215, 235);
    private static final int MUTED = Color.rgb(142, 166, 197);
    private static final int MINT = Color.rgb(158, 233, 206);
    private static final int ELECTRIC = Color.rgb(111, 231, 165);
    private static final int FUEL = Color.rgb(116, 183, 255);
    private static final int TRACK = Color.rgb(74, 88, 95);
    private static final String DEFAULT_VEHICLE_NAME = "我的座驾";

    private static final long STATUS_INTERVAL_MS = 12_000;
    private static final long CONTROL_STATUS_INTERVAL_MS = 1_500;
    private static final long PHONE_CONFIRM_INTERVAL_MS = 2_500;
    private static final long TOAST_DURATION_MS = 2_800;
    private static final long HELLO_TIMEOUT_MS = 2_000;
    private static final long CONFIRM_TIMEOUT_MS = 13_000;
    private static final long FAILURE_PULSE_GAP_MS = 200;
    private static final int BT_PERMISSION_REQUEST = 41;
    private static final String PREFS_NAME = "watch_garage";
    private static final String SNAPSHOT_KEY = "vehicle-snapshot";
    private static final String CAR_IMAGE_HASH_KEY = "car-image-hash";
    private static final String CAR_IMAGE_FILE_KEY = "car-image-file";
    private static final String EVENTS_KEY = "connection-events";
    private static final String HAPTICS_KEY = "control-haptics";
    private static final String PHONE_ADDR_KEY = "preferred-phone-address";

    private record PendingRequest(String id, String type, String action, String controlDirection) {}
    private record EventEntry(long ts, String event, String id, String type, String detail) {}

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> assetCache = new HashMap<>();
    private final List<EventEntry> events = new ArrayList<>();

    private BluetoothPhoneClient client;
    private SharedPreferences prefs;
    private Vibrator vibrator;

    private FrameLayout homeShell;
    private VerticalPagerView homePager;
    private View pagerDot0;
    private View pagerDot1;
    private ScrollView settingsScroll;
    private LinearLayout confirmScreen;
    private FrameLayout toastBox;
    private TextView toastView;

    private LinearLayout heroBlock;
    private LinearLayout splitRow;
    private TextView vehicleNameView;
    private TextView totalHeroView;
    private TextView subrangeView;
    private TextView electricText;
    private TextView fuelText;
    private View electricFill;
    private View fuelFill;
    private View connectionDot;
    private TextView statusSummaryView;
    private ImageView carImageView;
    private TextView controlsRefreshView;
    private TextView emptyView;
    private LinearLayout gridContainer;

    private ImageView confirmIconView;
    private TextView confirmTitleView;
    private Switch hapticsSwitch;
    private TextView phoneTargetView;
    private LinearLayout diagnosticsBox;

    private boolean visible;
    private boolean roundScreen;
    private boolean showSplitDetail;
    private boolean settingsOpen;
    private boolean hapticsEnabled = true;
    private boolean handshakeComplete;
    private boolean linked;
    private boolean hasReply;
    private boolean phoneConfirmation;
    private boolean controlPending;
    private boolean cachedVehicle;
    private boolean controlDirectionsSupported;

    private String mode = "";
    private String vehicleName = DEFAULT_VEHICLE_NAME;
    private String modeLabel = "待连接";
    private String motionLabel = "状态未知";
    private String updatedLabel = "等待车况";
    private String compatibilityNotice = "";
    private String controlNotice = "";
    private String confirmAction = "";
    private String confirmToken = "";
    private String confirmTitle = "";
    private String confirmIconAsset = "";
    private String storedSnapshot = "";
    private String carImageHash = "";
    private String failedCarImageHash = "";
    private String imageSyncHash = "";
    private int imageSyncTotal;
    private final List<String> imageSyncChunks = new ArrayList<>();

    private List<String> mapped = new ArrayList<>();
    private List<String> available = new ArrayList<>();
    private Map<String, String> controlDirections = new LinkedHashMap<>();
    private JSONObject blockedControls = new JSONObject();
    private List<String> features;
    private PendingRequest pending;
    private int sequence;

    private final Runnable pollTask = this::syncStatus;
    private final Runnable toastTask = this::clearToast;
    private final Runnable requestTimeoutTask = this::onRequestTimeout;
    private final Runnable confirmTimeoutTask = this::onConfirmTimeout;
    private final Runnable secondPulseTask = this::vibrateShort;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        vibrator = getSystemService(Vibrator.class);
        hapticsEnabled = !"off".equals(prefs.getString(HAPTICS_KEY, "on"));
        roundScreen = getResources().getConfiguration().isScreenRound();
        loadEvents();

        client = new BluetoothPhoneClient(this, main, this);
        client.setPreferredAddress(prefs.getString(PHONE_ADDR_KEY, ""));

        setContentView(buildUi());
        loadCachedCarImage();
        loadSnapshot();
        renderAll();
    }

    @Override protected void onStart() {
        super.onStart();
        visible = true;
        ensureBluetoothAndConnect();
    }

    @Override protected void onStop() {
        visible = false;
        stopSession();
        super.onStop();
    }

    @Override public void onBackPressed() {
        if (!confirmTitle.isEmpty()) {
            cancelConfirmation();
            return;
        }
        if (settingsOpen) {
            settingsOpen = false;
            renderScreens();
            return;
        }
        if (homePager.currentPage() > 0) {
            homePager.setCurrentPage(0, true);
            return;
        }
        super.onBackPressed();
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (homeShell != null && homeShell.getVisibility() == View.VISIBLE
                && homePager != null && homePager.handleCrownMotion(event)) {
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BT_PERMISSION_REQUEST && client.hasPermission() && visible) {
            client.connect();
        }
    }

    private void ensureBluetoothAndConnect() {
        if (!visible) return;
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BT_PERMISSION_REQUEST);
            modeLabel = "待授权";
            renderMeta();
            return;
        }
        if (!client.isConnected()) {
            modeLabel = "连接中";
            renderMeta();
            client.connect();
        } else if (handshakeComplete) {
            syncStatus();
        }
    }

    private void stopSession() {
        main.removeCallbacks(pollTask);
        main.removeCallbacks(toastTask);
        main.removeCallbacks(requestTimeoutTask);
        main.removeCallbacks(confirmTimeoutTask);
        main.removeCallbacks(secondPulseTask);
        cancelConfirmation();
        pending = null;
        handshakeComplete = false;
        linked = false;
        client.disconnect("hidden");
    }

    @Override public void onConnected(String deviceName) {
        if (!visible) return;
        recordEvent("bt_connected", null, deviceName);
        handshakeComplete = false;
        sendRequest("hello", null, null, null);
    }

    @Override public void onMessage(byte[] data) {
        if (!visible || pending == null) return;
        if (data.length > WatchFraming.MAX_FRAME_BYTES) {
            recordEvent("drop", pending, "too_large");
            return;
        }
        try {
            JSONObject reply = new JSONObject(new String(data, StandardCharsets.UTF_8));
            if (!pending.id().equals(reply.optString("id"))) return;
            handleReply(pending, reply);
        } catch (JSONException ignored) {
        }
    }

    @Override public void onDisconnected(String reason) {
        if (!visible) return;
        disconnect(reason, "closed", pending);
    }

    @Override public void onError(String message) {
        if (!visible) return;
        modeLabel = "未连接";
        showToast(message);
        renderAll();
    }

    private void syncStatus() {
        if (!visible || !compatibilityNotice.isEmpty()) return;
        if (!client.isConnected()) {
            ensureBluetoothAndConnect();
            schedulePoll();
            return;
        }
        if (!handshakeComplete) {
            sendRequest("hello", null, null, null);
            return;
        }
        sendRequest("status", null, null, null);
    }

    private void schedulePoll() {
        main.removeCallbacks(pollTask);
        if (!visible || pending != null || !confirmTitle.isEmpty() || !compatibilityNotice.isEmpty()) return;
        long delay = phoneConfirmation ? PHONE_CONFIRM_INTERVAL_MS
                : controlPending ? CONTROL_STATUS_INTERVAL_MS : STATUS_INTERVAL_MS;
        main.postDelayed(pollTask, delay);
    }

    private boolean isBusy() {
        return pending != null && !"status".equals(pending.type()) && !"car_image".equals(pending.type());
    }

    private void triggerRefresh() {
        if (isBusy() || !confirmTitle.isEmpty()) return;
        if (controlPending) {
            showToast(controlNotice.isEmpty() ? "正在同步车况" : controlNotice);
            syncStatus();
            return;
        }
        if (!client.isConnected()) {
            ensureBluetoothAndConnect();
            return;
        }
        if (supportsRequest("refresh")) showToast("正在刷新车况…");
        sendRequest("refresh", null, null, null);
    }

    private void sendRequest(String type, String action, String token, String direction) {
        if (!visible || !confirmTitle.isEmpty()) return;
        if (!"hello".equals(type) && !handshakeComplete) return;
        if (!supportsRequest(type)) {
            showToast("手机端不支持此功能，请更新到配套版本");
            if ("prepare".equals(type) || "execute".equals(type)) vibrateFailure();
            return;
        }
        if (pending != null && (!"status".equals(pending.type()) && !"car_image".equals(pending.type())
                || "status".equals(type) || "car_image".equals(type))) return;
        clearPending();
        main.removeCallbacks(pollTask);

        String id = Long.toString(System.currentTimeMillis(), 36) + "_" + Long.toString(++sequence, 36);
        PendingRequest req = new PendingRequest(id, type, action, direction);
        pending = req;
        renderMeta();

        long timeout = "hello".equals(type) ? HELLO_TIMEOUT_MS : "refresh".equals(type) ? 13_000 : 8_000;
        main.postDelayed(requestTimeoutTask, timeout);
        try {
            String payload = "car_image".equals(type)
                    ? WatchProtocol.buildCarImageRequest(id, imageSyncHash, imageSyncChunks.size()).toString()
                    : WatchProtocol.buildRequest(id, type, action, token, direction, BuildConfig.VERSION_NAME).toString();
            recordEvent("send", req, "");
            client.send(payload, () -> {
                if (pending != req) return;
                recordEvent("send_failed", req, "");
                if ("hello".equals(type)) finishHello(null);
                else if ("car_image".equals(type)) {
                    clearPending();
                    resetImageSync();
                    schedulePoll();
                } else disconnect("发送失败，请检查蓝牙连接", "send_failed", req);
            });
        } catch (JSONException invalid) {
            clearPending();
        }
    }

    private void onRequestTimeout() {
        if (pending == null) return;
        PendingRequest req = pending;
        recordEvent("timeout", req, "");
        if ("hello".equals(req.type())) {
            finishHello(null);
        } else if ("car_image".equals(req.type())) {
            clearPending();
            resetImageSync();
            schedulePoll();
        } else {
            disconnect("手机回复超时，请检查连接", "timeout", req);
        }
    }

    private void clearPending() {
        main.removeCallbacks(requestTimeoutTask);
        pending = null;
    }

    private void finishHello(JSONObject reply) {
        clearPending();
        if (reply != null) {
            JSONArray featArray = reply.optJSONArray("features");
            List<String> list = new ArrayList<>();
            if (featArray != null) {
                for (int i = 0; i < featArray.length(); i++) list.add(featArray.optString(i));
            }
            features = list;
            compatibilityNotice = WatchProtocol.compatibilityMessage(reply, BuildConfig.VERSION_NAME);
            if (!compatibilityNotice.isEmpty()) {
                modeLabel = "版本不符";
                showToast(compatibilityNotice);
                renderAll();
                return;
            }
        }
        handshakeComplete = true;
        syncStatus();
    }

    private boolean supportsRequest(String type) {
        if ("status".equals(type) || "hello".equals(type)) return true;
        String feature = switch (type) {
            case "refresh" -> "refresh";
            case "prepare", "execute" -> "confirm";
            case "cancel" -> "cancel";
            default -> type;
        };
        return features == null || features.contains(feature);
    }

    private void handleReply(PendingRequest request, JSONObject reply) {
        String err = reply.optString("error");
        if (reply.optInt("v") == WatchProtocol.PROTOCOL && "reply".equals(reply.optString("type")) && !err.isEmpty()) {
            String message = "too_large".equals(err) ? "手机回复超过 2048 B"
                    : "internal".equals(err) ? "手机端内部错误" : "手机返回未知错误";
            recordEvent("recv", request, err);
            disconnect(message, "reply_error", request);
            return;
        }
        if ("hello".equals(request.type())) {
            if ("hello-reply".equals(reply.optString("type")) && reply.has("v") && reply.has("protocol")) {
                recordEvent("recv", request, "hello-reply");
                finishHello(reply);
            }
            return;
        }
        if ("car_image".equals(request.type())) {
            if (reply.optInt("v") != WatchProtocol.PROTOCOL || !"car_image_reply".equals(reply.optString("type"))) return;
            clearPending();
            String hash = reply.optString("hash");
            int index = reply.optInt("index", -1);
            int total = reply.optInt("total", 0);
            String data = reply.optString("data");
            if (!hash.equals(imageSyncHash) || index != imageSyncChunks.size() || total <= 0 || data.isEmpty()) {
                recordEvent("car_img_fail", request, "bad_chunk");
                resetImageSync();
                schedulePoll();
                return;
            }
            imageSyncChunks.add(data);
            if (imageSyncChunks.size() < total) {
                sendRequest("car_image", null, null, null);
                return;
            }
            String completedHash = imageSyncHash;
            String joined = String.join("", imageSyncChunks);
            resetImageSync();
            saveAndDisplayCarImage(completedHash, joined);
            schedulePoll();
            return;
        }
        if (reply.optInt("v") != WatchProtocol.PROTOCOL || !"reply".equals(reply.optString("type"))) return;
        String replyMode = reply.optString("mode");
        if (!"demo".equals(replyMode) && !"live".equals(replyMode)) return;

        recordEvent("recv", request, "");
        boolean firstReply = !linked || !mode.equals(replyMode);
        clearPending();
        linked = true;
        hasReply = true;
        mode = replyMode;
        modeLabel = "demo".equals(mode) ? "演示" : "已连接";
        phoneConfirmation = reply.optBoolean("phoneConfirmation", false);
        String previousNotice = controlNotice;
        boolean wasControlPending = controlPending;
        controlNotice = reply.optString("controlNotice");
        blockedControls = reply.optJSONObject("blocked") != null ? reply.optJSONObject("blocked") : new JSONObject();
        controlPending = reply.optBoolean("controlPending", false) || phoneConfirmation;
        if (phoneConfirmation) controlNotice = "请在手机确认银河操作";

        List<String> replyAvailable = WatchProtocol.knownActions(reply.optJSONArray("available"));
        List<String> replyMapped = reply.has("mapped")
                ? WatchProtocol.knownActions(reply.optJSONArray("mapped"))
                : new ArrayList<>(replyAvailable);
        if (replyMapped.size() > WatchProtocol.HOME_ACTIONS) {
            replyMapped = new ArrayList<>(replyMapped.subList(0, WatchProtocol.HOME_ACTIONS));
        }
        boolean partialUpdate = controlPending && !firstReply && replyMapped.size() < mapped.size()
                && mapped.containsAll(replyMapped);
        if (!partialUpdate) mapped = replyMapped;

        JSONObject reportedDirections = reply.optJSONObject("controlDirections");
        controlDirectionsSupported = reportedDirections != null;
        cachedVehicle = reply.optBoolean("vehicleCached", false);
        Map<String, String> directions = cachedVehicle
                ? Map.of()
                : WatchProtocol.readControlDirections(reportedDirections, reply.optString("lockState"));

        available = new ArrayList<>();
        if (!controlPending) {
            for (String action : replyAvailable) {
                if (mapped.contains(action) && ("demo".equals(mode) || directions.containsKey(action)
                        || (!WatchProtocol.needsDirection(action, controlDirectionsSupported)
                        && !("lock".equals(action) && cachedVehicle)))) {
                    available.add(action);
                }
            }
        }
        if (controlPending && !firstReply && !cachedVehicle) {
            controlDirections.putAll(directions);
        } else {
            controlDirections = new LinkedHashMap<>(directions);
        }

        if (reply.has("vehicleName")) {
            String nextName = reply.optString("vehicleName").trim();
            vehicleName = nextName.isEmpty() ? DEFAULT_VEHICLE_NAME : nextName;
        }
        JSONObject vehicle = reply.optJSONObject("vehicle");
        applyVehicle(vehicle, cachedVehicle);
        if ("live".equals(mode) && vehicle != null && vehicle.optLong("updatedAt") > 0) {
            try {
                JSONObject snapshot = new JSONObject(vehicle.toString());
                snapshot.put("mapped", new JSONArray(mapped));
                snapshot.put("vehicleName", vehicleName);
                String text = snapshot.toString();
                if (!text.equals(storedSnapshot)) {
                    storedSnapshot = text;
                    prefs.edit().putString(SNAPSHOT_KEY, text).apply();
                }
            } catch (JSONException ignored) {
            }
        }

        String status = reply.optString("status");
        String replyAction = reply.optString("action");
        String token = reply.optString("token");
        String message = reply.optString("message");
        boolean directionChanged = request.controlDirection() != null
                && !request.controlDirection().equals(directions.get(request.action()));

        if ("prepare".equals(request.type()) && "confirm".equals(status) && request.action().equals(replyAction)
                && !directionChanged && token.length() == 36 && !message.isEmpty()) {
            clearToast();
            confirmAction = request.action();
            confirmToken = token;
            confirmIconAsset = WatchProtocol.iconAsset(request.action(), directions.get(request.action()));
            confirmTitle = message;
            main.removeCallbacks(confirmTimeoutTask);
            main.postDelayed(confirmTimeoutTask, CONFIRM_TIMEOUT_MS);
        } else if (!"status".equals(request.type())) {
            boolean dispatched = "execute".equals(request.type()) && "dispatched".equals(status);
            String toastMessage = message;
            if (controlPending && (phoneConfirmation || !"dispatched".equals(status))) {
                toastMessage = controlNotice.isEmpty() ? "正在同步车况" : controlNotice;
            } else if (dispatched && "live".equals(mode)) {
                toastMessage = "指令已发送";
            } else if ("confirm".equals(status) && directionChanged) {
                toastMessage = "按钮状态已变化，请重新选择";
            }
            showToast(toastMessage);
            if (dispatched) vibrateShort();
            else if ("prepare".equals(request.type()) || "execute".equals(request.type())) vibrateFailure();
        } else if (firstReply || !previousNotice.equals(controlNotice) || wasControlPending != controlPending) {
            clearToast();
            if (!controlPending || phoneConfirmation) showToast(controlNotice);
        }
        renderAll();
        String imgHash = reply.optString("carImageHash");
        int imgChunks = reply.optInt("carImageChunks", 0);
        boolean checkImage = "status".equals(request.type()) || "refresh".equals(request.type());
        if (checkImage && supportsRequest("car_image") && !controlPending && confirmTitle.isEmpty()
                && !imgHash.isEmpty() && imgChunks > 0 && !imgHash.equals(carImageHash) && !imgHash.equals(failedCarImageHash)) {
            if (!imgHash.equals(imageSyncHash)) {
                imageSyncHash = imgHash;
                imageSyncTotal = imgChunks;
                imageSyncChunks.clear();
                recordEvent("car_img_start", request, imgHash + "x" + imgChunks);
            }
            sendRequest("car_image", null, null, null);
            return;
        }
        schedulePoll();
    }

    private void onConfirmTimeout() {
        cancelConfirmation();
        showToast("确认已过期，请重新选择");
        vibrateFailure();
    }

    private void selectAction(String action) {
        if (isBusy() || !confirmTitle.isEmpty()) return;
        if (!linked) {
            showToast("请先连接手机");
            vibrateFailure();
            return;
        }
        if (controlPending) {
            showToast(controlNotice.isEmpty() ? "正在同步车况" : controlNotice);
            vibrateFailure();
            syncStatus();
            return;
        }
        if (!mapped.contains(action)) {
            showToast("请在手机核对银河小组件配置");
            vibrateFailure();
            return;
        }
        if ("live".equals(mode) && !available.contains(action)
                && (WatchProtocol.needsDirection(action, controlDirectionsSupported)
                || ("lock".equals(action) && cachedVehicle))) {
            String reason = blockedControls.optString(action);
            String notice = (controlDirectionsSupported || "lock".equals(action))
                    ? (!controlNotice.isEmpty() ? controlNotice : (!reason.isEmpty() ? reason : "请刷新车况，核对按钮状态"))
                    : "请更新手机端后使用此项车控";
            showToast(notice);
            vibrateFailure();
            return;
        }
        clearToast();
        if (supportsRequest("prepare")) vibrateShort();
        sendRequest("prepare", action, null, controlDirections.get(action));
    }

    private void executeConfirmation() {
        if (confirmToken.isEmpty()) return;
        String action = confirmAction;
        String token = confirmToken;
        clearConfirmationState();
        vibrateShort();
        showToast("demo".equals(mode) ? "正在执行演示…" : "正在发送命令…");
        renderScreens();
        sendRequest("execute", action, token, null);
    }

    private void cancelConfirmation() {
        String token = confirmToken;
        clearConfirmationState();
        if (!token.isEmpty()) {
            showToast("已取消操作");
            if (linked && client.isConnected() && supportsRequest("cancel")) {
                try {
                    String id = Long.toString(System.currentTimeMillis(), 36) + "_" + Long.toString(++sequence, 36);
                    String payload = WatchProtocol.buildRequest(id, "cancel", null, token, null, BuildConfig.VERSION_NAME).toString();
                    client.send(payload, () -> {});
                } catch (JSONException ignored) {
                }
            }
        }
        renderScreens();
        schedulePoll();
    }

    private void clearConfirmationState() {
        main.removeCallbacks(confirmTimeoutTask);
        confirmAction = "";
        confirmToken = "";
        confirmTitle = "";
        confirmIconAsset = "";
    }

    private void disconnect(String message, String reason, PendingRequest request) {
        boolean waitingForControl = pending != null && "execute".equals(pending.type());
        boolean controlInterrupted = !confirmTitle.isEmpty()
                || (request != null && ("prepare".equals(request.type()) || "execute".equals(request.type()) || "cancel".equals(request.type())));
        boolean replyError = "reply_error".equals(reason);
        recordEvent("disconnect", request, reason);
        clearPending();
        clearConfirmationState();
        resetImageSync();
        handshakeComplete = false;
        features = null;
        compatibilityNotice = "";
        linked = false;
        phoneConfirmation = false;
        controlPending = false;
        cachedVehicle = false;
        blockedControls = new JSONObject();
        controlDirectionsSupported = false;
        controlDirections.clear();
        modeLabel = "demo".equals(mode) ? "演示离线" : "离线";
        available.clear();
        if (controlInterrupted && !"hidden".equals(reason)) vibrateFailure();
        if (visible && message != null && !message.isEmpty()) {
            String result = "结果未知，请在银河 App 查看";
            showToast(waitingForControl ? (replyError ? message + "，" + result : result) : message);
        }
        renderAll();
        schedulePoll();
    }

    private void applyVehicle(JSONObject vehicle, boolean cached) {
        totalHeroView.setText(WatchProtocol.heroDisplay(vehicle));
        subrangeView.setText(WatchProtocol.subrangeDisplay(vehicle));
        electricText.setText(WatchProtocol.energyDisplay(vehicle, "electricKm", "electricPercent"));
        fuelText.setText(WatchProtocol.energyDisplay(vehicle, "fuelKm", "fuelPercent"));
        updateBar(electricFill, WatchProtocol.validPercent(vehicle, "electricPercent"));
        updateBar(fuelFill, WatchProtocol.validPercent(vehicle, "fuelPercent"));
        String motion = vehicle == null ? "" : vehicle.optString("motion");
        motionLabel = motion.isEmpty() ? "状态未知" : motion;
        String updated = vehicle == null ? "" : vehicle.optString("updatedLabel");
        updatedLabel = updated.isEmpty() ? "等待车况" : (cached ? "上次 " : "") + updated;
    }

    private void updateBar(View fill, int percent) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) fill.getLayoutParams();
        params.weight = Math.max(0, percent);
        fill.setLayoutParams(params);
        fill.setVisibility(percent > 0 ? View.VISIBLE : View.INVISIBLE);
    }

    private void resetImageSync() {
        imageSyncHash = "";
        imageSyncTotal = 0;
        imageSyncChunks.clear();
    }

    private void loadCachedCarImage() {
        String hash = prefs.getString(CAR_IMAGE_HASH_KEY, "");
        String path = prefs.getString(CAR_IMAGE_FILE_KEY, "");
        if (hash.isEmpty() || path.isEmpty()) return;
        File file = new File(path);
        if (!file.isFile()) return;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap != null) {
            carImageHash = hash;
            carImageView.setImageBitmap(bitmap);
        }
    }

    private void saveAndDisplayCarImage(String hash, String base64Text) {
        try {
            byte[] png = Base64.decode(base64Text, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(png, 0, png.length);
            if (bitmap == null) {
                failedCarImageHash = hash;
                recordEvent("car_img_fail", null, "decode_null");
                return;
            }
            File target = new File(getFilesDir(), "car_" + hash + ".png");
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write(png);
            }
            String previousPath = prefs.getString(CAR_IMAGE_FILE_KEY, "");
            if (!previousPath.isEmpty() && !previousPath.equals(target.getAbsolutePath())) {
                //noinspection ResultOfMethodCallIgnored
                new File(previousPath).delete();
            }
            prefs.edit().putString(CAR_IMAGE_HASH_KEY, hash)
                    .putString(CAR_IMAGE_FILE_KEY, target.getAbsolutePath()).apply();
            carImageHash = hash;
            carImageView.setImageBitmap(bitmap);
            recordEvent("car_img_ok", null, hash);
        } catch (IllegalArgumentException | IOException failed) {
            failedCarImageHash = hash;
            recordEvent("car_img_fail", null, failed.getClass().getSimpleName());
        }
    }

    private void loadSnapshot() {
        String raw = prefs.getString(SNAPSHOT_KEY, "");
        if (raw.isEmpty() || hasReply) return;
        try {
            JSONObject snapshot = new JSONObject(raw);
            if (snapshot.optLong("updatedAt") <= 0) return;
            storedSnapshot = raw;
            String savedName = snapshot.optString("vehicleName").trim();
            if (!savedName.isEmpty()) vehicleName = savedName;
            applyVehicle(snapshot, true);
            modeLabel = "离线缓存";
            mapped = WatchProtocol.knownActions(snapshot.optJSONArray("mapped"));
            if (mapped.size() > WatchProtocol.HOME_ACTIONS) {
                mapped = new ArrayList<>(mapped.subList(0, WatchProtocol.HOME_ACTIONS));
            }
        } catch (JSONException ignored) {
        }
    }

    private void loadEvents() {
        try {
            JSONArray array = new JSONArray(prefs.getString(EVENTS_KEY, "[]"));
            for (int i = Math.max(0, array.length() - 20); i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                events.add(new EventEntry(item.getLong("ts"), item.getString("event"),
                        item.optString("id"), item.optString("type"), item.optString("detail")));
            }
        } catch (JSONException ignored) {
            events.clear();
        }
    }

    private void recordEvent(String event, PendingRequest req, String detail) {
        events.add(new EventEntry(System.currentTimeMillis(), event,
                req == null ? "" : req.id(), req == null ? "" : req.type(), detail == null ? "" : detail));
        while (events.size() > 20) events.remove(0);
        JSONArray array = new JSONArray();
        try {
            for (EventEntry entry : events) {
                array.put(new JSONObject().put("ts", entry.ts()).put("event", entry.event())
                        .put("id", entry.id()).put("type", entry.type()).put("detail", entry.detail()));
            }
            prefs.edit().putString(EVENTS_KEY, array.toString()).apply();
        } catch (JSONException ignored) {
        }
        if (settingsOpen) renderDiagnostics();
    }

    private void showToast(String message) {
        if (!visible || message == null || message.isEmpty()) return;
        main.removeCallbacks(toastTask);
        toastView.setText(message);
        toastBox.setVisibility(View.VISIBLE);
        main.postDelayed(toastTask, TOAST_DURATION_MS);
    }

    private void clearToast() {
        main.removeCallbacks(toastTask);
        toastBox.setVisibility(View.GONE);
    }

    private void vibrateShort() {
        if (!visible || !hapticsEnabled || vibrator == null || !vibrator.hasVibrator()) return;
        vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    private void vibrateFailure() {
        if (!visible || !hapticsEnabled || vibrator == null || !vibrator.hasVibrator()) return;
        main.removeCallbacks(secondPulseTask);
        vibrateShort();
        main.postDelayed(secondPulseTask, FAILURE_PULSE_GAP_MS);
    }

    private void renderAll() {
        renderScreens();
        renderMeta();
        renderGrid();
        renderDiagnostics();
    }

    private void renderScreens() {
        boolean showConfirm = !confirmTitle.isEmpty();
        confirmScreen.setVisibility(showConfirm ? View.VISIBLE : View.GONE);
        settingsScroll.setVisibility(!showConfirm && settingsOpen ? View.VISIBLE : View.GONE);
        homeShell.setVisibility(!showConfirm && !settingsOpen ? View.VISIBLE : View.GONE);
        if (showConfirm) {
            confirmTitleView.setText(confirmTitle);
            confirmIconView.setImageBitmap(assetBitmap(confirmIconAsset));
            clearToast();
        } else if (settingsOpen) {
            renderDiagnostics();
            updatePhoneTargetLabel();
            settingsScroll.requestFocus();
        } else {
            homePager.requestFocus();
        }
    }

    private void renderMeta() {
        vehicleNameView.setText(vehicleName);
        String refreshText = phoneConfirmation ? "请在手机确认"
                : controlPending ? "同步中…"
                : pending != null && !"status".equals(pending.type()) && !"car_image".equals(pending.type()) ? "请稍候…"
                : updatedLabel;
        statusSummaryView.setText(String.format(Locale.getDefault(), "%s | %s", modeLabel, motionLabel));
        controlsRefreshView.setText(refreshText);
        boolean online = "已连接".equals(modeLabel) || "演示".equals(modeLabel);
        GradientDrawable dotBg = (GradientDrawable) connectionDot.getBackground();
        dotBg.setColor(online ? MINT : Color.rgb(120, 134, 140));
    }

    private void renderGrid() {
        gridContainer.removeAllViews();
        if (!compatibilityNotice.isEmpty()) {
            emptyView.setText(compatibilityNotice);
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        if (mapped.isEmpty()) {
            emptyView.setText(linked ? "请在手机配置银河卡片" : "连接手机后显示已映射车控");
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);
        int count = mapped.size();
        int orbDp = count <= 1 ? 86 : count == 2 ? 74 : 58;
        int iconDp = count <= 1 ? 58 : count == 2 ? 50 : 40;
        int labelSp = count <= 1 ? 17 : count == 2 ? 16 : 14;
        for (int i = 0; i < count; i += 2) {
            String first = mapped.get(i);
            String second = i + 1 < count ? mapped.get(i + 1) : null;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) rowParams.topMargin = dp(8);
            row.addView(buildOrbTile(first, orbDp, iconDp, labelSp), new LinearLayout.LayoutParams(0, -2, 1));
            if (second != null) {
                LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, -2, 1);
                secondParams.setMarginStart(dp(8));
                row.addView(buildOrbTile(second, orbDp, iconDp, labelSp), secondParams);
            }
            gridContainer.addView(row, rowParams);
        }
    }

    private View buildOrbTile(String action, int orbDp, int iconDp, int labelSp) {
        boolean isAvailable = available.contains(action);
        String direction = controlDirections.get(action);
        boolean isActive = isAvailable && ("close".equals(direction) || ("lock".equals(action) && "unlock".equals(direction)));
        String label = WatchProtocol.controlLabel(action, direction);
        String hint = !linked ? "待连接" : (!isAvailable && !controlPending ? "暂不可用" : "");

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setOnClickListener(view -> selectAction(action));

        FrameLayout orb = new FrameLayout(this);
        GradientDrawable orbBg = new GradientDrawable();
        orbBg.setShape(GradientDrawable.OVAL);
        int fill = isAvailable ? (isActive ? ORB_ACTIVE : ORB_READY) : linked ? ORB_PAUSED : ORB_UNASSIGNED;
        int stroke = isAvailable ? (isActive ? ORB_ACTIVE_BORDER : ORB_READY_BORDER)
                : linked ? ORB_PAUSED_BORDER : ORB_UNASSIGNED_BORDER;
        orbBg.setColor(fill);
        orbBg.setStroke(dp(1), stroke);
        orb.setBackground(orbBg);

        ImageView icon = new ImageView(this);
        icon.setImageBitmap(assetBitmap(WatchProtocol.iconAsset(action, direction)));
        icon.setAlpha(isAvailable ? 1f : 0.62f);
        orb.addView(icon, new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER));
        cell.addView(orb, new LinearLayout.LayoutParams(dp(orbDp), dp(orbDp)));

        TextView labelView = text(label, labelSp, INK, true);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.topMargin = dp(4);
        cell.addView(labelView, labelParams);

        if (!hint.isEmpty()) {
            TextView hintView = text(hint, 10, MUTED, false);
            hintView.setSingleLine(true);
            hintView.setGravity(Gravity.CENTER);
            cell.addView(hintView);
        }
        return cell;
    }

    private void renderDiagnostics() {
        diagnosticsBox.removeAllViews();
        diagnosticsBox.addView(text("连接记录", 13, INK, true));
        if (events.isEmpty()) {
            TextView none = text("暂无记录", 11, MUTED, false);
            none.setPadding(0, dp(4), 0, 0);
            diagnosticsBox.addView(none);
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        int start = Math.max(0, events.size() - 3);
        for (int i = events.size() - 1; i >= start; i--) {
            EventEntry entry = events.get(i);
            StringBuilder line = new StringBuilder(fmt.format(new Date(entry.ts())))
                    .append(' ').append(entry.event());
            if (!entry.type().isEmpty()) line.append(' ').append(entry.type());
            if (!entry.detail().isEmpty()) line.append(' ').append(entry.detail());
            if (!entry.id().isEmpty()) line.append('\n').append(entry.id());
            TextView row = text(line.toString(), 10, MUTED, false);
            row.setPadding(0, dp(4), 0, 0);
            diagnosticsBox.addView(row);
        }
    }

    private void updatePhoneTargetLabel() {
        String addr = client.preferredAddress();
        phoneTargetView.setText(addr.isEmpty() ? "自动连接已配对手机" : "指定设备：" + addr);
    }

    private void choosePhoneDevice() {
        List<BluetoothPhoneClient.PairedDevice> devices = client.bondedDevices();
        if (devices.isEmpty()) {
            showToast("暂无已配对蓝牙设备");
            return;
        }
        String[] labels = new String[devices.size() + 1];
        labels[0] = "自动选择已配对手机";
        for (int i = 0; i < devices.size(); i++) {
            BluetoothPhoneClient.PairedDevice device = devices.get(i);
            labels[i + 1] = device.name() + "\n" + device.address();
        }
        new AlertDialog.Builder(this)
                .setTitle("选择配对手机")
                .setItems(labels, (dialog, index) -> {
                    String selected = index == 0 ? "" : devices.get(index - 1).address();
                    client.setPreferredAddress(selected);
                    prefs.edit().putString(PHONE_ADDR_KEY, selected).apply();
                    updatePhoneTargetLabel();
                    client.disconnect("reconnect");
                    ensureBluetoothAndConnect();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updatePagerDots(int page) {
        updateDot(pagerDot0, page == 0);
        updateDot(pagerDot1, page == 1);
    }

    private void updateDot(View dot, boolean active) {
        int size = dp(active ? 7 : 5);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) dot.getLayoutParams();
        params.width = size;
        params.height = size;
        dot.setLayoutParams(params);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(active ? MINT : Color.rgb(79, 99, 110));
        dot.setBackground(bg);
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(15, 27, 46), Color.rgb(24, 44, 77), Color.rgb(8, 13, 22)}));

        int hPad = dp(roundScreen ? 22 : 14);
        int vPad = dp(roundScreen ? 16 : 14);
        WatchProtocol.RoundHomeMetrics homeMetrics = roundScreen
                ? WatchProtocol.RoundHomeMetrics.forDiameterDp(dialDiameterDp())
                : WatchProtocol.RoundHomeMetrics.rectDefault();

        homeShell = new FrameLayout(this);
        homePager = new VerticalPagerView(this);
        homePager.addView(buildMileagePage(homeMetrics));
        homePager.addView(buildControlsPage(hPad, vPad));
        homePager.setOnPageChangeListener(this::updatePagerDots);
        homeShell.addView(homePager, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout pagerDots = new LinearLayout(this);
        pagerDots.setOrientation(LinearLayout.VERTICAL);
        pagerDots.setGravity(Gravity.CENTER_HORIZONTAL);
        pagerDot0 = new View(this);
        pagerDot1 = new View(this);
        pagerDots.addView(pagerDot0, new LinearLayout.LayoutParams(dp(7), dp(7)));
        LinearLayout.LayoutParams dot1Params = new LinearLayout.LayoutParams(dp(5), dp(5));
        dot1Params.topMargin = dp(6);
        pagerDots.addView(pagerDot1, dot1Params);
        pagerDot0.setOnClickListener(v -> homePager.setCurrentPage(0, true));
        pagerDot1.setOnClickListener(v -> homePager.setCurrentPage(1, true));
        updatePagerDots(0);

        FrameLayout.LayoutParams dotsParams = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL);
        dotsParams.setMarginEnd(dp(10));
        homeShell.addView(pagerDots, dotsParams);

        confirmScreen = new LinearLayout(this);
        confirmScreen.setOrientation(LinearLayout.VERTICAL);
        confirmScreen.setGravity(Gravity.CENTER);
        confirmScreen.setPadding(hPad, dp(28), hPad, dp(28));
        confirmScreen.setVisibility(View.GONE);
        LinearLayout confirmHeader = new LinearLayout(this);
        confirmHeader.setGravity(Gravity.CENTER);
        confirmIconView = new ImageView(this);
        confirmHeader.addView(confirmIconView, new LinearLayout.LayoutParams(dp(24), dp(24)));
        confirmTitleView = text("", 20, INK, true);
        confirmTitleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ctParams = new LinearLayout.LayoutParams(-2, -2);
        ctParams.setMarginStart(dp(8));
        confirmHeader.addView(confirmTitleView, ctParams);
        confirmScreen.addView(confirmHeader, new LinearLayout.LayoutParams(-1, -2));

        TextView confirmBtn = actionButton("确认操作", MINT, Color.rgb(16, 37, 30), true, v -> executeConfirmation());
        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(-1, dp(44));
        cbParams.topMargin = dp(16);
        confirmScreen.addView(confirmBtn, cbParams);

        TextView cancelBtn = actionButton("取消", Color.rgb(38, 54, 61), INK, false, v -> cancelConfirmation());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, dp(44));
        cancelParams.topMargin = dp(8);
        confirmScreen.addView(cancelBtn, cancelParams);

        settingsScroll = new ScrollView(this);
        settingsScroll.setFocusable(true);
        settingsScroll.setFocusableInTouchMode(true);
        settingsScroll.setVerticalScrollBarEnabled(false);
        settingsScroll.setVisibility(View.GONE);
        LinearLayout settingsContent = new LinearLayout(this);
        settingsContent.setOrientation(LinearLayout.VERTICAL);
        settingsContent.setPadding(hPad, dp(34), hPad, dp(34));
        settingsScroll.addView(settingsContent, new ScrollView.LayoutParams(-1, -2));

        settingsContent.addView(text("设置", 18, INK, true));
        LinearLayout hapticsBox = new LinearLayout(this);
        hapticsBox.setGravity(Gravity.CENTER_VERTICAL);
        hapticsBox.setBackground(rounded(META, 12));
        hapticsBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        hapticsBox.addView(text("车控震动", 14, INK, false), new LinearLayout.LayoutParams(0, -2, 1));
        hapticsSwitch = new Switch(this);
        hapticsSwitch.setChecked(hapticsEnabled);
        hapticsSwitch.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{MINT, MUTED}));
        hapticsSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            hapticsEnabled = checked;
            prefs.edit().putString(HAPTICS_KEY, checked ? "on" : "off").apply();
        });
        hapticsBox.addView(hapticsSwitch);
        LinearLayout.LayoutParams hbParams = new LinearLayout.LayoutParams(-1, -2);
        hbParams.topMargin = dp(10);
        settingsContent.addView(hapticsBox, hbParams);

        LinearLayout phoneBox = new LinearLayout(this);
        phoneBox.setOrientation(LinearLayout.VERTICAL);
        phoneBox.setBackground(rounded(META, 12));
        phoneBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        phoneBox.addView(text("配对手机", 13, INK, true));
        phoneTargetView = text("自动连接已配对手机", 11, MUTED, false);
        phoneBox.addView(phoneTargetView);
        phoneBox.setOnClickListener(view -> choosePhoneDevice());
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(-1, -2);
        pbParams.topMargin = dp(8);
        settingsContent.addView(phoneBox, pbParams);

        diagnosticsBox = new LinearLayout(this);
        diagnosticsBox.setOrientation(LinearLayout.VERTICAL);
        diagnosticsBox.setBackground(rounded(PANEL, 12));
        diagnosticsBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams diagParams = new LinearLayout.LayoutParams(-1, -2);
        diagParams.topMargin = dp(8);
        settingsContent.addView(diagnosticsBox, diagParams);

        TextView backBtn = actionButton("返回", META, INK, false, view -> {
            settingsOpen = false;
            renderScreens();
        });
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(-1, dp(40));
        backParams.topMargin = dp(10);
        settingsContent.addView(backBtn, backParams);

        toastBox = new FrameLayout(this);
        toastBox.setBackground(rounded(Color.rgb(48, 67, 74), 12));
        toastBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        toastBox.setVisibility(View.GONE);
        toastView = text("", 13, INK, false);
        toastView.setGravity(Gravity.CENTER);
        toastBox.addView(toastView, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        root.addView(homeShell, new FrameLayout.LayoutParams(-1, -1));
        root.addView(settingsScroll, new FrameLayout.LayoutParams(-1, -1));
        root.addView(confirmScreen, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams toastParams = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        toastParams.setMargins(hPad, 0, hPad, 0);
        root.addView(toastBox, toastParams);
        return root;
    }

    private View buildMileagePage(WatchProtocol.RoundHomeMetrics metrics) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(metrics.hPadDp()), dp(metrics.topPadDp()), dp(metrics.hPadDp()), dp(metrics.bottomPadDp()));

        vehicleNameView = text(vehicleName, metrics.modelSp(), SUB_INK, true);
        vehicleNameView.setSingleLine(true);
        vehicleNameView.setEllipsize(TextUtils.TruncateAt.END);
        if (metrics.modelMaxWidthDp() > 0) vehicleNameView.setMaxWidth(dp(metrics.modelMaxWidthDp()));
        page.addView(vehicleNameView);

        FrameLayout rangeArea = new FrameLayout(this);
        rangeArea.setOnClickListener(v -> {
            showSplitDetail = !showSplitDetail;
            heroBlock.setVisibility(showSplitDetail ? View.GONE : View.VISIBLE);
            splitRow.setVisibility(showSplitDetail ? View.VISIBLE : View.GONE);
        });

        heroBlock = new LinearLayout(this);
        heroBlock.setOrientation(LinearLayout.VERTICAL);
        heroBlock.setGravity(Gravity.CENTER_HORIZONTAL);
        totalHeroView = text("— km", metrics.heroSp(), INK, true);
        totalHeroView.setSingleLine(true);
        subrangeView = text("电 — · 油 — km", metrics.subrangeSp(), MUTED, false);
        subrangeView.setSingleLine(true);
        heroBlock.addView(totalHeroView);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-2, -2);
        subParams.topMargin = dp(1);
        heroBlock.addView(subrangeView, subParams);
        rangeArea.addView(heroBlock, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        splitRow = new LinearLayout(this);
        splitRow.setOrientation(LinearLayout.HORIZONTAL);
        splitRow.setVisibility(View.GONE);
        electricText = text("— km", metrics.splitTextSp(), INK, true);
        fuelText = text("— km", metrics.splitTextSp(), INK, true);
        electricFill = new View(this);
        fuelFill = new View(this);
        splitRow.addView(buildEnergyBox("energy-electric.png", electricText, electricFill, ELECTRIC, metrics),
                new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams fuelBoxParams = new LinearLayout.LayoutParams(0, -2, 1);
        fuelBoxParams.setMarginStart(dp(metrics.splitGapDp()));
        splitRow.addView(buildEnergyBox("energy-fuel.png", fuelText, fuelFill, FUEL, metrics), fuelBoxParams);
        rangeArea.addView(splitRow, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(-1, -2);
        rangeParams.topMargin = dp(Math.max(1, metrics.sectionGapDp() / 2));
        page.addView(rangeArea, rangeParams);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable statusBg = rounded(META, 12);
        statusBg.setStroke(dp(1), META_BORDER);
        statusRow.setBackground(statusBg);
        statusRow.setPadding(dp(metrics.statusHPadDp()), dp(metrics.statusVPadDp()),
                dp(metrics.statusHPadDp()), dp(metrics.statusVPadDp()));
        connectionDot = new View(this);
        connectionDot.setBackground(rounded(Color.rgb(120, 134, 140), 4));
        statusRow.addView(connectionDot, new LinearLayout.LayoutParams(dp(metrics.statusDotDp()), dp(metrics.statusDotDp())));
        statusSummaryView = text("待连接 | 状态未知", metrics.statusTextSp(), INK, false);
        statusSummaryView.setSingleLine(true);
        LinearLayout.LayoutParams sumParams = new LinearLayout.LayoutParams(-2, -2);
        sumParams.setMarginStart(dp(metrics.sectionGapDp() + 1));
        sumParams.setMarginEnd(dp(metrics.sectionGapDp() + 1));
        statusRow.addView(statusSummaryView, sumParams);
        ImageView refreshIcon = new ImageView(this);
        refreshIcon.setImageBitmap(assetBitmap("refresh.png"));
        statusRow.addView(refreshIcon, new LinearLayout.LayoutParams(dp(metrics.statusIconDp()), dp(metrics.statusIconDp())));
        statusRow.setOnClickListener(v -> triggerRefresh());
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
        statusParams.topMargin = dp(metrics.sectionGapDp());
        page.addView(statusRow, statusParams);

        carImageView = new ImageView(this);
        carImageView.setImageBitmap(assetBitmap("starshine-8.png"));
        carImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams carParams = new LinearLayout.LayoutParams(-1, 0, 1);
        carParams.topMargin = dp(metrics.sectionGapDp());
        page.addView(carImageView, carParams);

        return page;
    }

    private View buildControlsPage(int hPad, int vPad) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(hPad, roundScreen ? dp(14) : vPad, hPad, roundScreen ? dp(14) : vPad);

        LinearLayout topPill = new LinearLayout(this);
        topPill.setOrientation(LinearLayout.HORIZONTAL);
        topPill.setGravity(Gravity.CENTER_VERTICAL);
        topPill.setBackground(rounded(META, 12));
        topPill.setPadding(dp(10), dp(4), dp(10), dp(4));
        ImageView refreshIcon = new ImageView(this);
        refreshIcon.setImageBitmap(assetBitmap("refresh.png"));
        topPill.addView(refreshIcon, new LinearLayout.LayoutParams(dp(13), dp(13)));
        controlsRefreshView = text("等待车况", 12, SUB_INK, false);
        controlsRefreshView.setSingleLine(true);
        LinearLayout.LayoutParams crParams = new LinearLayout.LayoutParams(-2, -2);
        crParams.setMarginStart(dp(4));
        topPill.addView(controlsRefreshView, crParams);
        topPill.setOnClickListener(v -> triggerRefresh());
        page.addView(topPill, new LinearLayout.LayoutParams(-2, -2));

        FrameLayout centerArea = new FrameLayout(this);
        emptyView = text("连接手机后显示已映射车控", 13, MUTED, false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setBackground(rounded(ORB_UNASSIGNED, 14));
        emptyView.setPadding(dp(12), dp(16), dp(12), dp(16));
        centerArea.addView(emptyView, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setGravity(Gravity.CENTER);
        centerArea.addView(gridContainer, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        page.addView(centerArea, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout settingsPill = new LinearLayout(this);
        settingsPill.setOrientation(LinearLayout.HORIZONTAL);
        settingsPill.setGravity(Gravity.CENTER_VERTICAL);
        settingsPill.setBackground(rounded(META, 14));
        settingsPill.setPadding(dp(12), dp(5), dp(12), dp(5));
        ImageView gear = new ImageView(this);
        gear.setImageBitmap(assetBitmap("settings.png"));
        settingsPill.addView(gear, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView settingsLabel = text("设置", 12, SUB_INK, false);
        LinearLayout.LayoutParams slParams = new LinearLayout.LayoutParams(-2, -2);
        slParams.setMarginStart(dp(4));
        settingsPill.addView(settingsLabel, slParams);
        settingsPill.setOnClickListener(v -> {
            if (pending != null || !confirmTitle.isEmpty()) return;
            settingsOpen = true;
            renderScreens();
        });
        page.addView(settingsPill, new LinearLayout.LayoutParams(-2, -2));

        return page;
    }

    private LinearLayout buildEnergyBox(String iconName, TextView valueView, View fillView, int fillColor,
                                        WatchProtocol.RoundHomeMetrics metrics) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rounded(Color.rgb(32, 49, 57), 8));
        box.setPadding(dp(metrics.splitBoxHPadDp()), dp(metrics.splitBoxVPadDp()),
                dp(metrics.splitBoxHPadDp()), dp(metrics.splitBoxVPadDp() + 1));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageBitmap(assetBitmap(iconName));
        row.addView(icon, new LinearLayout.LayoutParams(dp(metrics.splitIconDp()), dp(metrics.splitIconDp())));
        valueView.setSingleLine(true);
        LinearLayout.LayoutParams vParams = new LinearLayout.LayoutParams(-2, -2);
        vParams.setMarginStart(dp(metrics.sectionGapDp()));
        row.addView(valueView, vParams);
        box.addView(row);

        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setWeightSum(100);
        track.setBackground(rounded(TRACK, 3));
        fillView.setBackground(rounded(fillColor, 3));
        track.addView(fillView, new LinearLayout.LayoutParams(0, dp(4), 0));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(-1, dp(4));
        trackParams.topMargin = dp(metrics.sectionGapDp());
        box.addView(track, trackParams);
        return box;
    }

    private TextView actionButton(String label, int bg, int fg, boolean bold, View.OnClickListener listener) {
        TextView btn = text(label, 16, fg, bold);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(rounded(bg, 12));
        btn.setOnClickListener(listener);
        return btn;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private int dialDiameterDp() {
        Configuration cfg = getResources().getConfiguration();
        if (cfg.screenWidthDp > 0 && cfg.screenHeightDp > 0) {
            return Math.min(cfg.screenWidthDp, cfg.screenHeightDp);
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float density = dm.density > 0f ? dm.density : 1f;
        return Math.round(Math.min(dm.widthPixels, dm.heightPixels) / density);
    }

    private Bitmap assetBitmap(String name) {
        if (name == null || name.isEmpty()) return null;
        Bitmap cached = assetCache.get(name);
        if (cached != null) return cached;
        try (InputStream stream = getAssets().open(name)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap != null) assetCache.put(name, bitmap);
            return bitmap;
        } catch (IOException ignored) {
            return null;
        }
    }
}
