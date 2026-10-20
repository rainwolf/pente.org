package org.pente.gameServer.kids.test;

import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import junit.framework.TestCase;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.event.DSGEventWrapper;
import org.pente.gameServer.event.DSGJoinMainRoomEvent;
import org.pente.gameServer.event.DSGLoginEvent;
import org.pente.gameServer.event.DSGUpdatePlayerDataEvent;
import org.pente.gameServer.kids.LiveKidState;
import org.pente.gameServer.server.RedisConnectionManager;

/** A kid's parentPid goes only to the kid's own login echo, never to other players. */
public class KidWirePrivacyTest extends TestCase {

    public KidWirePrivacyTest(String name) {
        super(name);
    }

    private static SimpleDSGPlayerData kid() {
        SimpleDSGPlayerData p = new SimpleDSGPlayerData();
        p.setPlayerID(42);
        p.setName("kid");
        p.setParentPid(7L);
        return p;
    }

    public void testJoinMainRoomEventHasNoParentPid() {
        SimpleDSGPlayerData kid = kid();
        JsonObject body = body(new DSGJoinMainRoomEvent("kid", kid), "dsgJoinMainRoomEvent");
        assertTrue(!body.getAsJsonObject("dsgPlayerData").has("parentPid"));
        assertEquals(Long.valueOf(7L), kid.getParentPid());
    }

    public void testUpdatePlayerDataEventHasNoParentPid() {
        SimpleDSGPlayerData kid = kid();
        JsonObject body = body(new DSGUpdatePlayerDataEvent(kid), "dsgUpdatePlayerDataEvent");
        assertTrue(!body.getAsJsonObject("data").has("parentPid"));
        assertEquals(Long.valueOf(7L), kid.getParentPid());
    }

    public void testLoginEchoKeepsParentPid() {
        DSGLoginEvent login = new DSGLoginEvent();
        login.setMe(kid());
        JsonObject body = body(login, "dsgLoginEvent");
        assertEquals(7L, body.getAsJsonObject("me").get("parentPid").getAsLong());
    }

    public void testNonKidLoginHasNoParentPidKey() {
        SimpleDSGPlayerData p = kid();
        p.setParentPid(null);
        DSGLoginEvent login = new DSGLoginEvent();
        login.setMe(p);
        JsonObject body = body(login, "dsgLoginEvent");
        assertTrue(!body.getAsJsonObject("me").has("parentPid"));
    }

    public void testJoinEventDoesNotSerializeKidState() {
        DSGJoinMainRoomEvent join = new DSGJoinMainRoomEvent("kid", kid());
        join.setKidState(new LiveKidState(42, 7L, Set.of(9L), true, 1L));
        JsonObject body = body(join, "dsgJoinMainRoomEvent");
        assertTrue("kidState under " + body, !hasKey(body, "kidState"));
        assertNotNull(join.getKidState());
    }

    private static boolean hasKey(JsonElement e, String key) {
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                if (en.getKey().equals(key) || hasKey(en.getValue(), key)) {
                    return true;
                }
            }
        } else if (e.isJsonArray()) {
            for (JsonElement c : e.getAsJsonArray()) {
                if (hasKey(c, key)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void testRedisRoundTripKeepsParentPid() {
        SimpleDSGPlayerData back = (SimpleDSGPlayerData)
                RedisConnectionManager.deserialize(RedisConnectionManager.serialize(kid()));
        assertEquals(Long.valueOf(7L), back.getParentPid());
    }

    /** Encodes like the server's writers do and returns the body under the one wrapper key. */
    private static JsonObject body(Object event, String key) {
        String json = new DSGEventWrapper(event).getJSON();
        JsonObject root = new Gson().fromJson(json, JsonObject.class);
        assertEquals("exactly one wrapper key in " + json, 1, root.entrySet().size());
        assertTrue("wrapper key " + key + " in " + json, root.has(key));
        return root.getAsJsonObject(key);
    }
}
