package org.pente.gameServer.mobile.test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import junit.framework.*;
import org.pente.gameServer.mobile.IndexResponse;

import java.lang.reflect.Constructor;

/**
 * Checks that the game id is serialized as "gameId" on the three index entry types.
 * Constructors are package-private, so they are invoked reflectively.
 */
public class IndexResponseGameIdTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{IndexResponseGameIdTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(IndexResponseGameIdTest.class);
    }

    public IndexResponseGameIdTest(String name) {
        super(name);
    }

    private static Object newEntry(Class<?> type, Object... args) throws Exception {
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            if (c.getParameterTypes().length == args.length) {
                c.setAccessible(true);
                return c.newInstance(args);
            }
        }
        throw new IllegalStateException("no matching constructor for " + type);
    }

    private static JsonObject serialize(Object entry) {
        String json = new Gson().toJson(entry);
        return new JsonParser().parse(json).getAsJsonObject();
    }

    public void testInvitationEntryGameId() throws Exception {
        Object e = newEntry(IndexResponse.InvitationEntry.class,
                5L, "Pente", "alice", 1500, "white (p1)", 3, "rated", 2, 0, 41);
        JsonObject json = serialize(e);
        assertTrue(json.has("gameId"));
        assertEquals(41, json.get("gameId").getAsInt());
        assertEquals("Pente", json.get("gameName").getAsString());
    }

    public void testGameEntryGameId() throws Exception {
        Object e = newEntry(IndexResponse.GameEntry.class,
                99L, "Speed Pente", "bob", 1600, "black (p2)", 7, "0d 1h", "unrated", 1, 0, 42);
        JsonObject json = serialize(e);
        assertTrue(json.has("gameId"));
        assertEquals(42, json.get("gameId").getAsInt());
        assertEquals(99L, json.get("gid").getAsLong());
    }

    public void testOpenInvitationEntryGameId() throws Exception {
        Object e = newEntry(IndexResponse.OpenInvitationEntry.class,
                7L, "Keryo-Pente", "carol", 1200, "whiteblack", 2, "unrated", 0, 0, 43);
        JsonObject json = serialize(e);
        assertTrue(json.has("gameId"));
        assertEquals(43, json.get("gameId").getAsInt());
        assertEquals("carol", json.get("inviterName").getAsString());
    }
}
