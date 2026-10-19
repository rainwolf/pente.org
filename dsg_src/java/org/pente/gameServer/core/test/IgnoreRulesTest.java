package org.pente.gameServer.core.test;

import junit.framework.TestCase;

import org.pente.gameServer.client.web.IgnoreRules;
import org.pente.gameServer.core.DSGIgnoreData;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerData;

/**
 * Who may be ignored and how an add merges into an existing ignore (/gameServer/ignored).
 * Lives under core/test because the build's compile-tests excludes client/**.
 *
 * Run: ant test-one -Dtest=org.pente.gameServer.core.test.IgnoreRulesTest
 */
public class IgnoreRulesTest extends TestCase {

    private static final String NOT_FOUND = "Player ignore not found";
    private static final String SELF = "You can't ignore yourself silly";

    public IgnoreRulesTest(String name) {
        super(name);
    }

    private static DSGPlayerData player(long pid, char status) {
        DSGPlayerData d = new SimpleDSGPlayerData();
        d.setPlayerID(pid);
        d.setStatus(status);
        return d;
    }

    private static DSGIgnoreData ignore(boolean invite, boolean chat) {
        DSGIgnoreData i = new DSGIgnoreData();
        i.setIgnoreInvite(invite);
        i.setIgnoreChat(chat);
        return i;
    }

    public void testNullTargetRejected() {
        assertEquals(NOT_FOUND, IgnoreRules.rejectTarget(null, 1));
    }

    public void testInactiveTargetRejected() {
        assertEquals(NOT_FOUND, IgnoreRules.rejectTarget(player(2, DSGPlayerData.DEACTIVE), 1));
    }

    public void testSelfRejected() {
        assertEquals(SELF, IgnoreRules.rejectTarget(player(1, DSGPlayerData.ACTIVE), 1));
    }

    public void testActiveOtherAccepted() {
        assertNull(IgnoreRules.rejectTarget(player(2, DSGPlayerData.ACTIVE), 1));
    }

    public void testMergeAddsChatKeepsInvite() {
        DSGIgnoreData i = ignore(true, false);
        assertTrue(IgnoreRules.mergeFlags(i, false, true));
        assertTrue(i.getIgnoreInvite());
        assertTrue(i.getIgnoreChat());
    }

    public void testMergeNoChange() {
        DSGIgnoreData i = ignore(true, true);
        assertTrue(!IgnoreRules.mergeFlags(i, true, false));
        assertTrue(i.getIgnoreInvite());
        assertTrue(i.getIgnoreChat());
    }
}
