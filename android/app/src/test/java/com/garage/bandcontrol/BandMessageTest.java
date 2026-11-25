package com.garage.bandcontrol;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public final class BandMessageTest {
    @Test public void helloRoundTripsWithTheProtocolAndSupportedFeatures() throws Exception {
        BandMessage request = decode(new JSONObject().put("v", 1).put("id", "test-123")
                .put("type", "hello").put("bandApp", "0.1.16").put("minPhoneApp", "0.1.14"));
        assertEquals("hello", request.type());
        JSONObject reply = BandMessage.helloReply(request.id(), BuildConfig.VERSION_NAME);
        assertEquals(1, reply.getInt("v"));
        assertEquals("hello-reply", reply.getString("type"));
        assertEquals("test-123", reply.getString("id"));
        assertEquals(BuildConfig.VERSION_NAME, reply.getString("phoneApp"));
        assertEquals(1, reply.getInt("protocol"));
        assertEquals("[\"confirm\",\"cancel\",\"refresh\"]", reply.getJSONArray("features").toString());
    }

    @Test public void allElevenControlsHaveDistinctWireNames() throws Exception {
        CarAction[] actions = CarAction.values();
        assertEquals(11, actions.length);
        for (CarAction action : actions) {
            BandMessage message = decode(new JSONObject().put("v", 1).put("id", "test-123")
                    .put("type", "prepare").put("action", action.id));
            assertEquals(action, message.action());
        }
    }

    @Test public void rejectsUnknownActionsAndUnconfirmedExecutions() throws Exception {
        JSONObject message = new JSONObject().put("v", 1).put("id", "test-123").put("type", "execute");
        assertNull(decode(message.put("action", "unknown")));
        assertNull(decode(message.put("action", CarAction.SENTRY.id)));
    }

    @Test public void validatesPrepareDirectionsForEachAction() throws Exception {
        JSONObject message = new JSONObject().put("v", 1).put("id", "test-123").put("type", "prepare");
        for (CarAction action : CarAction.values()) {
            message.put("action", action.id).remove("controlDirection");
            assertNull(decode(message).direction());
            for (ControlDirection direction : ControlDirection.values()) {
                BandMessage request = decode(message.put("controlDirection", direction.id));
                boolean expected = switch (action) {
                    case LOCK -> direction == ControlDirection.LOCK || direction == ControlDirection.UNLOCK;
                    case FIND, TRUNK -> false;
                    default -> direction == ControlDirection.OPEN || direction == ControlDirection.CLOSE;
                };
                if (expected) assertSame(direction, request.direction());
                else assertNull(request);
            }
            assertNull(decode(message.put("controlDirection", "toggle")));
        }
    }

    @Test public void legacyLockAndWindowFieldsAreValidatedWithTheNewDirection() throws Exception {
        JSONObject message = new JSONObject().put("v", 1).put("id", "test-123").put("type", "prepare")
                .put("action", CarAction.LOCK.id);
        assertSame(ControlDirection.UNLOCK, decode(message.put("lockState", "locked")).direction());
        assertSame(ControlDirection.LOCK, decode(message.put("lockState", "unlocked")).direction());
        assertNull(decode(message.put("controlDirection", "unlock")));
        message.remove("controlDirection");
        assertNull(decode(message.put("lockState", "unknown")));
        assertNull(decode(message.put("action", CarAction.CLIMATE.id).put("lockState", "locked")));
        message.remove("lockState");
        message.put("action", CarAction.WINDOW_VENT.id);
        assertSame(ControlDirection.CLOSE, decode(message.put("windowDirection", "close")).direction());
        assertNull(decode(message.put("controlDirection", "open")));
        assertNull(decode(message.put("action", CarAction.CLIMATE.id)));
    }

    @Test public void replyBudgetCountsUtf8BytesAndAllowsExactly2048() throws Exception {
        String id = "test-123";
        JSONObject reply = BandMessage.reply(id, "ready", "");
        int room = BandMessage.MAX_BYTES - reply.toString().getBytes(StandardCharsets.UTF_8).length;
        String message = "中".repeat(room / 3) + "x".repeat(room % 3);
        reply.put("message", message);
        assertEquals(2048, reply.toString().getBytes(StandardCharsets.UTF_8).length);
        assertEquals(reply.toString(), BandMessage.encodeReply(id, () -> reply, invalid -> fail(invalid.toString())));
        reply.put("message", message + "x");
        assertError(id, "too_large", BandMessage.encodeReply(id, () -> reply, invalid -> fail(invalid.toString())));
    }

    @Test public void aReplyBuilderExceptionReturnsAnImmediateCorrelatedError() throws Exception {
        for (Exception problem : new Exception[]{new JSONException("failed"), new IllegalStateException("failed")}) {
            AtomicReference<Exception> reported = new AtomicReference<>();
            String payload = BandMessage.encodeReply("test-123", () -> {
                if (problem instanceof JSONException invalid) throw invalid;
                throw (RuntimeException) problem;
            }, reported::set);
            assertError("test-123", "internal", payload);
            assertSame(problem, reported.get());
        }
    }

    private static void assertError(String id, String code, String payload) throws JSONException {
        JSONObject error = new JSONObject(payload);
        assertEquals(4, error.length());
        assertEquals(1, error.getInt("v"));
        assertEquals("reply", error.getString("type"));
        assertEquals(id, error.getString("id"));
        assertEquals(code, error.getString("error"));
    }

    private static BandMessage decode(JSONObject message) throws Exception {
        return BandMessage.decode(message.toString().getBytes(StandardCharsets.UTF_8));
    }
}
