package org.pente.gameServer.kids.test;

import junit.framework.TestCase;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.kids.KidParentAccess;

public class KidParentAccessTest extends TestCase {

    private SimpleDSGPlayerData P, K, S, X, O, R;

    public KidParentAccessTest(String name) {
        super(name);
    }

    private static SimpleDSGPlayerData player(long pid, String name, Long parentPid) {
        SimpleDSGPlayerData d = new SimpleDSGPlayerData();
        d.setPlayerID(pid);
        d.setName(name);
        d.setParentPid(parentPid);
        d.setStatus(DSGPlayerData.ACTIVE);
        return d;
    }

    protected void setUp() {
        P = player(1, "parent", null);
        K = player(100, "kid", Long.valueOf(1));
        S = player(101, "sibling", Long.valueOf(1));
        X = player(200, "stranger", null);
        O = player(300, "otherkid", Long.valueOf(2));
        R = player(400, "released", null);
    }

    public void testParentOfPlayer1() {
        assertTrue(KidParentAccess.isParentOf(P, K, X));
    }

    public void testParentOfPlayer2() {
        assertTrue(KidParentAccess.isParentOf(P, X, K));
    }

    public void testStrangerIsNotParent() {
        assertTrue(!KidParentAccess.isParentOf(X, K, O));
    }

    public void testOtherKidsParentIsNotParent() {
        SimpleDSGPlayerData other = player(2, "otherparent", null);
        assertTrue(!KidParentAccess.isParentOf(other, K, X));
    }

    public void testSiblingIsNotParent() {
        assertTrue(!KidParentAccess.isParentOf(S, K, X));
    }

    public void testReleasedKidNotParent() {
        assertTrue(!KidParentAccess.isParentOf(P, R, X));
    }

    public void testNullsAreFalse() {
        assertTrue(!KidParentAccess.isParentOf(null, K, X));
        assertTrue(!KidParentAccess.isParentOf(P, null, null));
    }

    public void testStorerOverloadLoads() {
        MapPlayerStorer players = new MapPlayerStorer();
        players.put(P);
        players.put(K);
        players.put(X);
        assertTrue(KidParentAccess.isParentOf(P, players, 100, 200));
        assertTrue(!KidParentAccess.isParentOf(P, players, 200, 0));
    }

    public void testStorerFailureFailsClosed() {
        MapPlayerStorer players = new MapPlayerStorer();
        players.put(P);
        players.put(K);
        players.failLoads = true;
        assertTrue(!KidParentAccess.isParentOf(P, players, 100, 200));
    }

    public void testHistoryRequestorPidTb() {
        // parent sees kid's private TB games in history
        assertEquals(100L, KidParentAccess.historyRequestorPid(P, K, true));
        // non-parent sees neither
        assertEquals(200L, KidParentAccess.historyRequestorPid(X, K, true));
        assertEquals(1L, KidParentAccess.historyRequestorPid(P, X, true));
    }

    public void testHistoryRequestorPidLiveNeverExempt() {
        // parent must NOT see kid's private live games: behaves as on main
        assertEquals(1L, KidParentAccess.historyRequestorPid(P, K, false));
        assertEquals(200L, KidParentAccess.historyRequestorPid(X, K, false));
    }

    public void testCanReadGameOnlyTb() {
        MapPlayerStorer players = new MapPlayerStorer();
        players.put(P);
        players.put(K);
        players.put(X);
        assertTrue(KidParentAccess.canReadGame(P, players, 100, 200, true));
        assertTrue(!KidParentAccess.canReadGame(P, players, 100, 200, false));
        assertTrue(!KidParentAccess.canReadGame(X, players, 100, 300, true));
    }
}
