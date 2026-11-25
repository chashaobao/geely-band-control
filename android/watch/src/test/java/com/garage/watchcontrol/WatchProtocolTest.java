package com.garage.watchcontrol;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WatchProtocolTest {
    @Test public void buildsHelloAndPrepareRequestsMatchingCompanionWireFormat() throws Exception {
        JSONObject hello = WatchProtocol.buildRequest("abc_1", "hello", null, null, null, "0.1.23");
        assertEquals(1, hello.getInt("v"));
        assertEquals("hello", hello.getString("type"));
        assertEquals("0.1.23", hello.getString("bandApp"));
        assertEquals("0.1.14", hello.getString("minPhoneApp"));

        JSONObject prepare = WatchProtocol.buildRequest("abc_2", "prepare", "climate", null, "open", "0.1.23");
        assertEquals("prepare", prepare.getString("type"));
        assertEquals("climate", prepare.getString("action"));
        assertEquals("open", prepare.getString("controlDirection"));

        JSONObject carImg = WatchProtocol.buildCarImageRequest("abc_3", "hash1234", 2);
        assertEquals("car_image", carImg.getString("type"));
        assertEquals("hash1234", carImg.getString("hash"));
        assertEquals(2, carImg.getInt("index"));
    }

    @Test public void validatesCompatibilityAndDirectionLabels() throws Exception {
        assertEquals("", WatchProtocol.compatibilityMessage(
                new JSONObject().put("v", 1).put("protocol", 1).put("phoneApp", "0.1.23"), "0.1.23"));
        assertEquals("请更新手机端到配套版本", WatchProtocol.compatibilityMessage(
                new JSONObject().put("v", 1).put("protocol", 1).put("phoneApp", "0.1.10"), "0.1.23"));
        assertEquals("请更新手表端到配套版本", WatchProtocol.compatibilityMessage(
                new JSONObject().put("v", 1).put("protocol", 1).put("phoneApp", "1.0.0"), "0.1.23"));

        Map<String, String> directions = WatchProtocol.readControlDirections(
                new JSONObject().put("lock", "unlock").put("window_vent", "close").put("climate", "open"), null);
        assertEquals("解锁", WatchProtocol.controlLabel("lock", directions.get("lock")));
        assertEquals("关窗", WatchProtocol.controlLabel("window_vent", directions.get("window_vent")));
        assertEquals("开空调", WatchProtocol.controlLabel("climate", directions.get("climate")));
        assertEquals("lock-open.png", WatchProtocol.iconAsset("lock", "lock"));
        assertEquals("lock.png", WatchProtocol.iconAsset("lock", "unlock"));
        assertTrue(WatchProtocol.needsDirection("climate", false));
        assertFalse(WatchProtocol.needsDirection("trunk", true));
        assertEquals(2, WatchProtocol.knownActions(new JSONArray().put("climate").put("climate").put("bad").put("find")).size());
    }

    @Test public void crownScrollSwitchesBetweenFirstAndSecondPageWithDebounce() {
        assertEquals(1, VerticalPagerView.nextPageFromCrown(0, 2, -1.0f, 1000L, 0L));
        assertEquals(0, VerticalPagerView.nextPageFromCrown(1, 2, 1.0f, 1500L, 1000L));
        assertEquals(-1, VerticalPagerView.nextPageFromCrown(0, 2, 1.0f, 1500L, 1000L));
        assertEquals(-1, VerticalPagerView.nextPageFromCrown(1, 2, -1.0f, 1500L, 1000L));
        assertEquals(-1, VerticalPagerView.nextPageFromCrown(0, 2, -1.0f, 1100L, 1000L));
        assertEquals(-1, VerticalPagerView.nextPageFromCrown(0, 2, 0.0f, 1500L, 1000L));
    }
}
