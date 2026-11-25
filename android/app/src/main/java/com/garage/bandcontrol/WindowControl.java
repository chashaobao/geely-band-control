package com.garage.bandcontrol;

import static com.garage.bandcontrol.ControlDirection.CLOSE;
import static com.garage.bandcontrol.ControlDirection.OPEN;

final class WindowControl {
    record Decision(ControlDirection direction, String blockedReason) {}

    private static final Decision OPEN_WINDOW = new Decision(OPEN, "");
    private static final Decision CLOSE_WINDOW = new Decision(CLOSE, "");
    private static final Decision UNKNOWN = new Decision(null, "请刷新银河卡片，核对车窗状态");
    private static final Decision CONFLICT = new Decision(null, "车窗状态与按钮图标不一致，请刷新车况");
    private static final Decision NO_CLOSE = new Decision(null, "车窗未关，当前卡片按钮未显示关窗方向");

    private WindowControl() {}

    static Decision resolve(String status, ControlDirection iconDirection, boolean activeWindowSlot) {
        boolean opened = false;
        boolean closed = false;
        for (String warning : status.split("[\\s\\u3000、，,；;。|/]+")) {
            switch (warning) {
                case "车窗未关", "车窗未关闭" -> opened = true;
                case "车窗已关", "车窗已关闭" -> closed = true;
            }
        }
        if (opened && closed) return CONFLICT;
        if (opened) return iconDirection == CLOSE ? CLOSE_WINDOW : NO_CLOSE;
        if (!closed && !"已驻车".equals(status) && !"已停车".equals(status)
                && !"车门未锁".equals(status) && !"车门未上锁".equals(status)) return UNKNOWN;
        if (activeWindowSlot) return CONFLICT;
        return iconDirection == OPEN ? OPEN_WINDOW : UNKNOWN;
    }
}
