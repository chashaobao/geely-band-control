package com.garage.bandcontrol;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WidgetReadings {
    private static final Pattern RANGE = Pattern.compile(
            "(\\d{1,3}(?:,\\d{3})|\\d{1,5})\\s*(?:km|㎞|公里)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATED = Pattern.compile(
            "(?:(\\d{4})[-/年])?(\\d{1,2})[-/月](\\d{1,2})\\s*日?\\s+(\\d{1,2}):(\\d{2})");

    record Distance(String value, String unit) {
        Integer kilometers() {
            String text = value.replace('\u00a0', ' ').trim();
            Matcher distance = RANGE.matcher(text);
            String cleanedUnit = unit.replace('\u00a0', ' ').trim();
            if (!cleanedUnit.isEmpty()) {
                if (!cleanedUnit.equalsIgnoreCase("km") && !cleanedUnit.equals("㎞") && !cleanedUnit.equals("公里")) {
                    return null;
                }
                if (!distance.matches()) distance = RANGE.matcher(text + " " + cleanedUnit);
            }
            return distance.matches() ? Integer.parseInt(distance.group(1).replace(",", "")) : null;
        }
    }

    record Energy(Distance distance, Integer percent) {}

    private WidgetReadings() {}

    static VehicleStatus parse(Distance total, Energy electric, Energy fuel, String motion,
                               String updateLabel, long receivedAt, ZoneId zone) {
        String label = updateLabel.trim();
        Matcher time = UPDATED.matcher(label);
        long updatedAt = time.find() ? parseTime(time, receivedAt, zone) : 0;
        if (updatedAt == 0) return VehicleStatus.EMPTY;

        Integer totalKm = total.kilometers();
        Integer electricKm = electric.distance().kilometers();
        Integer fuelKm = fuel.distance().kilometers();
        if ("03-15 12:00".equals(label) && Integer.valueOf(364).equals(totalKm)
                && electricKm == null && fuelKm == null) {
            return VehicleStatus.EMPTY;
        }
        if (totalKm == null && electricKm == null && fuelKm == null) return VehicleStatus.EMPTY;
        return new VehicleStatus(totalKm, electricKm, fuelKm, electric.percent(), fuel.percent(),
                motion.trim(), updatedAt, label);
    }

    private static long parseTime(Matcher time, long now, ZoneId zone) {
        LocalDateTime current = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone);
        int month = Integer.parseInt(time.group(2));
        int year = time.group(1) != null ? Integer.parseInt(time.group(1))
                : current.getYear() - (current.getMonthValue() == 1 && month == 12 ? 1 : 0);
        try {
            LocalDateTime value = LocalDateTime.of(year, month, Integer.parseInt(time.group(3)),
                    Integer.parseInt(time.group(4)), Integer.parseInt(time.group(5)));
            long epoch = value.atZone(zone).toInstant().toEpochMilli();
            return epoch > 0 && epoch <= now + 120_000 ? epoch : 0;
        } catch (DateTimeException invalid) {
            return 0;
        }
    }
}
