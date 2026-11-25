package com.garage.bandcontrol;

import org.junit.Test;

import static com.garage.bandcontrol.ControlDirection.CLOSE;
import static com.garage.bandcontrol.ControlDirection.OPEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public final class WindowControlTest {
    @Test public void anOpenWindowWarningSelectsOnlyTheActiveWindowButtonForClosing() {
        assertEquals(CLOSE, WindowControl.resolve("车窗未关", CLOSE, true).direction());
        assertEquals(CLOSE, WindowControl.resolve("车门未锁、车窗未关闭", CLOSE, true).direction());
        assertBlocked("车窗未关", OPEN);
        assertBlocked("车窗未关", null);
    }

    @Test public void closedParkedOrUnlockedReadingsAllowOpeningWithARecognizedOpeningIcon() {
        assertEquals(OPEN, WindowControl.resolve("已驻车", OPEN, false).direction());
        assertEquals(OPEN, WindowControl.resolve("车窗已关闭", OPEN, false).direction());
        assertEquals(OPEN, WindowControl.resolve("已停车", OPEN, false).direction());
        assertEquals(OPEN, WindowControl.resolve("车门未锁", OPEN, false).direction());
        assertNull(WindowControl.resolve("已驻车", OPEN, true).direction());
        assertBlocked("已停车", null);
        assertBlocked("已驻车", CLOSE);
        assertBlocked("车窗未关；车窗已关", CLOSE);
    }

    @Test public void missingAndUnrelatedWarningsDoNotEstablishWindowDirection() {
        assertBlocked("", OPEN);
        assertBlocked("行驶中", OPEN);
        assertBlocked("车门未锁", CLOSE);
        assertBlocked("天窗未关", CLOSE);
    }

    private static void assertBlocked(String status, ControlDirection icon) {
        WindowControl.Decision decision = WindowControl.resolve(status, icon, icon == CLOSE);
        assertNull(decision.direction());
        assertFalse(decision.blockedReason().isEmpty());
    }
}
