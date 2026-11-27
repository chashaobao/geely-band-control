package com.mario.lynkband;

import android.app.Activity;

import com.mario.lynkband.transport.WatchUpdateWire;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class PhoneWearUpdates {
    private static final int PICK_WATCH = 271;
    private static final int PICK_RPK = 272;
    private static final int SAVE_RPK = 273;
    private static final String WATCH_PACKAGE = "com.mario.lynkband.watch";
    private static final String VELA_PACKAGE = "com.mario.lynkband.phone";
    private static final String[] MI_FITNESS = {"com.xiaomi.wearable", "com.xiaomi.wearable.global", "com.mi.health"};
    private static final String CACHE_PREFIX = "wear-update-";
    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean resumed;
    private AlertDialog dialog;
    private Operation operation;
    private PickerResult pendingResult;
    private File exportFile;
    private String exportVersion;

    PhoneWearUpdates(Activity activity, Bundle saved) {
        this.activity = activity;
        if (saved == null) return;
        String name = saved.getString("wearExportFile", "");
        if (name.matches("wear-update-[0-9]+\\.tmp")) {
            File file = new File(activity.getCacheDir(), name);
            if (file.isFile() && file.length() > 0 && file.length() <= PhoneWearReleases.MAX_RPK) {
                exportFile = file;
                exportVersion = saved.getString("wearExportVersion", "");
            }
        }
    }

    void show(boolean watch) {
        if (!resumed || operation != null) return;
        choose(watch ? "选择 Watch APK 来源" : "选择 Vela RPK 来源",
                new String[]{"选择 GitHub 发布版本", "选择本地文件"}, which -> {
                    if (which == 0) releases(watch);
                    else openPackage(watch);
                });
    }

    private void openPackage(boolean watch) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
        if (watch) {
            intent.setType("application/vnd.android.package-archive");
        } else {
            // RPKs have no common MIME type across Android file providers.
            intent.setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/octet-stream", "application/zip", "application/x-rpk", "application/vnd.quickapp.rpk"
            });
        }
        picker(intent, watch ? PICK_WATCH : PICK_RPK);
    }

    private void releases(boolean watch) {
        run("正在获取 GitHub 发布版本…", op -> PhoneWearReleases.list(op::check), releases -> {
            List<PhoneWearReleases.Release> choices = releases.stream().filter(release -> release.assets() == null
                    || release.assets().stream().anyMatch(asset -> watch ? asset.isWatch() : asset.isRpk()))
                    .collect(java.util.stream.Collectors.toList());
            if (choices.isEmpty()) {
                message("未找到安装包", "这个仓库的最近发布中没有可用的穿戴端安装包。可以改选本地文件。");
                return;
            }
            String[] labels = choices.stream().map(PhoneWearReleases.Release::tag).toArray(String[]::new);
            choose("选择 GitHub 发布版本", labels, which -> listAssets(watch, choices.get(which)));
        });
    }

    private void listAssets(boolean watch, PhoneWearReleases.Release release) {
        run("正在读取发布文件…", op -> PhoneWearReleases.assets(release, op::check), assets -> {
            List<PhoneWearReleases.Asset> choices = assets.stream().filter(asset -> watch ? asset.isWatch() : asset.isRpk())
                    .collect(java.util.stream.Collectors.toList());
            if (choices.isEmpty()) {
                message("没有适用的安装包", "这个版本未附带所选类型的安装包。请返回选择其他版本或本地文件。");
                return;
            }
            String[] labels = choices.stream().map(PhoneWearUpdates::assetLabel).toArray(String[]::new);
            choose(release.tag(), labels, which -> {
                PhoneWearReleases.Asset selected = choices.get(which);
                String download = "正在下载并校验 " + selected.name() + "…";
                PackageSource source = (out, op) -> PhoneWearReleases.download(release, selected, assets, out,
                        op::check, bytes -> op.progress(download, bytes, selected.size()));
                if (watch) watchGuidance(source);
                else stageRpk(source, selected.name());
            });
        });
    }

    private static String assetLabel(PhoneWearReleases.Asset asset) {
        String name = asset.name();
        if (name.startsWith("lynk-band-pill-")) return "跑道屏 · " + name;
        if (name.startsWith("lynk-band-standard-")) return "标准屏 · " + name;
        return asset.isRpk() ? "旧版通用包 · " + name : name;
    }

    private PackageSource local(Uri uri, long limit) {
        return (out, op) -> {
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new UpdateException("无法打开选中的文件。");
                PhoneWearReleases.copy(in, out, limit, -1, op::check,
                        bytes -> op.progress("正在读取本地安装包…", bytes, -1), null);
            }
        };
    }

    private void watchGuidance(PackageSource source) {
        display(new AlertDialog.Builder(activity).setTitle("发送 Watch 更新")
                .setMessage("先确认手机已选择配对的 Watch。手机显示「正在等待手表接收」后，在手表上的「小领通 → 设置」打开「接收手机更新」，并保持两端页面打开。手表收到安装包后会提示确认安装。旧版没有接收入口时，请先按手表现有方式安装带接收功能的版本。")
                .setNegativeButton("取消", null).setPositiveButton("开始准备", (d, w) -> sendWatch(source)));
    }

    private void sendWatch(PackageSource source) {
        run("正在准备 Watch 安装包…", op -> {
            File packageFile = File.createTempFile(CACHE_PREFIX, ".apk", activity.getCacheDir());
            try {
                writePackage(packageFile, source, op);
                PackageInfo info = activity.getPackageManager().getPackageArchiveInfo(packageFile.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info == null || !WATCH_PACKAGE.equals(info.packageName) || info.signingInfo == null
                        || info.signingInfo.getApkContentsSigners() == null || info.signingInfo.getApkContentsSigners().length == 0
                        || info.getLongVersionCode() <= 0) {
                    throw new UpdateException("请选择有效且已签名的小领通 Watch APK。");
                }
                String version = info.versionName == null ? Long.toString(info.getLongVersionCode()) : info.versionName;
                String title = "正在等待手表接收 " + version + "…";
                op.progress(title, 0, packageFile.length());
                try {
                    new WatchApkTransfer(activity).send(packageFile, op.cancelled::get,
                            bytes -> op.progress("正在发送 Watch 更新…", bytes, packageFile.length()));
                } catch (IOException error) {
                    String detail = error.getMessage();
                    throw new UpdateException(detail == null ? "请核对配对状态，并打开手表上的接收更新页面。" : detail);
                }
                return version;
            } finally {
                packageFile.delete();
            }
        }, version -> message("Watch 安装包已发送", "版本 " + version + "。请在手表上核对安装提示并确认。"));
    }

    private void stageRpk(PackageSource source, String releaseName) {
        var current = ((WristApp) activity.getApplication()).currentVelaVersion();
        if (current == null) {
            message("尚未读取 Vela 版本", "请在手机选择 Vela，并打开一台穿戴设备上的小领通后重试。旧版首次更新可用配套 RPK 和设备的安装工具。");
            return;
        }
        run("正在准备 Vela 安装包…", op -> {
            File file = File.createTempFile(CACHE_PREFIX, ".tmp", activity.getCacheDir());
            try {
                writePackage(file, source, op);
                RpkInfo metadata = rpkInfo(file);
                checkRpkTarget(metadata, current);
                String version = metadata.version();
                if (releaseName != null && !releaseName.equals("lynk-band.rpk") && !releaseName.endsWith("-" + version + ".rpk")) {
                    throw new UpdateException("安装包中的版本与发布文件名不符。");
                }
                op.check();
                return new Export(file, version, releaseName == null ? metadata.fileName() : releaseName);
            } catch (IOException | RuntimeException error) {
                file.delete();
                throw error;
            }
        }, export -> {
            clearExport();
            exportFile = export.file();
            exportVersion = export.version();
            if (!picker(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, export.name()), SAVE_RPK)) clearExport();
        });
    }

    private void writePackage(File file, PackageSource source, Operation operation) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) { source.copy(out, operation); }
        operation.check();
        if (file.length() == 0) throw new UpdateException("安装包为空。");
    }

    static String rpkVersion(File file) throws IOException {
        return rpkInfo(file).version();
    }

    static RpkInfo rpkInfo(File file) throws IOException {
        try (ZipFile archive = new ZipFile(file)) {
            ZipEntry manifest = archive.getEntry("manifest.json");
            ZipEntry cert = archive.getEntry("META-INF/CERT");
            if (manifest == null || manifest.isDirectory() || cert == null || cert.isDirectory() || cert.getSize() <= 0) {
                throw new UpdateException("请选择带清单和签名信息的小领通 Vela RPK。");
            }
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            try (InputStream in = archive.getInputStream(manifest)) {
                PhoneWearReleases.copy(in, data, 64 * 1024L, manifest.getSize(), () -> {}, bytes -> {}, null);
            }
            JSONObject info = new JSONObject(new String(data.toByteArray(), StandardCharsets.UTF_8));
            String version = info.optString("versionName");
            int versionCode = info.opt("versionCode") instanceof Integer value ? value : 0;
            if (!VELA_PACKAGE.equals(info.optString("package")) || !version.matches("[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}")
                    || versionCode <= 0) {
                throw new UpdateException("RPK 的应用包名或版本不符合预期。");
            }
            String shape = switch (info.optString("icon")) {
                case "/common/launcher-round.png" -> "pill-";
                case "/common/launcher-pro.png" -> "standard-";
                default -> "";
            };
            String profile = shape.isEmpty() ? "" : shape.substring(0, shape.length() - 1);
            return new RpkInfo(version, versionCode, profile, "lynk-band-" + shape + version + ".rpk");
        } catch (JSONException | java.util.zip.ZipException badArchive) {
            throw new UpdateException("无法读取此 Vela RPK 的安装包清单。");
        }
    }

    static void checkRpkTarget(RpkInfo candidate, WristApp.VelaVersion current) throws IOException {
        if (current == null) throw new UpdateException("请先打开要更新的 Vela 设备上的小领通，并保持连接。");
        if (!candidate.profile().equals(current.profile()))
            throw new UpdateException("RPK 屏型与当前设备的安装包不匹配。");
        if (!WatchUpdateWire.versionAllowed(current.code(), candidate.versionCode()))
            throw new UpdateException("RPK 版本低于当前 Vela 版本，不能降级。");
    }

    boolean activityResult(int request, int result, Intent data) {
        if (request != PICK_WATCH && request != PICK_RPK && request != SAVE_RPK) return false;
        Uri uri = result == Activity.RESULT_OK && data != null ? data.getData() : null;
        pendingResult = new PickerResult(request, uri != null && "content".equals(uri.getScheme()) ? uri : null);
        if (resumed) handleResult();
        return true;
    }

    private void handleResult() {
        PickerResult result = pendingResult;
        pendingResult = null;
        if (result == null) return;
        if (result.request() == SAVE_RPK) {
            File file = exportFile;
            String version = exportVersion;
            exportFile = null;
            exportVersion = null;
            if (file != null && result.uri() != null) saveRpk(file, version, result.uri());
            else if (file != null) file.delete();
            else if (result.uri() != null) {
                discardDocument(result.uri());
                message("导出已中断", "请重新选择 Vela 安装包。");
            }
        } else if (result.uri() != null) {
            if (result.request() == PICK_WATCH) watchGuidance(local(result.uri(), PhoneWearReleases.MAX_WATCH_APK));
            else stageRpk(local(result.uri(), PhoneWearReleases.MAX_RPK), null);
        }
    }

    private void saveRpk(File file, String version, Uri destination) {
        var current = ((WristApp) activity.getApplication()).currentVelaVersion();
        run("正在保存 Vela 安装包…", op -> {
            boolean saved = false;
            try {
                checkRpkTarget(rpkInfo(file), current);
                try (InputStream in = new java.io.FileInputStream(file);
                     OutputStream out = activity.getContentResolver().openOutputStream(destination, "wt")) {
                    if (out == null) throw new UpdateException("无法写入所选保存位置。");
                    PhoneWearReleases.copy(in, out, PhoneWearReleases.MAX_RPK, file.length(), op::check,
                            bytes -> op.progress("正在保存 Vela 安装包…", bytes, file.length()), null);
                }
                saved = true;
            } finally {
                file.delete();
                if (!saved) discardDocument(destination);
            }
            return version;
        }, done -> {
            AlertDialog.Builder success = new AlertDialog.Builder(activity).setTitle("Vela 安装包已保存")
                    .setMessage("版本 " + done + "。请在小米运动健康或设备兼容的 RPK 安装工具中打开第三方应用安装入口，选中刚保存的文件。入口取决于设备和固件。");
            Intent fitness = fitnessLaunch();
            if (fitness != null) success.setNegativeButton("关闭", null).setPositiveButton("打开小米运动健康", (d, w) -> start(fitness));
            else success.setPositiveButton("知道了", null);
            display(success);
        });
    }

    private void discardDocument(Uri document) {
        try {
            if (DocumentsContract.isDocumentUri(activity, document)) DocumentsContract.deleteDocument(activity.getContentResolver(), document);
        } catch (IOException | RuntimeException ignored) {
            // The provider may not allow deleting a document that it created.
        }
    }

    private Intent fitnessLaunch() {
        for (String packageName : MI_FITNESS) {
            Intent launch = activity.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch != null) return launch;
        }
        return null;
    }

    void resume() {
        resumed = true;
        handleResult();
    }

    void stop() {
        resumed = false;
        cancel();
        closeDialog();
    }

    void save(Bundle state) {
        if (exportFile != null) {
            state.putString("wearExportFile", exportFile.getName());
            state.putString("wearExportVersion", exportVersion);
        }
    }

    void destroy() {
        stop();
        executor.shutdown();
        if (activity.isFinishing()) clearExport();
    }

    private void clearExport() {
        if (exportFile != null) exportFile.delete();
        exportFile = null;
        exportVersion = null;
    }

    private boolean picker(Intent intent, int request) {
        try {
            activity.startActivityForResult(intent, request);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            message("无法打开文件选择器", "此设备没有可用的系统文件选择器。");
            return false;
        }
    }

    private void start(Intent intent) {
        try { activity.startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(activity, "无法打开小米运动健康。请手动打开安装工具。", Toast.LENGTH_LONG).show();
        }
    }

    private void choose(String title, String[] choices, Consumer<Integer> chosen) {
        display(new AlertDialog.Builder(activity).setTitle(title)
                .setItems(choices, (d, which) -> chosen.accept(which)).setNegativeButton("取消", null));
    }

    private void message(String title, String body) {
        display(new AlertDialog.Builder(activity).setTitle(title).setMessage(body).setPositiveButton("知道了", null));
    }

    private AlertDialog display(AlertDialog.Builder builder) {
        closeDialog();
        dialog = builder.show();
        return dialog;
    }

    private void closeDialog() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private <T> void run(String title, Work<T> work, Consumer<T> success) {
        if (!resumed || operation != null) return;
        Operation op = new Operation();
        operation = op;
        AlertDialog progress = display(new AlertDialog.Builder(activity).setTitle("穿戴端更新")
                .setMessage(title).setNegativeButton("取消", (d, w) -> cancel()).setOnCancelListener(d -> cancel()));
        progress.setCanceledOnTouchOutside(false);
        android.view.Window window = progress.getWindow();
        if (window != null) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        executor.execute(() -> {
            try {
                T value = work.run(op);
                activity.runOnUiThread(() -> {
                    if (complete(op)) success.accept(value);
                    else if (value instanceof Export export) export.file().delete();
                });
            } catch (IOException | RuntimeException error) {
                activity.runOnUiThread(() -> {
                    if (complete(op)) {
                        String detail = error.getMessage();
                        // Network and document exceptions can contain private or temporary URLs.
                        if (detail == null || !detail.matches("[\u3400-\u9fffA-Za-z0-9 .，。！？、：「」（）()…→+_-]{1,240}")) {
                            detail = "未能完成操作。请检查网络、安装包或保存位置后重试。";
                        }
                        message("穿戴端更新未完成", detail);
                    }
                });
            }
        });
    }

    private boolean complete(Operation current) {
        if (!resumed || operation != current) return false;
        operation = null;
        closeDialog();
        return true;
    }

    private void cancel() {
        if (operation != null) {
            operation.cancelled.set(true);
            operation = null;
        }
    }

    private final class Operation {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private long lastProgress = -1_000;

        void check() throws IOException {
            if (cancelled.get()) throw new UpdateException("操作已取消。");
        }

        void progress(String stage, long bytes, long total) {
            long now = SystemClock.elapsedRealtime();
            if (bytes != 0 && bytes != total && now - lastProgress < 250) return;
            lastProgress = now;
            String size = bytes == 0 || total <= 0 ? (bytes == 0 ? "" : "\n" + (bytes / 1024) + " KiB")
                    : "\n" + (100 * bytes / total) + "%";
            activity.runOnUiThread(() -> {
                if (resumed && operation == this && dialog != null) dialog.setMessage(stage + size);
            });
        }
    }

    record RpkInfo(String version, int versionCode, String profile, String fileName) {}
    private record PickerResult(int request, Uri uri) {}
    private record Export(File file, String version, String name) {}
    private interface Work<T> { T run(Operation op) throws IOException; }
    private interface PackageSource { void copy(OutputStream out, Operation op) throws IOException; }
    private static final class UpdateException extends IOException {
        UpdateException(String message) { super(message); }
    }
}
