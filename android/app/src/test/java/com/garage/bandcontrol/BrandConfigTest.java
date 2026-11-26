package com.garage.bandcontrol;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BrandConfigTest {
    @Test public void geelyUsesItsOwnProviderAndStorage() {
        BrandConfig geely = BrandConfig.fromId("GEELY");
        assertSame(BrandConfig.GEELY, geely);
        assertEquals("com.geely.consumer", geely.packageName);
        assertEquals("com.geely.consumer.carcontrol.widget.appwidget.Wg_Middle", geely.provider);
        assertEquals("wg_middle_unbound", geely.setupLayout);
        assertEquals("wg_middle_unable", geely.unavailableLayout);
        assertEquals(BrandConfig.GALAXY.slots, geely.slots);
        for (BrandConfig other : java.util.List.of(BrandConfig.GALAXY, BrandConfig.LYNKCO)) {
            assertNotEquals(other.hostId, geely.hostId);
            assertNotEquals(other.cardsKey, geely.cardsKey);
            assertNotEquals(other.namespace, geely.namespace);
        }
        assertEquals(MiHomeVoice.Command.FIND, MiHomeVoice.match(geely.namespace, "GEELY_V1:FIND", null, null).command());
        assertNull(MiHomeVoice.match(geely.namespace, "GARAGE_V1:FIND", null, null));
        assertNull(MiHomeVoice.match(geely.namespace, "GEELY_V1:WINDOW_VENT", null, null));
    }

    @Test public void legacyGalaxyStorageAndHostStayStable() {
        assertSame(BrandConfig.GALAXY, BrandConfig.fromId(""));
        assertSame(BrandConfig.GALAXY, BrandConfig.fromId("unknown"));
        assertEquals("widgetCards", BrandConfig.GALAXY.cardsKey);
        assertEquals(0x4742, BrandConfig.GALAXY.hostId);
        assertEquals("GARAGE_V1", BrandConfig.GALAXY.namespace);
        assertEquals(175, BrandConfig.GALAXY.heightDp);
    }
    @Test public void configurableLynkMappingIsIsolatedAndDoesNotGuessIcons() {
        BrandConfig lynk = BrandConfig.fromId("LYNKCO");
        assertSame(BrandConfig.LYNKCO, lynk);
        assertNotEquals(BrandConfig.GALAXY.hostId, lynk.hostId);
        assertNotEquals(BrandConfig.GALAXY.cardsKey, lynk.cardsKey);
        assertEquals("com.lynkco.vehicle.widget.ListWidgetProvider", lynk.provider);
        assertEquals(BrandConfig.SlotMode.CONFIGURABLE, lynk.slotMode);
        assertEquals(java.util.List.of("fr_unlock", "fr_window", "fr_kjck", "fr_ccqk"), lynk.slots);
        assertEquals(new BrandConfig.Slot("fr_ccqk", "ccqk_on", "pb_ccqk_on"), lynk.slot("fr_ccqk"));
        assertEquals(BrandConfig.SlotMode.DYNAMIC, BrandConfig.GALAXY.slotMode);
        assertNull(lynk.iconPrefix);
        assertNull(lynk.setupLayout);
        assertFalse(CarAction.LYNK_KJCK.isSwitch());
        for (ControlDirection direction : ControlDirection.values()) assertFalse(CarAction.LYNK_CCQK.acceptsDirection(direction));
    }
}
