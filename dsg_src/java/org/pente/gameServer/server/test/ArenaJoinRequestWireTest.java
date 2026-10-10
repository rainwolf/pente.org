package org.pente.gameServer.server.test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import junit.framework.TestCase;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.event.*;

import java.awt.Color;
import java.util.*;

/**
 * The arena join-request events on the wire (spec 2026-10-10, Wire protocol):
 * each one is wrapped under its exact lowercase key and carries exactly the
 * spec's fields (plus the envelope-wide "time").
 */
public class ArenaJoinRequestWireTest extends TestCase {

    public ArenaJoinRequestWireTest(String name) {
        super(name);
    }

    // ---- S->C ----------------------------------------------------------

    public void testJoinRequestsEventKeyAndFields() {
        DSGArenaJoinRequestsEvent e = new DSGArenaJoinRequestsEvent(7, Arrays.asList(
                new DSGArenaJoinRequestsEvent.Request("bob", 4),
                new DSGArenaJoinRequestsEvent.Request("dave", 9)));

        JsonObject body = body(e, "dsgArenaJoinRequestsEvent");

        assertKeys(body, "table", "requests", "time");
        assertEquals(7, body.get("table").getAsInt());
        JsonArray requests = body.getAsJsonArray("requests");
        assertEquals(2, requests.size());
        JsonObject first = requests.get(0).getAsJsonObject();
        assertKeys(first, "player", "seq");
        assertEquals("bob", first.get("player").getAsString());
        assertEquals(4L, first.get("seq").getAsLong());
        assertEquals("dave", requests.get(1).getAsJsonObject().get("player").getAsString());
        assertEquals(9L, requests.get(1).getAsJsonObject().get("seq").getAsLong());
    }

    public void testEmptyJoinRequestsEventStillCarriesRequestsArray() {
        JsonObject body = body(new DSGArenaJoinRequestsEvent(3,
                Collections.<DSGArenaJoinRequestsEvent.Request>emptyList()), "dsgArenaJoinRequestsEvent");

        assertKeys(body, "table", "requests", "time");
        assertEquals(0, body.getAsJsonArray("requests").size());
    }

    public void testJoinRequestsEventCopiesItsList() {
        List<DSGArenaJoinRequestsEvent.Request> list = new ArrayList<>();
        list.add(new DSGArenaJoinRequestsEvent.Request("bob", 1));
        DSGArenaJoinRequestsEvent e = new DSGArenaJoinRequestsEvent(3, list);
        list.clear();

        assertEquals(1, e.getRequests().size());
    }

    public void testMyRequestsEventKeyAndFields() {
        int[] tables = {2, 5};
        DSGArenaMyRequestsEvent e = new DSGArenaMyRequestsEvent(tables);
        tables[0] = 99;   // the event keeps its own copy

        JsonObject body = body(e, "dsgArenaMyRequestsEvent");

        assertKeys(body, "tables", "time");
        JsonArray out = body.getAsJsonArray("tables");
        assertEquals(2, out.size());
        assertEquals(2, out.get(0).getAsInt());
        assertEquals(5, out.get(1).getAsInt());
    }

    public void testRequestEndedEventKeyAndFields() {
        JsonObject body = body(new DSGArenaRequestEndedEvent(
                "bob", 7, "alice", DSGArenaRequestEndedEvent.DECLINED), "dsgArenaRequestEndedEvent");

        assertKeys(body, "table", "player", "owner", "reason", "time");
        assertEquals(7, body.get("table").getAsInt());
        assertEquals("bob", body.get("player").getAsString());
        assertEquals("alice", body.get("owner").getAsString());
        assertEquals("DECLINED", body.get("reason").getAsString());
    }

    public void testReasonCodesAreTheSpecStrings() {
        assertEquals("DECLINED", DSGArenaRequestEndedEvent.DECLINED);
        assertEquals("TABLE_FULL", DSGArenaRequestEndedEvent.TABLE_FULL);
        assertEquals("TABLE_CLOSED", DSGArenaRequestEndedEvent.TABLE_CLOSED);
        assertEquals("DUPLICATE", DSGArenaRequestEndedEvent.DUPLICATE);
        assertEquals("BLOCKED", DSGArenaRequestEndedEvent.BLOCKED);
        assertEquals("NOT_AVAILABLE", DSGArenaRequestEndedEvent.NOT_AVAILABLE);
        assertEquals("GUEST_RATED", DSGArenaRequestEndedEvent.GUEST_RATED);
        assertEquals("BOOTED", DSGArenaRequestEndedEvent.BOOTED);
        assertEquals("NO_LONGER_AVAILABLE", DSGArenaRequestEndedEvent.NO_LONGER_AVAILABLE);
    }

    // ---- C->S ----------------------------------------------------------

    public void testWithdrawEventDecodesFromClientFrame() {
        DSGEventWrapper w = serverGson().fromJson(
                "{\"dsgArenaWithdrawJoinRequestEvent\":{\"table\":7,\"time\":0}}", DSGEventWrapper.class);

        Object decoded = w.getEncodedEvent();

        assertTrue(decoded instanceof DSGArenaWithdrawJoinRequestEvent);
        assertTrue(decoded instanceof DSGTableEvent);
        assertEquals(7, ((DSGArenaWithdrawJoinRequestEvent) decoded).getTable());
        assertNull(((DSGArenaWithdrawJoinRequestEvent) decoded).getPlayer());
    }

    public void testWithdrawEventEncodesWithLowercaseKey() {
        JsonObject body = body(new DSGArenaWithdrawJoinRequestEvent("bob", 7), "dsgArenaWithdrawJoinRequestEvent");

        assertKeys(body, "table", "player", "time");
        assertEquals(7, body.get("table").getAsInt());
    }

    public void testLowercaseDeclineDecodesAndCapitalKeyIsNotAliased() {
        DSGEventWrapper lower = serverGson().fromJson(
                "{\"dsgArenaRejectTableJoinEvent\":{\"table\":7,\"playerToReject\":\"bob\"}}", DSGEventWrapper.class);
        DSGEventWrapper capital = serverGson().fromJson(
                "{\"DSGArenaRejectTableJoinEvent\":{\"table\":7,\"playerToReject\":\"bob\"}}", DSGEventWrapper.class);

        assertTrue(lower.getEncodedEvent() instanceof DSGArenaRejectTableJoinEvent);
        assertNull(capital.getEncodedEvent());
    }

    // ---- helpers -------------------------------------------------------

    /** Same Gson setup the server uses to read frames (WebSocketDSGEventHandler.readMessage). */
    private static Gson serverGson() {
        GsonBuilder b = new GsonBuilder();
        b.registerTypeAdapter(Color.class, new DSGColorAdapter());
        b.registerTypeAdapter(DSGPlayerData.class, new DSGPlayerDataAdapter());
        b.registerTypeAdapter(DSGPlayerGameData.class, new DSGPlayerGameDataAdapter());
        return b.create();
    }

    /** Encodes like the server's writers do and returns the body under the one wrapper key. */
    private static JsonObject body(Object event, String key) {
        String json = new DSGEventWrapper(event).getJSON();
        JsonObject root = new Gson().fromJson(json, JsonObject.class);
        assertEquals("exactly one wrapper key in " + json, 1, root.entrySet().size());
        assertTrue("wrapper key " + key + " in " + json, root.has(key));
        return root.getAsJsonObject(key);
    }

    private static void assertKeys(JsonObject o, String... expected) {
        Set<String> actual = new TreeSet<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            actual.add(e.getKey());
        }
        assertEquals(new TreeSet<>(Arrays.asList(expected)), actual);
    }
}
