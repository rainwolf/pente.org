package org.pente.gameServer.server.test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import junit.framework.TestCase;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.event.*;

import java.awt.Color;

/** Tombstone skip-dead-stone events on the wire (spec 8.1, 16.3). */
public class TombstoneSkipDeadWireTest extends TestCase {

    public TombstoneSkipDeadWireTest(String name) {
        super(name);
    }

    private static void assertFalse(boolean b) { assertTrue(!b); }

    public void testInboundSkipDecodes() {
        DSGEventWrapper w = serverGson().fromJson(
            "{\"dsgTombstoneSkipDeadTableEvent\":{\"player\":\"alice\",\"table\":7,\"time\":0}}",
            DSGEventWrapper.class);
        Object e = w.getEncodedEvent();
        assertTrue(e instanceof DSGTombstoneSkipDeadTableEvent);
        assertEquals("alice", ((DSGTombstoneSkipDeadTableEvent) e).getPlayer());
        assertEquals(7, ((DSGTombstoneSkipDeadTableEvent) e).getTable());
        assertFalse(((DSGTombstoneSkipDeadTableEvent) e).isSilent());
    }

    public void testOutboundSkipCarriesSilent() {
        JsonObject o = serverGson().fromJson(new DSGEventWrapper(
            new DSGTombstoneSkipDeadTableEvent("alice", 7, true)).getJSON(), JsonObject.class);
        JsonObject ev = o.getAsJsonObject("dsgTombstoneSkipDeadTableEvent");
        assertNotNull(ev);
        assertTrue(ev.get("silent").getAsBoolean());
        assertEquals("alice", ev.get("player").getAsString());
    }

    public void testOutboundErrorCarriesCode() {
        JsonObject o = serverGson().fromJson(new DSGEventWrapper(
            new DSGTombstoneSkipDeadTableErrorEvent("alice", 7,
                DSGTableErrorEvent.NOT_AWAITING_DEAD_STONE)).getJSON(), JsonObject.class);
        assertEquals(26, o.getAsJsonObject("dsgTombstoneSkipDeadTableErrorEvent").get("error").getAsInt());
    }

    private static Gson serverGson() {
        GsonBuilder b = new GsonBuilder();
        b.registerTypeAdapter(Color.class, new DSGColorAdapter());
        b.registerTypeAdapter(DSGPlayerData.class, new DSGPlayerDataAdapter());
        b.registerTypeAdapter(DSGPlayerGameData.class, new DSGPlayerGameDataAdapter());
        return b.create();
    }
}
