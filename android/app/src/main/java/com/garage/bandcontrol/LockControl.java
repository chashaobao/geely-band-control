package com.garage.bandcontrol;

final class LockControl {
    private LockControl() {}

    static String blockedReason(boolean validReading, String status, String lockState) {
        boolean unlocked = "unlocked".equals(lockState);
        if (!unlocked && !"locked".equals(lockState)) return "请在手机核对车门锁态";
        if (!validReading) return "请点车况时间刷新银河卡片";

        boolean recognized = false;
        boolean doorUnlocked = false;
        for (String warning : status.split("[\\s\\u3000、，,；;。|/]+")) {
            switch (warning) {
                case "行驶中", "车辆行驶中", "正在行驶", "驾驶中", "未驻车" -> {
                    return "银河卡片显示车辆未驻车";
                }
                case "车门未锁", "车门未上锁" -> {
                    recognized = true;
                    doorUnlocked = true;
                }
                case "已驻车", "已停车", "车窗未关", "车窗未关闭", "天窗未关", "天窗未关闭",
                        "车门未关", "车门未关闭", "后备箱未关", "后备箱未关闭" -> recognized = true;
                default -> { }
            }
        }
        if (doorUnlocked && !unlocked) return "银河显示车门未锁，锁图标状态不一致";
        return recognized ? "" : "银河卡片状态待核对，请刷新车况";
    }
}
