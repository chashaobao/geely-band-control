package com.mario.lynkband;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Reads the current provider bitmap on the widget host's main thread. */
final class VehiclePhoto {
    record Image(String id, String base64) {}

    enum Target { WATCH, BAND }

    private final Target target;
    private WeakReference<Bitmap> previous = new WeakReference<>(null);
    private int generation;
    private String previousVehicle;
    private Image cached;

    VehiclePhoto(Target target) { this.target = target; }

    Image read(View root, String binding) {
        View panel = CardContent.find(root, "rl_widget_cal_state");
        String name = CardContent.text(panel, "widget_name");
        if (panel == null || CardContent.awaitingOwner(root) || CardContent.find(root, "ll_loading_auth") != null
                || CardContent.find(root, "widget_hint") != null || name.isEmpty())
            return null;
        // com.lynkco.customer 4.3.1 (403101550), layout_widget / res/rcW.xml:331.
        // car_img's XML src is mipmap/car_side_line until the provider supplies the vehicle.
        View slot = CardContent.find(panel, "car_img");
        if (!(slot instanceof ImageView image) || image.getImageAlpha() == 0 || !visibleInCard(slot, root)
                || !(image.getDrawable() instanceof BitmapDrawable drawable)) return null;
        Bitmap source = drawable.getBitmap();
        if (source == null || source.isRecycled()) return null;
        String vehicle = binding + "/" + name;
        if (previous.get() == source && generation == source.getGenerationId()) {
            // A partial RemoteViews update may change the vehicle before replacing its bitmap.
            return target == Target.WATCH || vehicle.equals(previousVehicle) ? cached : null;
        }
        previousVehicle = vehicle;
        previous = new WeakReference<>(source);
        generation = source.getGenerationId();
        cached = null;
        try {
            Resources res = slot.getResources();
            if (!LynkWidget.OWNER.equals(res.getResourcePackageName(slot.getId()))) return null;
            int placeholderId = res.getIdentifier("car_side_line", "mipmap", LynkWidget.OWNER);
            if (placeholderId == 0) return null;
            Drawable initial = res.getDrawable(placeholderId, null);
            if (!(initial instanceof BitmapDrawable silhouette) || source.sameAs(silhouette.getBitmap())) return null;
            cached = encode(source);
        } catch (RuntimeException unavailable) {
            // A stale provider resource or an unreadable bitmap must not affect card status and controls.
        }
        return cached;
    }

    private static boolean visibleInCard(View view, View root) {
        for (View current = view; current != null; current = current.getParent() instanceof View p ? p : null) {
            if (current.getVisibility() != View.VISIBLE || current.getAlpha() <= 0f) return false;
            // The host also renders offscreen when the companion activity is closed.
            if (current == root) return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation") // API 28/29 spell lossy WebP as WEBP.
    private Image encode(Bitmap source) {
        int maxWidth = target == Target.BAND ? 160 : 256;
        int maxHeight = target == Target.BAND ? 80 : 128;
        int maxBytes = target == Target.BAND ? BandPhoto.MAX_CHARS / 4 * 3 : 8192;
        float scale = Math.min(1f, Math.min((float) maxWidth / source.getWidth(), (float) maxHeight / source.getHeight()));
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap.CompressFormat format = target == Target.BAND ? Bitmap.CompressFormat.PNG
                : Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(maxBytes);
        for (int attempt = 0; attempt < 4; attempt++) {
            Bitmap scaled = Bitmap.createScaledBitmap(source, width, height, true);
            Bitmap readable = scaled;
            try {
                if (scaled.getConfig() == Bitmap.Config.HARDWARE) readable = scaled.copy(Bitmap.Config.ARGB_8888, false);
                if (readable == null || !hasVisiblePixel(readable)) return null;
                bytes.reset();
                if (!readable.compress(format, 85, bytes)) return null;
                if (bytes.size() > 0 && bytes.size() <= maxBytes) {
                    byte[] encoded = bytes.toByteArray();
                    return new Image(digest(encoded), Base64.encodeToString(encoded, Base64.NO_WRAP));
                }
            } finally {
                if (readable != null && readable != scaled) readable.recycle();
                if (scaled != source) scaled.recycle();
            }
            width = Math.max(1, width * 2 / 3);
            height = Math.max(1, height * 2 / 3);
        }
        return null;
    }

    private static boolean hasVisiblePixel(Bitmap bitmap) {
        if (!bitmap.hasAlpha()) return true;
        int[] row = new int[bitmap.getWidth()];
        for (int y = 0; y < bitmap.getHeight(); y++) {
            bitmap.getPixels(row, 0, row.length, 0, y, row.length, 1);
            for (int pixel : row) if ((pixel >>> 24) != 0) return true;
        }
        return false;
    }

    private static String digest(byte[] bytes) {
        try {
            StringBuilder hex = new StringBuilder(64);
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hex.append(Character.forDigit((b & 255) >>> 4, 16)).append(Character.forDigit(b & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
