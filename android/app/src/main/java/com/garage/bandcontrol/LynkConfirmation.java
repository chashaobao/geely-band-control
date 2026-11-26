package com.garage.bandcontrol;

/** Matches the official prompt to an already authorized, explicitly identified action. */
enum LynkConfirmation {
    LOCK("上锁车门", "确认上锁车辆吗", "确认锁车吗", "确认锁定车辆吗"),
    UNLOCK("解锁车门", "确认解锁车辆吗", "确认解锁车门吗"),
    VENT("微开车窗", "确认微开车窗吗", "确认车窗微开吗"),
    OPEN("全开车窗", "确认打开车窗吗", "确认全开车窗吗", "确认车窗全开吗"),
    CLOSE("关闭车窗", "确认关闭车窗吗", "确认关窗吗"),
    WINDOW_TOGGLE("切换车窗"),
    LOCK_TOGGLE("切换车锁");

    final String label;
    private final String[] titles;

    LynkConfirmation(String label, String... titles) {
        this.label = label;
        this.titles = titles;
    }

    static LynkConfirmation forMark(String ownerCategory, ControlDirection direction) {
        if ("锁".equals(ownerCategory)) {
            if (direction == ControlDirection.LOCK) return LOCK;
            if (direction == ControlDirection.UNLOCK) return UNLOCK;
        }
        boolean vent = "车窗微开".equals(ownerCategory);
        if (vent || "车窗全开".equals(ownerCategory)) {
            if (direction == ControlDirection.CLOSE) return CLOSE;
            if (direction == ControlDirection.OPEN) return vent ? VENT : OPEN;
        }
        return null;
    }

    boolean matches(String title) {
        if (this == WINDOW_TOGGLE) return VENT.matches(title) || OPEN.matches(title) || CLOSE.matches(title);
        if (this == LOCK_TOGGLE) return LOCK.matches(title) || UNLOCK.matches(title);
        String text = title.trim();
        if (text.endsWith("？") || text.endsWith("?")) text = text.substring(0, text.length() - 1).trim();
        for (String candidate : titles) if (candidate.equals(text)) return true;
        return false;
    }
}
