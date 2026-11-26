package com.mario.lynkband;

import android.content.Context;
import android.content.SharedPreferences;

import com.mario.lynkband.solar.SolarController;
import com.mario.lynkband.solar.ThemeSync;

/** The paired watch shares the landscape choice. Solar settings are local to the phone. */
enum PhoneLandscape {
    SKY("蓝天与星空", R.drawable.phone_day_landscape, R.drawable.watch_stars),
    RIDGE("山脊营地", R.drawable.phone_day_ridge, R.drawable.watch_ridge_night),
    CITY("城市天际线", R.drawable.phone_day_city, R.drawable.watch_city_night),
    COAST("海岸灯塔", R.drawable.phone_day_coast, R.drawable.watch_coast_night),
    ALPINE("雪山映湖", R.drawable.phone_day_alpine, R.drawable.watch_alpine_night);

    static final String KEY = "landscape";
    final String title;
    private final int dayImage, nightImage;

    PhoneLandscape(String title, int dayImage, int nightImage) {
        this.title = title;
        this.dayImage = dayImage;
        this.nightImage = nightImage;
    }

    int image(boolean night) { return night ? nightImage : dayImage; }

    int baseColor(boolean night) {
        return switch (this) {
            case SKY -> night ? 0xff162331 : 0xffc6dfef;
            case RIDGE -> night ? 0xff192a2c : 0xffcedecb;
            case CITY -> night ? 0xff222438 : 0xffd6dce9;
            case COAST -> night ? 0xff152934 : 0xffcce4e5;
            case ALPINE -> night ? 0xff19283b : 0xffd1e3e9;
        };
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("phoneTheme", Context.MODE_PRIVATE);
    }

    static PhoneLandscape selected(SharedPreferences prefs) {
        String name = prefs.getString(KEY, SKY.name());
        for (PhoneLandscape scene : values()) if (scene.name().equals(name)) return scene;
        return SKY;
    }

    void select(SharedPreferences prefs) {
        ThemeSync.select(prefs, KEY, name(), true);
    }

    static boolean night(Context context) {
        return SolarController.initialNight(context, preferences(context));
    }
}
