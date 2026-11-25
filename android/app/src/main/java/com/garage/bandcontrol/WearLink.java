package com.garage.bandcontrol;

import android.content.Context;
import android.os.Handler;

import java.util.function.Consumer;

final class WearLink {
    private final XiaomiLink xiaomi;
    private final BluetoothWatchLink bluetoothWatch;
    private final Runnable onChange;
    private String lastStatus = "通信服务未启动";

    WearLink(Context context, Handler main, XiaomiLink.Receiver receiver,
             Consumer<String> onDisconnect, Runnable onChange) {
        this.onChange = onChange;
        this.xiaomi = new XiaomiLink(context, main, receiver, onDisconnect, this::refreshStatus);
        this.bluetoothWatch = new BluetoothWatchLink(context, main, receiver, onDisconnect, this::refreshStatus);
    }

    String status() {
        return lastStatus;
    }

    boolean canAuthorize() {
        return xiaomi.canAuthorize();
    }

    void authorize() {
        xiaomi.authorize();
    }

    void start() {
        xiaomi.start();
        bluetoothWatch.start();
        refreshStatus();
    }

    void stop() {
        bluetoothWatch.stop();
        xiaomi.stop();
        refreshStatus();
    }

    void send(String node, String text, Consumer<String> onFailure) {
        if (BluetoothWatchLink.handlesNode(node)) {
            bluetoothWatch.send(node, text, onFailure);
        } else {
            xiaomi.send(node, text, onFailure);
        }
    }

    private void refreshStatus() {
        String x = xiaomi.status();
        String b = bluetoothWatch.status();
        String combined;
        if (!b.isEmpty() && x.equals("已收到手环消息")) {
            combined = "已收到穿戴设备消息";
        } else if (!b.isEmpty() && (x.equals("通信服务未启动") || x.equals("正在连接小米运动健康")
                || x.equals("等待小米运动健康连接") || x.equals("等待手环连接") || x.equals("无法启动小米通信 SDK"))) {
            combined = b;
        } else {
            combined = x;
        }
        if (combined.equals(lastStatus)) return;
        lastStatus = combined;
        onChange.run();
    }
}
