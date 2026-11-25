package com.garage.bandcontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import java.util.function.IntConsumer;

/** Fixed slot identities from 4.2.9/4.3.0. No inferred functional directions. */
final class LynkWidgets extends GalaxyWidgets {
    LynkWidgets(Context context, SharedPreferences prefs, Handler main, IntConsumer onChange) {
        super(context, prefs, main, onChange, BrandConfig.LYNKCO);
    }
    static Binding binding(WidgetTree.Button button) {
        String action = switch (button.slot()) {
            case "fr_unlock" -> "lynk_unlock";
            case "fr_window" -> "lynk_window";
            case "fr_kjck" -> "lynk_kjck";
            case "fr_ccqk" -> "lynk_ccqk";
            default -> "";
        };
        return action.isEmpty() ? null : new Binding(button.slot(), button.fingerprint(), action, "");
    }
}
