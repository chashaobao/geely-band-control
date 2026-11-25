package com.garage.bandcontrol;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class MiHomeVoice {
    private static final String PACKAGE = "com.xiaomi.smarthome";
    private static final String[] FIELDS = {"正文", "展开正文", "标题"};
    static final long MAX_AGE_MS = 30_000;
    static final long CONTROL_REPEAT_MS = 5_000;
    private static final int RECENT_LIMIT = 64;

    enum Command {
        TEST(null, "测试", null),
        LOCK(CarAction.LOCK, "上锁", ControlDirection.LOCK),
        UNLOCK(CarAction.LOCK, "解锁", ControlDirection.UNLOCK),
        CLIMATE(CarAction.CLIMATE),
        WINDOW_VENT(CarAction.WINDOW_VENT, CarAction.WINDOW_VENT.label, ControlDirection.OPEN),
        TRUNK(CarAction.TRUNK),
        FIND(CarAction.FIND),
        WINDOW_OPEN(CarAction.WINDOW_OPEN, CarAction.WINDOW_OPEN.label, ControlDirection.OPEN),
        HEAT(CarAction.HEAT),
        COOL(CarAction.COOL),
        PURIFY(CarAction.PURIFY),
        DEFROST(CarAction.DEFROST),
        SENTRY(CarAction.SENTRY);

        final String code;
        final CarAction action;
        final String label;
        private final ControlDirection requiredDirection;

        Command(CarAction action) {
            this(action, action.label, null);
        }

        Command(CarAction action, String label, ControlDirection requiredDirection) {
            this.code = "GARAGE_V1:" + name();
            this.action = action;
            this.label = label;
            this.requiredDirection = requiredDirection;
        }

        String directionBlock(ControlDirection direction) {
            return requiredDirection == null || requiredDirection == direction
                    ? "" : "当前卡片没有「" + label + "」方向的按钮";
        }

        static Command fromText(CharSequence text) {
            for (Command command : values()) {
                if (text != null && command.code.contentEquals(text)) return command;
            }
            return null;
        }
    }

    enum Admission { ACCEPTED, STALE, DUPLICATE }

    record Match(Command command, String field) {}
    private record Notice(String key, long postedAt) {}

    private final Set<Notice> notices = new LinkedHashSet<>();
    private final Map<Command, Long> nextControlAt = new EnumMap<>(Command.class);

    static boolean isSource(String packageName) { return PACKAGE.equals(packageName); }

    static Match match(CharSequence text, CharSequence bigText, CharSequence title) {
        CharSequence[] values = {text, bigText, title};
        Match match = null;
        for (int index = 0; index < values.length; index++) {
            Command command = Command.fromText(values[index]);
            if (command == null) continue;
            if (match == null) match = new Match(command, FIELDS[index]);
            else if (match.command() != command) return null;
        }
        return match;
    }

    Admission receive(String key, long postedAt, long now, long elapsed, Command command) {
        long age = now - postedAt;
        if (postedAt <= 0 || age < 0 || age > MAX_AGE_MS) return Admission.STALE;
        notices.removeIf(notice -> now - notice.postedAt() > MAX_AGE_MS);
        if (!notices.add(new Notice(key, postedAt))) return Admission.DUPLICATE;
        if (notices.size() > RECENT_LIMIT) notices.remove(notices.iterator().next());
        if (command.action != null) {
            if (elapsed < nextControlAt.getOrDefault(command, 0L)) return Admission.DUPLICATE;
            nextControlAt.put(command, elapsed + CONTROL_REPEAT_MS);
        }
        return Admission.ACCEPTED;
    }
}
