package com.garage.bandcontrol;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

final class WidgetTree {
    static final String NORMAL_LAYOUT = "wg_middle_normal";
    static final String SETUP_LAYOUT = "wg_middle_init";
    static final String SETTINGS = "wg_btn_setting";
    static final String REFRESH = "wg_btn_update_time";
    static final String LOCKED = "locked";
    static final String UNLOCKED = "unlocked";
    private static final String CONFIRMATION = "wg_middle_layout_confirm";
    private static final String ENERGY_VALUE = "wg_tv_energy_percent";
    private static final String ENERGY_UNIT = "wg_tv_energy_percent_unit";
    private static final List<String> ENERGY_BARS = List.of("wg_pb_energy_normal", "wg_pb_energy_warn",
            "wg_pb_energy_alert");
    static final List<String> SLOTS = List.of("wgFun1Container", "wgFun2Container", "wgFun3Container", "wgFun4Container");

    record Button(View view, String slot, String fingerprint, Rect bounds, String label, String role,
                  WidgetIcons.Match match) {}
    record OfficialConfirmation(View accept, View cancel, String title) {}

    private WidgetTree() {}

    static VehicleStatus read(View root, long now, ZoneId zone) {
        if (loading(root)) return VehicleStatus.EMPTY;
        View mileage = named(root, "wg_view_mileage");
        View alarm = named(root, "wg_alarm_container");
        View motion = named(alarm, "wg_tv_warn_text");
        if (motion == null) motion = unique(alarm, view -> view instanceof TextView);
        return WidgetReadings.parse(distance(mileage, "wg_tv_mileage", "wg_tv_mileage_unit"),
                energy(root, "wg_energy_container_electric", "wg_energy_bar_electric"),
                energy(root, "wg_energy_container_oil", "wg_energy_bar_oil"),
                text(motion), text(named(root, "wg_tv_update_time")), now, zone);
    }

    static boolean loading(View root) {
        return named(root, "wg_view_update_time_loading") != null || named(root, "wg_tv_update_time_loading") != null;
    }

    static boolean slotLoading(View root, String slotName) {
        return slotLoading(named(root, slotName));
    }

    private static boolean slotLoading(View slot) {
        return named(slot, "wg_view_fun_loading") != null;
    }

    private static WidgetReadings.Energy energy(View root, String containerName, String barName) {
        // RemoteViews can replace the included progress bar inside the energy container.
        View container = named(root, containerName);
        if (container == null) container = named(root, barName);
        Map<Integer, WidgetReadings.Distance> distances = new LinkedHashMap<>();
        collectEnergyDistances(container, distances);
        WidgetReadings.Distance distance = distances.size() == 1 ? distances.values().iterator().next()
                : new WidgetReadings.Distance("", "");
        View visibleBar = unique(container, view -> view instanceof ProgressBar && ENERGY_BARS.contains(resource(view)));
        return new WidgetReadings.Energy(distance, progressPercent(visibleBar));
    }

    private static Integer progressPercent(View view) {
        if (!(view instanceof ProgressBar bar) || bar.isIndeterminate()) return null;
        int minimum = bar.getMin();
        int maximum = bar.getMax();
        int progress = bar.getProgress();
        if (maximum <= minimum || progress < minimum || progress > maximum) return null;
        return (int) Math.round(100.0 * (progress - (long) minimum) / (maximum - (long) minimum));
    }

    private static void collectEnergyDistances(View view, Map<Integer, WidgetReadings.Distance> distances) {
        if (view == null || view.getVisibility() != View.VISIBLE || distances.size() > 1) return;
        if (view instanceof TextView) {
            String unit = "";
            if (ENERGY_VALUE.equals(resource(view)) && view.getParent() instanceof ViewGroup row) {
                unit = text(unique(row, other -> other.getParent() == row && ENERGY_UNIT.equals(resource(other))));
            }
            WidgetReadings.Distance candidate = new WidgetReadings.Distance(text(view), unit);
            Integer km = candidate.kilometers();
            if (km != null) distances.putIfAbsent(km, candidate);
        } else if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collectEnergyDistances(group.getChildAt(i), distances);
        }
    }

    private static WidgetReadings.Distance distance(View container, String number, String unit) {
        return new WidgetReadings.Distance(text(named(container, number)), text(named(container, unit)));
    }

    static List<Button> buttons(ViewGroup root, String providerVersion, boolean controlsReady, WidgetIcons icons) {
        List<Button> buttons = new ArrayList<>();
        if (confirmationVisible(root)) return buttons;

        View settings = named(root, SETTINGS);
        if (clickable(settings, root)) {
            buttons.add(new Button(settings, SETTINGS, "", bounds(root, settings), "配置", SETTINGS, null));
        }
        View refresh = named(root, REFRESH);
        if (!loading(root) && clickable(refresh, root)) {
            buttons.add(new Button(refresh, REFRESH, "", bounds(root, refresh), "刷新车况", REFRESH, null));
        }

        if (!controlsReady) return buttons;
        int lockIcons = 0;
        for (String slotName : SLOTS) {
            View slot = named(root, slotName);
            if (slot == null || slotLoading(slot)) continue;
            View icon = named(slot, "wg_view_fun_status");
            if (!(icon instanceof ImageView image) || image.getDrawable() == null
                    || icon.getWidth() <= 0 || icon.getHeight() <= 0) continue;
            try {
                WidgetIcons.Match match = icons.identify(image, providerVersion);
                if (match != null && match.action() == CarAction.LOCK) lockIcons++;
                View click = unique(slot, view -> clickable(view, root));
                if (click == null) continue;
                String label = description(click);
                if (label.isEmpty()) label = description(icon);
                String identity = providerVersion + "|" + slotName + "|" + resource(click) + "|" + label;
                buttons.add(new Button(click, slotName, fingerprint(icon, identity), bounds(root, click), label, "", match));
            } catch (RuntimeException unsupportedDrawable) {
                // A failed icon render leaves this slot unavailable until its identity can be checked.
            }
        }
        // The unconfigured template uses the closed-lock image in every slot.
        if (lockIcons > 1) {
            buttons.removeIf(WidgetTree::recognizedLock);
        }
        return buttons;
    }

    private static boolean recognizedLock(Button button) {
        return button.match() != null && button.match().action() == CarAction.LOCK;
    }

    static boolean confirmationVisible(View root) {
        return named(root, CONFIRMATION) != null;
    }

    private static final List<String> NON_CAR_CONTAINERS = List.of(
            "wgFun1Container", "wgFun2Container", "wgFun3Container", "wgFun4Container",
            "wg_energy_container_electric", "wg_energy_container_oil",
            "wg_energy_bar_electric", "wg_energy_bar_oil",
            "wg_alarm_container", "wg_view_mileage", CONFIRMATION, SETTINGS, REFRESH);

    static ImageView vehicleImage(ViewGroup root) {
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return null;
        List<ImageView> candidates = new ArrayList<>();
        collectVehicleImages(root, candidates);
        ImageView best = null;
        int bestScore = -1;
        for (ImageView candidate : candidates) {
            int w = candidate.getWidth();
            int h = candidate.getHeight();
            if (w < 48 || h < 24) continue;
            if (w >= root.getWidth() * 0.92f && h >= root.getHeight() * 0.85f) continue;
            if (w <= h * 1.15f && w < root.getWidth() * 0.25f) continue;
            String name = resource(candidate).toLowerCase(java.util.Locale.ROOT);
            int bonus = (name.contains("car") || name.contains("vehicle") || name.contains("model")) ? 100_000 : 0;
            int score = bonus + w * h;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static void collectVehicleImages(View view, List<ImageView> out) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        String entry = resource(view);
        if (NON_CAR_CONTAINERS.contains(entry)) return;
        if (view instanceof ImageView image) {
            if (image.getDrawable() != null && image.getWidth() > 0 && image.getHeight() > 0) {
                out.add(image);
            }
        } else if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collectVehicleImages(group.getChildAt(i), out);
        }
    }

    static OfficialConfirmation confirmation(ViewGroup root) {
        View panel = named(root, CONFIRMATION);
        View accept = named(panel, "wg_middle_confirm_btn_ok");
        View cancel = named(panel, "wg_middle_confirm_btn_cancel");
        String title = text(named(panel, "wg_middle_confirm_title"));
        if (title.isEmpty() || !clickable(accept, root) || !clickable(cancel, root)) return null;
        return new OfficialConfirmation(accept, cancel, title);
    }

    private static boolean clickable(View view, View root) {
        if (view == null || !view.isClickable() || !view.hasOnClickListeners()) return false;
        for (View current = view; current != null; ) {
            if (!current.isEnabled() || current.getVisibility() != View.VISIBLE
                    || current.getWidth() <= 0 || current.getHeight() <= 0) return false;
            if (current == root) return true;
            current = current.getParent() instanceof View parent ? parent : null;
        }
        return false;
    }

    private static String description(View view) {
        CharSequence label = view.getContentDescription();
        return label == null ? "" : label.toString().trim();
    }

    private static String text(View view) {
        return view instanceof TextView text ? text.getText().toString().trim() : "";
    }

    private static View named(View root, String name) {
        return unique(root, view -> name.equals(resource(view)));
    }

    private static View unique(View root, Predicate<View> matches) {
        List<View> found = new ArrayList<>(2);
        collect(root, matches, found);
        return found.size() == 1 ? found.get(0) : null;
    }

    private static void collect(View view, Predicate<View> matches, List<View> found) {
        if (view == null || view.getVisibility() != View.VISIBLE || found.size() >= 2) return;
        if (matches.test(view)) found.add(view);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), matches, found);
        }
    }

    private static Rect bounds(ViewGroup root, View view) {
        Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
        root.offsetDescendantRectToMyCoords(view, rect);
        return rect;
    }

    private static String resource(View view) {
        if (view.getId() == View.NO_ID) return "";
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Resources.NotFoundException invalid) {
            return "";
        }
    }

    private static String fingerprint(View view, String descriptor) {
        Bitmap image = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(image);
            canvas.scale(48f / view.getWidth(), 48f / view.getHeight());
            view.draw(canvas);
            ByteBuffer pixels = ByteBuffer.allocate(image.getByteCount());
            image.copyPixelsToBuffer(pixels);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(descriptor.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(pixels.array()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        } finally {
            image.recycle();
        }
    }
}
