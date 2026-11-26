package com.garage.bandcontrol;

/** Owner supplied evidence. Automatic icon labels do not create or refresh a mark. */
record LynkMark(String slot, String fingerprint, String description, String category, String direction) {
    static final java.util.List<String> CATEGORIES = java.util.List.of("锁", "车窗微开", "车窗全开", "发动机", "快速降温", "快速升温", "天窗", "后备箱");
    boolean matches(String slot, String fingerprint, String description) {
        return this.slot.equals(slot) && this.fingerprint.equals(fingerprint) && this.description.equals(description);
    }
    boolean permits(MiHomeVoice.Command command) {
        return switch (command) {
            case LOCK, UNLOCK -> slot.equals("fr_unlock") && category.equals("锁");
            case WINDOW_VENT -> slot.equals("fr_window") && category.equals("车窗微开");
            case WINDOW_OPEN -> slot.equals("fr_window") && category.equals("车窗全开");
            case WINDOW_CLOSE -> slot.equals("fr_window") && category.startsWith("车窗");
            default -> false;
        };
    }
}
