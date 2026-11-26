package com.mario.lynkband.ui;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

/** Transparent decorative layer for the shared square landscape illustrations. */
public final class AmbientMotion extends View {
    private enum Scene { SKY, RIDGE, CITY, COAST, ALPINE }

    private static final float ART_SIZE = 960f;
    // Bright star centers in watch_stars.webp, in original artwork pixels.
    private static final int[] STARS = {
            82,191, 202,465, 311,296, 452,230, 614,308, 705,280, 704,479, 204,110
    };
    // Open sky above the peaks in the alpine artwork.
    private static final int[] ALPINE_STARS = {153,328, 343,224, 635,306, 761,271, 827,362};
    // The two tree lines in watch_ridge_night.webp, including the clearing by the tent.
    private static final int[] FIREFLIES = {
            91,593, 181,639, 286,591, 354,656, 498,554, 652,608, 769,634, 861,572
    };
    // Offline samples from watch_city_night.webp. Each exclusive-right/bottom rectangle
    // bounds a connected group of warm lit pixels (R > 112, G > 78, R - B > 24).
    private static final int[] WINDOWS = {
            50,452,54,455,    88,473,93,478,    130,447,134,450,  182,463,186,467,
            241,467,246,470,  301,449,306,454,  367,505,370,508,  424,474,428,477,
            465,477,470,480,  510,456,513,459,  586,483,589,486,  645,488,648,492,
            732,448,735,451,  718,498,722,501,  769,467,774,470,  852,441,855,444,
            900,469,905,473,  922,508,927,512
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect visible = new Rect();
    private final RadialGradient sunlight = glow(0xffffd892);
    private final RadialGradient starlight = glow(0xffc4e4ff);
    private final RadialGradient firelight = glow(0xffe5ff83);
    private final PowerManager power;
    private final int frameMs;
    private Scene scene = Scene.SKY;
    private boolean night, centerCrop, listening, policyAllowsMotion, scheduled;
    private long elapsedMs, lastFrameMs;

    public AmbientMotion(Context context) {
        super(context);
        power = context.getSystemService(PowerManager.class);
        boolean watch = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        frameMs = watch ? 125 : 84; // At most 8 fps on watch and 12 fps on phone.
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setScene(String scene, boolean night) {
        Scene next = Scene.SKY;
        for (Scene candidate : Scene.values()) {
            if (candidate.name().equalsIgnoreCase(scene)) {
                next = candidate;
                break;
            }
        }
        if (this.scene == next && this.night == night) return;
        this.scene = next;
        this.night = night;
        elapsedMs = 0;
        invalidate();
    }

    /** Match the underlying ImageView's CENTER_CROP, or stretch to the view by default. */
    public void setCenterCrop(boolean centerCrop) {
        if (this.centerCrop == centerCrop) return;
        this.centerCrop = centerCrop;
        invalidate();
    }

    private final BroadcastReceiver powerChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refreshPolicy(); }
    };
    private final ContentObserver scaleChanged = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { refreshPolicy(); }
    };

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        listening = true;
        IntentFilter filter = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        getContext().registerReceiver(powerChanged, filter);
        getContext().getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, scaleChanged);
        getViewTreeObserver().addOnPreDrawListener(beforeDraw);
        refreshPolicy();
    }

    @Override protected void onDetachedFromWindow() {
        listening = false;
        stopFrames();
        getViewTreeObserver().removeOnPreDrawListener(beforeDraw);
        getContext().getContentResolver().unregisterContentObserver(scaleChanged);
        getContext().unregisterReceiver(powerChanged);
        super.onDetachedFromWindow();
    }

    private void refreshPolicy() {
        if (!listening) return;
        policyAllowsMotion = power != null && power.isInteractive() && !power.isPowerSaveMode()
                && Settings.Global.getFloat(getContext().getContentResolver(),
                        Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        if (!policyAllowsMotion) stopFrames();
        invalidate();
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (!visible) stopFrames();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) stopFrames();
        else if (listening) refreshPolicy();
    }

    private boolean canAnimate() {
        if (!listening || !policyAllowsMotion || !ValueAnimator.areAnimatorsEnabled()
                || !hasWindowFocus() || getWindowVisibility() != VISIBLE || !isShown()) return false;
        Display display = getDisplay();
        if (display == null || display.getState() != Display.STATE_ON || !getGlobalVisibleRect(visible)) return false;
        for (View view = this; ; ) {
            if (view.getAlpha() <= 0f) return false;
            ViewParent parent = view.getParent();
            if (!(parent instanceof View)) return true;
            view = (View) parent;
        }
    }

    // A pre-draw from scrolling, layout or a visibility change can restart an idle layer.
    // The layer itself wakes only while visible, with at most one delayed callback pending.
    private final ViewTreeObserver.OnPreDrawListener beforeDraw = this::scheduleIfVisible;

    private boolean scheduleIfVisible() {
        if (!canAnimate()) stopFrames();
        else if (!scheduled) {
            scheduled = true;
            if (lastFrameMs == 0) lastFrameMs = SystemClock.uptimeMillis();
            int delay = night && scene == Scene.CITY ? 200 : frameMs;
            postDelayed(frame, delay);
        }
        return true;
    }

    private final Runnable frame = () -> {
        scheduled = false;
        if (!canAnimate()) {
            lastFrameMs = 0;
            return;
        }
        long now = SystemClock.uptimeMillis();
        elapsedMs += Math.min(now - lastFrameMs, 250L);
        lastFrameMs = now;
        invalidate();
    };

    private void stopFrames() {
        if (scheduled) removeCallbacks(frame);
        scheduled = false;
        lastFrameMs = 0;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!policyAllowsMotion || !ValueAnimator.areAnimatorsEnabled()) return;
        float sx = getWidth() / ART_SIZE, sy = getHeight() / ART_SIZE;
        int save = canvas.save();
        if (centerCrop) {
            float scale = Math.max(sx, sy);
            canvas.translate(Math.round((getWidth() - ART_SIZE * scale) * .5f),
                    Math.round((getHeight() - ART_SIZE * scale) * .5f));
            sx = sy = scale;
        }
        canvas.scale(sx, sy);
        double t = elapsedMs / 1000.0;
        if (!night) {
            drawSun(canvas, t);
        } else {
            switch (scene) {
                case SKY -> drawStars(canvas, t, STARS);
                case RIDGE -> drawFireflies(canvas, t);
                case CITY -> drawWindows(canvas, t);
                case COAST -> drawBeacon(canvas, t);
                case ALPINE -> drawStars(canvas, t, ALPINE_STARS);
            }
        }
        canvas.restoreToCount(save);
    }

    private static RadialGradient glow(int color) {
        return new RadialGradient(0, 0, 1, color, color & 0x00ffffff, Shader.TileMode.CLAMP);
    }

    private void halo(Canvas c, RadialGradient light, float x, float y, float rx, float ry, int alpha) {
        int save = c.save();
        c.translate(x, y);
        c.scale(rx, ry);
        paint.setColor(0xffffffff);
        paint.setShader(light);
        paint.setAlpha(alpha);
        c.drawCircle(0, 0, 1, paint);
        paint.setShader(null);
        c.restoreToCount(save);
    }

    private static float wave(double angle) { return (float) Math.sin(angle); }

    private void drawSun(Canvas c, double t) {
        if (scene == Scene.COAST || scene == Scene.ALPINE) {
            float x = (scene == Scene.COAST ? 373 : 711) + 16 * wave(t * .10);
            float y = (scene == Scene.COAST ? 422 : 362) + 5 * wave(t * .13);
            halo(c, sunlight, x, y, 65, 65, 98);
            paint.setColor(0xcffff1c7);
            c.drawCircle(x, y, 16, paint);
            return;
        }
        int save = c.save();
        float origin = scene == Scene.CITY ? 40 : scene == Scene.RIDGE ? 150 : 235;
        c.translate(origin + 28 * wave(t * .19), -150);
        for (int i = 0; i < 3; i++) {
            int beam = c.save();
            c.rotate(-13 - i * 13 + 3 * wave(t * .23 + i));
            halo(c, sunlight, 0, 390, i == 1 ? 52 : 92, 590, i == 1 ? 52 : 74);
            c.restoreToCount(beam);
        }
        c.restoreToCount(save);
        // Broad, soft pools of reflected light drift along the ground/river line.
        for (int i = 0; i < 5; i++) {
            float x = 108 + i * 164 + 19 * wave(t * .27 + i);
            float y = 631 + (i % 3) * 27 + 7 * wave(t * .32 + i * 1.7);
            halo(c, sunlight, x, y, 48 + (i % 2) * 28, 11 + i % 3 * 5,
                    (int) (35 + 19 * wave(t * .73 + i * 2.3)));
        }
    }

    private void drawBeacon(Canvas c, double t) {
        // A passing lamp brightens briefly at the illustrated lighthouse lens.
        float pulse = .5f + .5f * wave(t * .92);
        pulse *= pulse;
        halo(c, sunlight, 775, 420, 12 + 46 * pulse, 7 + 12 * pulse, (int) (32 + 142 * pulse));
    }

    private void drawStars(Canvas c, double t, int[] stars) {
        for (int i = 0; i < stars.length / 2; i++) {
            float x = stars[2*i], y = stars[2*i+1];
            float pulse = .5f + .5f * wave(t * (.76 + .09 * (i % 4)) + i * 2.4);
            pulse *= pulse;
            halo(c, starlight, x, y, 6 + 8*pulse, 6 + 8*pulse, (int) (18 + 102*pulse));
            paint.setColor(0xfff4faff);
            paint.setAlpha((int) (40 + 208*pulse));
            c.drawCircle(x, y, 1.6f + 1.5f*pulse, paint);
            if (i % 3 == 0 && pulse > .55f) {
                paint.setAlpha((int) (200 * (pulse - .55f)));
                paint.setStrokeWidth(1.5f);
                c.drawLine(x - 6*pulse, y, x + 6*pulse, y, paint);
                c.drawLine(x, y - 8*pulse, x, y + 8*pulse, paint);
            }
        }
    }

    private void drawFireflies(Canvas c, double t) {
        for (int i = 0; i < FIREFLIES.length / 2; i++) {
            double phase = i * 2.39;
            float pulse = .5f + .5f * wave(t * (.92 + .1 * (i % 3)) + phase);
            pulse *= pulse;
            float x = FIREFLIES[2*i] + 24 * wave(t * .66 + phase) + 7 * wave(t * 1.16 + i);
            float y = FIREFLIES[2*i+1] + 14 * wave(t * .83 + i) + 4 * wave(t * 1.7 + phase);
            float radius = 7 + 10*pulse;
            halo(c, firelight, x, y, radius, radius, (int) (140*pulse));
            paint.setColor(0xfffff9b1);
            paint.setAlpha((int) (22 + 225*pulse));
            c.drawCircle(x, y, 2 + 1.7f*pulse, paint);
        }
    }

    private void drawWindows(Canvas c, double t) {
        for (int i = 0; i < WINDOWS.length / 4; i++) {
            int j = 4*i;
            float l = WINDOWS[j], top = WINDOWS[j+1], r = WINDOWS[j+2], b = WINDOWS[j+3];
            // Independent occupied/unoccupied plateaus with a short, soft transition.
            float level = Math.min(1f, Math.max(-1f,
                    2.2f * wave(t * (.52 + .06 * (i % 6)) + i * 2.39)));
            if (level > 0) {
                halo(c, sunlight, (l+r)*.5f, (top+b)*.5f, 10, 8, (int) (87*level));
                paint.setColor(0xffffe4a5);
                paint.setAlpha((int) (221*level));
            } else {
                paint.setColor(0xff2e2c36);
                paint.setAlpha((int) (-114*level));
            }
            c.drawRect(l, top, r, b, paint);
        }
    }
}
