package com.garage.bandcontrol;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.xiaomi.xms.wearable.Wearable;
import com.xiaomi.xms.wearable.auth.AuthApi;
import com.xiaomi.xms.wearable.auth.Permission;
import com.xiaomi.xms.wearable.message.MessageApi;
import com.xiaomi.xms.wearable.node.Node;
import com.xiaomi.xms.wearable.node.NodeApi;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

final class XiaomiLink {
    interface Receiver {
        void onMessage(String node, byte[] data);
    }

    private final Context context;
    private final Handler main;
    private final Receiver receiver;
    private final Consumer<String> onDisconnect;
    private final Runnable onChange;
    private final Set<String> connected = new HashSet<>();
    private final Set<String> listening = new HashSet<>();
    private final Set<String> permissionNeeded = new HashSet<>();
    private final Set<String> registering = new HashSet<>();
    private final Runnable poll = this::poll;
    private MessageApi messages;
    private NodeApi nodes;
    private AuthApi auth;
    private boolean active;
    private boolean authorizing;
    private int generation;
    private String status = "通信服务未启动";

    XiaomiLink(Context context, Handler main, Receiver receiver, Consumer<String> onDisconnect, Runnable onChange) {
        this.context = context;
        this.main = main;
        this.receiver = receiver;
        this.onDisconnect = onDisconnect;
        this.onChange = onChange;
    }

    String status() {
        return status;
    }

    boolean canAuthorize() {
        return active && !permissionNeeded.isEmpty() && !authorizing;
    }

    void authorize() {
        if (!canAuthorize()) return;
        String node = permissionNeeded.iterator().next();
        int requestGeneration = generation;
        authorizing = true;
        setStatus("请在小米运动健康中确认设备授权");
        try {
            auth.requestPermission(node, Permission.DEVICE_MANAGER)
                    .addOnSuccessListener(granted -> main.post(() -> {
                        if (!active || generation != requestGeneration) return;
                        authorizing = false;
                        for (Permission item : granted) {
                            if (Permission.DEVICE_MANAGER.getName().equals(item.getName())
                                    && connected.contains(node)) {
                                permissionNeeded.remove(node);
                                register(node, requestGeneration);
                                return;
                            }
                        }
                        setStatus("小米设备通信权限未授予");
                    }))
                    .addOnFailureListener(error -> main.post(() -> authorizationFailed(requestGeneration)));
        } catch (RuntimeException unavailable) {
            authorizationFailed(requestGeneration);
        }
    }

    private void authorizationFailed(int requestGeneration) {
        if (!active || generation != requestGeneration) return;
        authorizing = false;
        setStatus("设备授权未完成，请检查小米运动健康");
    }

    void start() {
        if (active) return;
        active = true;
        generation++;
        setStatus("正在连接小米运动健康");
        try {
            messages = Wearable.getMessageApi(context);
            nodes = Wearable.getNodeApi(context);
            auth = Wearable.getAuthApi(context);
            poll();
        } catch (RuntimeException unavailable) {
            active = false;
            setStatus("无法启动小米通信 SDK");
            Log.w("XiaomiLink", "Wearable service initialization failed", unavailable);
        }
    }

    void stop() {
        if (!active) return;
        active = false;
        generation++;
        main.removeCallbacks(poll);
        for (String id : listening) messages.removeListener(id);
        listening.clear();
        connected.clear();
        permissionNeeded.clear();
        registering.clear();
        authorizing = false;
        setStatus("通信服务未启动");
    }

    void send(String node, String text, Consumer<String> onFailure) {
        if (!active || !listening.contains(node)) {
            onFailure.accept("not_connected");
            return;
        }
        try {
            messages.sendMessage(node, text.getBytes(StandardCharsets.UTF_8))
                    .addOnFailureListener(error -> main.post(() -> onFailure.accept("sdk_failure")));
        } catch (RuntimeException unavailable) {
            onFailure.accept("sdk_exception");
        }
    }

    private void poll() {
        if (!active) return;
        int requestGeneration = generation;
        try {
            nodes.getConnectedNodes()
                    .addOnSuccessListener(result -> main.post(() -> {
                        if (active && generation == requestGeneration) updateNodes(result, requestGeneration);
                    }))
                    .addOnFailureListener(error -> main.post(() -> {
                        if (active && generation == requestGeneration) setStatus("等待小米运动健康连接");
                    }));
        } catch (RuntimeException unavailable) {
            setStatus("等待小米运动健康连接");
        }
        main.postDelayed(poll, 5_000);
    }

    private void updateNodes(Iterable<Node> result, int requestGeneration) {
        Set<String> current = new HashSet<>();
        for (Node node : result) {
            if (node.id != null && !node.id.isEmpty()) current.add(node.id);
        }
        for (String previous : connected) {
            if (!current.contains(previous)) onDisconnect.accept(previous);
        }
        connected.clear();
        connected.addAll(current);
        for (String previous : new HashSet<>(listening)) {
            if (!current.contains(previous)) {
                messages.removeListener(previous);
                listening.remove(previous);
            }
        }
        permissionNeeded.retainAll(current);
        registering.retainAll(current);
        if (current.isEmpty()) {
            setStatus("等待手环连接");
            return;
        }
        for (String id : current) {
            if (listening.contains(id) || (authorizing && permissionNeeded.contains(id)) || !registering.add(id)) continue;
            auth.checkPermission(id, Permission.DEVICE_MANAGER).addOnSuccessListener(granted -> main.post(() -> {
                if (!active || generation != requestGeneration || !connected.contains(id)) return;
                registering.remove(id);
                if (Boolean.TRUE.equals(granted)) {
                    permissionNeeded.remove(id);
                    register(id, requestGeneration);
                } else {
                    permissionNeeded.add(id);
                    setStatus("手环已连接，请授权设备通信");
                }
            })).addOnFailureListener(error -> main.post(() -> {
                if (!active || generation != requestGeneration) return;
                registering.remove(id);
                permissionNeeded.add(id);
                setStatus("无法读取小米授权状态，可尝试重新授权");
            }));
        }
    }

    private void register(String id, int requestGeneration) {
        if (!listening.add(id)) return;
        messages.addListener(id, (device, data) -> main.post(() -> {
            if (!active || generation != requestGeneration || !connected.contains(id)
                    || !listening.contains(id) || !id.equals(device)) return;
            setStatus("已收到手环消息");
            receiver.onMessage(id, data);
        })).addOnSuccessListener(value -> main.post(() -> {
            if (active && generation == requestGeneration && !status.equals("已收到手环消息")) {
                setStatus("手环已连接，等待打开腕上银河");
            }
        })).addOnFailureListener(error -> main.post(() -> {
            if (!active || generation != requestGeneration) return;
            listening.remove(id);
            setStatus("手环已连接，消息监听失败");
        }));
    }

    private void setStatus(String value) {
        if (status.equals(value)) return;
        status = value;
        onChange.run();
    }
}
