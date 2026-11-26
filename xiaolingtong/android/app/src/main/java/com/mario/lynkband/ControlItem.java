package com.mario.lynkband;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import android.widget.ImageView;
import com.mario.lynkband.transport.ControlCategory;
import java.util.Arrays;

/** State and action are intentionally separate: a green, open window offers Close. */
record ControlItem(ControlCategory category, String state, String action, int matchedDrawable) {
    private ControlItem(ControlCategory category, String state, String action) { this(category, state, action, 0); }
    private record Match(String direction, int drawable) {}
    private static final String[] ICON_SUFFIXES = {"on", "on2", "ons", "on3", "off", "off2", "offs", "off3"};
    private record Reference(Resources resources, android.content.res.Configuration config, int id) {}
    private static final class ReferenceIcons {
        static final LruCache<Reference, int[]> cache = new LruCache<>(48);
    }
    String label() {
        if (category == ControlCategory.OTHER) return "选择按钮类型";
        return category.title + "." + switch (state) {
            case "open" -> "开";
            case "closed" -> "关";
            case "loading" -> "加载";
            default -> "未知";
        };
    }

    org.json.JSONObject wireAction(CardContent.Slot slot, boolean withState, boolean permitted) throws org.json.JSONException {
        org.json.JSONObject item = new org.json.JSONObject().put("slot", slot.id()).put("key", slot.signature())
                .put("label", action.isEmpty() ? label() : action);
        if (withState) {
            item.put("category", category.name()).put("displayLabel", label()).put("controlState", state)
                    .put("controlName", category == ControlCategory.OTHER ? "待选择" : category.title)
                    .put("enabled", permitted && slot.target() != null && !action.isEmpty());
        }
        return item;
    }

    static ControlItem resolve(ControlCategory category, CardContent.Slot slot) {
        if (!slot.stateVisible()) return new ControlItem(category, "unknown", "");
        if (slot.loading()) return new ControlItem(category, "loading", "");
        if (category == ControlCategory.OTHER || slot.icon() == null)
            return new ControlItem(category, "unknown", "");
        String[] pair = switch (category) {
            case WINDOW -> new String[]{"cc", "开车窗", "关车窗"};
            case CLIMATE -> new String[]{"kt", "开空调", "关空调"};
            case LOCK -> new String[]{"js", "解锁", "上锁"};
            case TRUNK -> new String[]{"hbx", "开后备箱", "关后备箱"};
            case SUNROOF -> new String[]{"tc", "开天窗", "关天窗"};
            case ENGINE -> new String[]{"yq", "启动引擎", "关闭引擎"};
            default -> null;
        };
        if (pair == null) return new ControlItem(category, "unknown", "");
        Match match = recognizedIcon(slot.icon(), pair[0]);
        String direction = match.direction();
        if (direction.isEmpty() && slot.target() != null) {
            String action = CardContent.describedAction(slot);
            if (action.equals(pair[1])) direction = "closed";
            if (action.equals(pair[2])) direction = "open";
        }
        return switch (direction) {
            case "open" -> new ControlItem(category, "open", slot.target() == null ? "" : pair[2], match.drawable());
            case "closed" -> new ControlItem(category, "closed", slot.target() == null ? "" : pair[1], match.drawable());
            default -> new ControlItem(category, "unknown", "");
        };
    }

    private static Match recognizedIcon(ImageView icon, String prefix) {
        Resources res = icon.getResources();
        var config = new android.content.res.Configuration(res.getConfiguration());
        int[] actual = pixels(icon.getDrawable());
        String result = "";
        int matchedId = 0;
        for (String suffix : ICON_SUFFIXES) {
            int id = res.getIdentifier("ic_ck_" + prefix + "_" + suffix, "mipmap", LynkWidget.OWNER);
            if (id == 0) continue;
            Reference key = new Reference(res, config, id);
            int[] reference = ReferenceIcons.cache.get(key);
            if (reference == null) {
                reference = pixels(res.getDrawable(id, null));
                ReferenceIcons.cache.put(key, reference);
            }
            if (Arrays.equals(actual, reference)) {
                String matched = suffix.startsWith("on") ? "open" : "closed";
                if (!result.isEmpty() && !result.equals(matched)) return new Match("", 0);
                matchedId = result.isEmpty() ? id : 0;
                result = matched;
            }
        }
        return new Match(result, matchedId);
    }

    private static int[] pixels(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        Rect bounds = drawable.copyBounds();
        try {
            drawable.setBounds(0, 0, 48, 48);
            drawable.draw(new Canvas(bitmap));
            int[] pixels = new int[48 * 48];
            bitmap.getPixels(pixels, 0, 48, 0, 0, 48, 48);
            return pixels;
        } finally {
            drawable.setBounds(bounds);
            bitmap.recycle();
        }
    }
}
