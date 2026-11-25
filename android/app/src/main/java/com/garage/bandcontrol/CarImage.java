package com.garage.bandcontrol;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class CarImage {
    static final int CHUNK_CHARS = 1400;
    private static final int MAX_CHUNKS = 24;

    record Packaged(String hash, List<String> chunks) {}

    private CarImage() {}

    static Packaged extract(View view) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        Bitmap primary = renderScaled(view, 220, 90);
        if (primary == null) return null;
        byte[] png = toPng(primary);
        primary.recycle();
        if (png == null) return null;

        Packaged packaged = packagePng(png);
        if (packaged != null && packaged.chunks().size() > MAX_CHUNKS) {
            Bitmap compact = renderScaled(view, 168, 68);
            if (compact != null) {
                byte[] compactPng = toPng(compact);
                compact.recycle();
                if (compactPng != null) {
                    Packaged smaller = packagePng(compactPng);
                    if (smaller != null) packaged = smaller;
                }
            }
        }
        return packaged;
    }

    static Packaged packagePng(byte[] pngBytes) {
        if (pngBytes == null || pngBytes.length == 0) return null;
        String base64 = Base64.getEncoder().encodeToString(pngBytes);
        if (base64.isEmpty()) return null;
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < base64.length(); i += CHUNK_CHARS) {
            chunks.add(base64.substring(i, Math.min(base64.length(), i + CHUNK_CHARS)));
        }
        if (chunks.size() > MAX_CHUNKS * 2) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(pngBytes);
            String hash = Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 12);
            return new Packaged(hash, List.copyOf(chunks));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static Bitmap renderScaled(View view, int maxWidth, int maxHeight) {
        int srcW = view.getWidth();
        int srcH = view.getHeight();
        float scale = Math.min((float) maxWidth / srcW, (float) maxHeight / srcH);
        if (scale > 1f) scale = 1f;
        int dstW = Math.max(1, Math.round(srcW * scale));
        int dstH = Math.max(1, Math.round(srcH * scale));
        Bitmap bitmap = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale((float) dstW / srcW, (float) dstH / srcH);
        view.draw(canvas);
        if (!hasVisibleDetail(bitmap)) {
            bitmap.recycle();
            return null;
        }
        return bitmap;
    }

    private static boolean hasVisibleDetail(Bitmap bitmap) {
        int stepX = Math.max(1, bitmap.getWidth() / 16);
        int stepY = Math.max(1, bitmap.getHeight() / 12);
        int firstColor = 0;
        boolean seenOpaque = false;
        for (int y = 0; y < bitmap.getHeight(); y += stepY) {
            for (int x = 0; x < bitmap.getWidth(); x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.alpha(pixel) <= 16) continue;
                if (!seenOpaque) {
                    firstColor = pixel;
                    seenOpaque = true;
                } else if (pixel != firstColor) {
                    return true;
                }
            }
        }
        return false;
    }

    private static byte[] toPng(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)) return null;
        return out.toByteArray();
    }
}
