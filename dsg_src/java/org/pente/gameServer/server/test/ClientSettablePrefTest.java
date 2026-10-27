package org.pente.gameServer.server.test;

import junit.framework.*;
import org.pente.gameServer.server.ServerPlayer;

import java.lang.reflect.Method;

/**
 * Checks the websocket preference allowlist. The method is package-private,
 * so it is invoked reflectively.
 */
public class ClientSettablePrefTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{ClientSettablePrefTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(ClientSettablePrefTest.class);
    }

    public ClientSettablePrefTest(String name) {
        super(name);
    }

    private static boolean allowed(String name) throws Exception {
        Method m = ServerPlayer.class.getDeclaredMethod("isClientSettablePref", String.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, name);
    }

    public void testClientPrefsAllowed() throws Exception {
        for (String n : new String[]{"tableSize", "playJoinSound", "playInviteSound",
                "chatTimestamp", "showPlayerJoinExit", "gameOptions", "ims"}) {
            assertTrue(n, allowed(n));
        }
    }

    public void testRestrictedPrefsRefused() throws Exception {
        for (String n : new String[]{"weekend", "tb", "beta", "pentedb", "gameState", "", "TB"}) {
            assertTrue(n, !allowed(n));
        }
        assertTrue(!allowed(null));
    }
}
