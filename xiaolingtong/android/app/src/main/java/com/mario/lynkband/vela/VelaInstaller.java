package com.mario.lynkband.vela;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class VelaInstaller {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    public interface Progress { void report(String stage, int percent); }

    public static byte[] key(String input) { return BandProtocol.parseKey(input); }

    @SuppressLint("MissingPermission")
    public static boolean send(BluetoothDevice device, byte[] key, File rpk, int versionCode,
                               BooleanSupplier cancelled, Progress progress) throws IOException {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        try (BluetoothSocket socket = connect(device, cancelled, timer)) {
            Runnable close = () -> closeSocket(socket);
            timer.scheduleWithFixedDelay(() -> { if (cancelled.getAsBoolean()) close.run(); },
                    0, 200, TimeUnit.MILLISECONDS);
            timer.schedule(close, 5, TimeUnit.MINUTES);
            try (BandWire wire = new BandWire(socket.getInputStream(), socket.getOutputStream());
                 BandProtocol protocol = new BandProtocol(wire)) {
                progress.report("正在认证 Vela 设备", -1);
                protocol.authenticate(key, Build.VERSION.SDK_INT, Build.MODEL);
                if (cancelled.getAsBoolean()) throw new IOException("操作已取消。");
                return protocol.install(Files.readAllBytes(rpk.toPath()), versionCode, progress::report)
                        == BandProtocol.Result.CONFIRMED;
            }
        } catch (IOException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException("Vela 蓝牙安装未完成，请核对设备授权和配对状态。", failure);
        } finally {
            Arrays.fill(key, (byte) 0);
            timer.shutdownNow();
        }
    }
    @SuppressLint("MissingPermission")
    private static BluetoothSocket connect(BluetoothDevice device, BooleanSupplier cancelled,
                                           ScheduledExecutorService timer) throws IOException {
        // Prefer the advertised serial service. Some paired Xiaomi devices expose only channel 5.
        for (boolean useChannel : new boolean[]{false, true}) {
            if (cancelled.getAsBoolean()) throw new IOException("操作已取消。");
            BluetoothSocket socket;
            try {
                socket = useChannel ? (BluetoothSocket) BluetoothDevice.class
                        .getMethod("createRfcommSocket", int.class).invoke(device, 5)
                        : device.createRfcommSocketToServiceRecord(SPP);
            } catch (ReflectiveOperationException | IOException | SecurityException unavailable) {
                continue;
            }
            if (socket == null) continue;
            long deadline = android.os.SystemClock.elapsedRealtime() + 10_000;
            var timeout = timer.scheduleWithFixedDelay(() -> {
                if (cancelled.getAsBoolean() || android.os.SystemClock.elapsedRealtime() >= deadline)
                    closeSocket(socket);
            }, 0, 200, TimeUnit.MILLISECONDS);
            try {
                socket.connect();
                return socket;
            } catch (IOException unavailable) {
                closeSocket(socket);
            } finally { timeout.cancel(false); }
        }
        throw new IOException("无法连接 Vela 经典蓝牙串口。请核对配对状态，或改用保存 RPK。");
    }

    private static void closeSocket(BluetoothSocket socket) {
        try { socket.close(); } catch (IOException ignored) { }
    }

}
