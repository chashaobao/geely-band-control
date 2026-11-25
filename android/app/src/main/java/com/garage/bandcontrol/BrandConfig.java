package com.garage.bandcontrol;

import java.util.List;

/** Immutable mapping; Lynk identities are static APK evidence, not device acceptance. */
final class BrandConfig {
    enum Brand { GALAXY, LYNKCO }
    enum SlotMode { DYNAMIC, FIXED }
    static final BrandConfig GALAXY = new BrandConfig(Brand.GALAXY);
    static final BrandConfig LYNKCO = new BrandConfig(Brand.LYNKCO);
    final Brand brand;
    final String name;
    final SlotMode slotMode;
    final int heightDp, hostId;
    final String cardsKey;
    final List<String> slots, energyBars, loading;
    final String packageName;
    final String provider;
    final String normalLayout;
    final String setupLayout;
    final String settings;
    final String refresh;
    final String confirm;
    final String accept;
    final String cancel;
    final String confirmTitle;
    final String mileage;
    final String mileageValue;
    final String mileageUnit;
    final String electric;
    final String electricBar;
    final String oil;
    final String oilBar;
    final String alarm;
    final String motion;
    final String time;
    final String energyValue;
    final String energyUnit;
    final String slotLoading;
    final String slotIcon;
    final String iconPrefix;
    final String namespace;
    private BrandConfig(Brand brand) {
        this.brand = brand;
        boolean lynk = brand == Brand.LYNKCO;
        name = lynk ? "领克" : "银河";
        slotMode = lynk ? SlotMode.FIXED : SlotMode.DYNAMIC;
        heightDp = lynk ? 155 : 175;
        hostId = lynk ? 0x4c43 : 0x4742;
        cardsKey = lynk ? "lynkcoWidgetCards" : "widgetCards";
        slots = lynk ? List.of("fr_unlock", "fr_window", "fr_kjck", "fr_ccqk") : List.of("wgFun1Container", "wgFun2Container", "wgFun3Container", "wgFun4Container");
        energyBars = lynk ? List.of("progress_bar_electricity", "progress_bar_h") : List.of("wg_pb_energy_normal", "wg_pb_energy_warn", "wg_pb_energy_alert");
        loading = lynk ? List.of("fr_sx", "ll_loading_auth") : List.of("wg_view_update_time_loading", "wg_tv_update_time_loading");
        packageName = lynk ? "com.lynkco.customer" : "com.geelygalaxy.customer";
        provider = lynk ? "com.lynkco.vehicle.widget.ListWidgetProvider" : "com.geely.galaxy.car.weight.appwidget.WgProviderMiddle";
        normalLayout = lynk ? "layout_widget" : "wg_middle_normal";
        setupLayout = lynk ? null : "wg_middle_init";
        settings = lynk ? "ic_bj_a" : "wg_btn_setting";
        refresh = lynk ? "ic_widget_sx" : "wg_btn_update_time";
        confirm = lynk ? "verify" : "wg_middle_layout_confirm";
        accept = lynk ? "tv_verify" : "wg_middle_confirm_btn_ok";
        cancel = lynk ? "tv_cancel" : "wg_middle_confirm_btn_cancel";
        confirmTitle = lynk ? "ty_confirm_title" : "wg_middle_confirm_title";
        mileage = lynk ? "mileage" : "wg_view_mileage";
        mileageValue = lynk ? "tv_mileage" : "wg_tv_mileage";
        mileageUnit = lynk ? "txt_km" : "wg_tv_mileage_unit";
        electric = lynk ? "ll_electricity" : "wg_energy_container_electric";
        electricBar = lynk ? "progress_bar_electricity" : "wg_energy_bar_electric";
        oil = lynk ? "ly_oil" : "wg_energy_container_oil";
        oilBar = lynk ? "progress_bar_h" : "wg_energy_bar_oil";
        alarm = lynk ? "condition_ll" : "wg_alarm_container";
        motion = lynk ? "condition_txt" : "wg_tv_warn_text";
        time = lynk ? "time" : "wg_tv_update_time";
        energyValue = lynk ? null : "wg_tv_energy_percent";
        energyUnit = lynk ? null : "wg_tv_energy_percent_unit";
        slotLoading = lynk ? null : "wg_view_fun_loading";
        slotIcon = lynk ? null : "wg_view_fun_status";
        iconPrefix = lynk ? null : "car_wg_funicon_";
        namespace = lynk ? "LYNKCO_V1" : "GARAGE_V1";
    }
    static BrandConfig fromId(String id) { return "LYNKCO".equals(id) ? LYNKCO : GALAXY; }
}
