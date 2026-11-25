package com.garage.bandcontrol;

import org.junit.Test;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class WidgetReadingsTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final long NOW = Instant.parse("2026-11-24T01:16:00Z").toEpochMilli();

    @Test public void keepsFieldIdentityEvenWhenTheRangesDoNotAddUp() {
        VehicleStatus status = WidgetReadings.parse(distance("700", "km"), energy("76", "km", 79),
                energy("658 km", "", 43), "已驻车", "11-24 09:15", NOW, SHANGHAI);
        assertEquals(Integer.valueOf(700), status.totalKm());
        assertEquals(Integer.valueOf(76), status.electricKm());
        assertEquals(Integer.valueOf(658), status.fuelKm());
        assertEquals(Integer.valueOf(79), status.electricPercent());
        assertEquals(Integer.valueOf(43), status.fuelPercent());
        assertEquals("已驻车", status.motion());
        assertEquals(NOW - 60_000, status.updatedAt());
    }

    @Test public void keepsZeroProgressAndOmitsUnavailableProgress() throws Exception {
        VehicleStatus status = WidgetReadings.parse(distance("1,007", "km"), energy("0", "公里", 0),
                energy("37", "%", null), "充电中", "11-24 09:15", NOW, SHANGHAI);
        assertEquals(Integer.valueOf(1007), status.totalKm());
        assertEquals(Integer.valueOf(0), status.electricKm());
        assertNull(status.fuelKm());
        assertEquals("充电中", status.motion());
        JSONObject json = status.json();
        assertEquals(0, json.getInt("electricPercent"));
        assertNull(json.opt("fuelPercent"));
    }

    @Test public void rejectsTemplateAndInvalidUpdateTime() {
        assertEquals(VehicleStatus.EMPTY, WidgetReadings.parse(distance("364", "km"), energy("37", "%", 36),
                energy("37", "%", 37), "已驻车", "03-15 12:00", NOW, SHANGHAI));
        assertEquals(VehicleStatus.EMPTY, WidgetReadings.parse(distance("734", "km"), energy("76", "km", 78),
                energy("658", "km", 46), "已驻车", "11-24 21:15", NOW, SHANGHAI));
    }

    @Test public void interpretsDecemberCardDateInEarlyJanuary() {
        long january = Instant.parse("2026-12-31T16:01:00Z").toEpochMilli();
        VehicleStatus status = WidgetReadings.parse(distance("734", "km"), energy("76", "km", null),
                energy("658", "km", null), "已驻车", "12-31 23:59", january, SHANGHAI);
        assertEquals(january - 120_000, status.updatedAt());
    }

    private static WidgetReadings.Distance distance(String value, String unit) {
        return new WidgetReadings.Distance(value, unit);
    }

    private static WidgetReadings.Energy energy(String value, String unit, Integer percent) {
        return new WidgetReadings.Energy(distance(value, unit), percent);
    }
}
