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
import android.graphics.Path;
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
    private enum Scene { SKY, RIDGE, CITY, COAST, ALPINE, BAMBOO, PRISM, CIRCUIT, PAW, FLUX, GENERIC }

    private static final float ART_SIZE = 960f;
    // Bright star centers in watch_stars.webp, in original artwork pixels.
    private static final int[] STARS = {
            82,191, 202,465, 311,296, 452,230, 614,308, 705,280, 704,479, 204,110
    };
    private static final int[] CIRCUIT_X = {45, 184, 245, 358, 434, 542, 616, 794, 890};
    private static final int[] CIRCUIT_Y = {688, 688, 633, 633, 702, 702, 574, 574, 510};
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
    private final Path trail = new Path();
    private final Rect visible = new Rect();
    private final RadialGradient sunlight = glow(0xffffd892);
    private final RadialGradient starlight = glow(0xffc4e4ff);
    private final RadialGradient ember = glow(0xffff843a);
    private final RadialGradient aqua = glow(0xff4bf1ed);
    private final RadialGradient orchid = glow(0xfffa63c2);
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
        Scene next = Scene.GENERIC;
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
        drawScene(canvas, elapsedMs / 1000.0);
        canvas.restoreToCount(save);
    }

    void drawScene(Canvas c, double t) {
        switch (scene) {
            case SKY, GENERIC -> {
                if (night) drawStars(c, t, STARS);
                else drawSun(c, t);
            }
            case RIDGE -> drawEmbers(c, t);
            case CITY -> drawCity(c, t);
            case COAST -> drawBeacon(c, t);
            case ALPINE -> drawSnow(c, t);
            case BAMBOO -> drawLeaves(c, t);
            case PRISM -> drawPrism(c, t);
            case CIRCUIT -> drawCircuit(c, t);
            case PAW -> drawPaws(c, t);
            case FLUX -> drawRibbons(c, t);
        }
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
        int save = c.save();
        c.translate(215 + 96 * wave(t * .64), -95);
        for (int i = 0; i < 3; i++) {
            int beam = c.save();
            c.rotate(-13 - i * 13 + 8 * wave(t * .72 + i));
            halo(c, sunlight, 0, 365, i == 1 ? 54 : 82, 505,
                    (int) (95 + 24 * wave(t * 1.1 + i)));
            c.restoreToCount(beam);
        }
        c.restoreToCount(save);
        // Broad, soft pools of reflected light drift along the ground/river line.
        for (int i = 0; i < 5; i++) {
            float x = 108 + i * 164 + 43 * wave(t * .77 + i);
            float y = 631 + (i % 3) * 34 + 10 * wave(t * .64 + i * 1.7);
            halo(c, sunlight, x, y, 56 + (i % 2) * 32, 13 + i % 3 * 6,
                    (int) (70 + 30 * wave(t * 1.4 + i * 2.3)));
        }
    }

    private static float phase(double time, double period) {
        return (float) ((time % period) / period);
    }

    private static float fade(float progress) {
        return wave(Math.PI * progress);
    }

    private void drawEmbers(Canvas c, double t) {
        float flicker = .5f + .5f * wave(t * 5 + wave(t * 3));
        halo(c, ember, 480, 635, 86 + 24 * flicker, 36 + 18 * flicker, night ? 158 : 108);
        for (int i = 0; i < 12; i++) {
            float progress = phase(t + i * .53, 4.2 + i % 3 * .6);
            float x = 480 + (i % 5 - 2) * (11 + progress * 17) + 29 * wave(progress * 4 + i);
            float y = 632 - progress * (176 + i % 4 * 26);
            float light = fade(progress);
            halo(c, ember, x, y, 9 + light * 15, 9 + light * 15, (int) (130 * light));
            paint.setColor(night ? 0xffffe1a9 : 0xffffb05f);
            paint.setAlpha((int) (242 * light));
            c.drawCircle(x, y, 2.5f + light * 2.4f, paint);
        }
    }

    private void drawBeacon(Canvas c, double t) {
        float swing = wave(t * .84);
        float shine = .72f + .28f * wave(t * .84 + Math.PI / 2);
        int save = c.save();
        c.rotate(14 * swing, 775, 420);
        halo(c, starlight, 556, 420, 218, 21, (int) ((night ? 136 : 102) * shine));
        halo(c, sunlight, 687, 420, 95, 6, (int) (113 * shine));
        c.restoreToCount(save);
        halo(c, sunlight, 775, 420, 23, 19, (int) (177 * shine));
    }

    private void drawCity(Canvas c, double t) {
        if (night) drawWindows(c, t);
        for (int i = 0; i < 5; i++) {
            float travel = phase(t + i * 1.21, 6.5);
            float x = 72 + 816 * (i % 2 == 0 ? travel : 1 - travel);
            float y = 505 + i * 28;
            RadialGradient tint = i % 2 == 0 ? sunlight : starlight;
            halo(c, tint, x, y, 61, 6, (int) (199 * fade(travel)));
        }
    }

    private void drawSnow(Canvas c, double t) {
        for (int i = 0; i < 26; i++) {
            float travel = phase(t + i * 1.87, 7.8 + i % 4 * 1.4);
            float x = 55 + (i * 137 % 826) + 32 * wave(t * .6 + i) + travel * 47;
            float y = 65 + travel * 806;
            float light = fade(travel);
            paint.setColor(night ? 0xffdeefff : 0xffeaffff);
            paint.setAlpha((int) ((night ? 220 : 178) * light));
            float radius = 3 + i % 4;
            c.drawCircle(x, y, radius, paint);
            if (i % 5 == 0) {
                paint.setStrokeWidth(2);
                c.drawLine(x - radius * 1.8f, y, x + radius * 1.8f, y, paint);
                c.drawLine(x, y - radius * 1.8f, x, y + radius * 1.8f, paint);
            }
        }
    }

    private void drawLeaves(Canvas c, double t) {
        for (int i = 0; i < 12; i++) {
            float travel = phase(t + i * 1.62, 8 + i % 3);
            float x = 72 + (i * 157 % 775) + 58 * wave(travel * 7 + i);
            float y = 228 + 566 * travel;
            int save = c.save();
            c.rotate(-32 + 56 * wave(t * .85 + i), x, y);
            paint.setColor(night ? 0xffb6d779 : 0xffd7f0a2);
            paint.setAlpha((int) (218 * fade(travel)));
            c.drawOval(x - 11, y - 3, x + 12, y + 3, paint);
            c.restoreToCount(save);
        }
    }

    private void drawPrism(Canvas c, double t) {
        float travel = phase(t, 6.8);
        int save = c.save();
        c.rotate(32, 480, 480);
        float x = -30 + 1010 * travel;
        int light = (int) ((night ? 181 : 146) * fade(travel));
        halo(c, aqua, x - 20, 480, 62, 570, light);
        halo(c, sunlight, x + 9, 480, 13, 565, light);
        halo(c, orchid, x + 37, 480, 34, 520, light * 2 / 3);
        c.restoreToCount(save);
    }

    private void drawCircuit(Canvas c, double t) {
        for (int i = 0; i < 3; i++) {
            float progress = phase(t + i * 1.84, 5.6) * (CIRCUIT_X.length - 1);
            int j = Math.min((int) progress, CIRCUIT_X.length - 2);
            float fraction = progress - j;
            float x = CIRCUIT_X[j] + (CIRCUIT_X[j + 1] - CIRCUIT_X[j]) * fraction;
            float y = CIRCUIT_Y[j] + (CIRCUIT_Y[j + 1] - CIRCUIT_Y[j]) * fraction - i * 59;
            halo(c, night ? aqua : sunlight, x, y, 34, 21, 185);
            paint.setColor(night ? 0xffc0ffff : 0xffffe5a4);
            c.drawCircle(x, y, 5.2f, paint);
        }
    }

    private void drawPaws(Canvas c, double t) {
        for (int i = 0; i < 4; i++) {
            float progress = phase(t + (3 - i) * .62, 6);
            if (progress > .62f) continue;
            float light = fade(progress / .62f);
            float x = 140 + i * 96, y = 725 - i * 66;
            int save = c.save();
            c.translate(x, y);
            c.rotate(i % 2 == 0 ? 26 : 40);
            c.scale(.85f + .2f * light, .85f + .2f * light);
            paint.setColor(night ? 0xffffc797 : 0xffa64074);
            paint.setAlpha((int) (174 * light));
            c.drawOval(-13, 0, 13, 21, paint);
            for (int toe = 0; toe < 4; toe++)
                c.drawCircle((toe - 1.5f) * 9, toe == 1 || toe == 2 ? -10 : -5, 4.6f, paint);
            c.restoreToCount(save);
        }
    }

    private void drawRibbons(Canvas c, double t) {
        for (int i = 0; i < 3; i++) {
            int y = 555 + i * 65;
            float curl = 83 * wave(t * .61 + i * .83);
            trail.rewind();
            trail.moveTo(-40, y + 70);
            trail.cubicTo(198, y + curl, 566, y - curl - 162, 1000, y - 33);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(i == 1 ? 0xffff7ba9 : 0xff75caff);
            paint.setAlpha(night ? 58 : 46);
            paint.setStrokeWidth(i == 1 ? 13 : 7);
            c.drawPath(trail, paint);
            paint.setStyle(Paint.Style.FILL);
            halo(c, i == 1 ? orchid : aqua, 375 + 128 * wave(t * .6 + i), y - 26, 153, 20,
                    (int) (68 + 24 * wave(t + i)));
        }
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
