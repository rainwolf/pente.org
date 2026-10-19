package org.pente.gameServer.kids.test;

import junit.framework.TestCase;

import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.core.SystemPids;
import org.pente.gameServer.kids.StorerKidChatPolicy;

public class StorerKidChatPolicyTest extends TestCase {

    private static final long PARENT = 1;
    private static final long KID = 100;
    private static final long STRANGER = 200;
    private static final long WHITELISTED = 300;

    private MapPlayerStorer players;
    private InMemoryKidStorer kids;
    private StorerKidChatPolicy policy;

    public StorerKidChatPolicyTest(String name) {
        super(name);
    }

    private static SimpleDSGPlayerData player(long pid, String name, Long parentPid) {
        SimpleDSGPlayerData d = new SimpleDSGPlayerData();
        d.setPlayerID(pid);
        d.setName(name);
        d.setParentPid(parentPid);
        return d;
    }

    protected void setUp() {
        players = new MapPlayerStorer();
        players.put(player(PARENT, "parent", null));
        players.put(player(KID, "kid", PARENT));
        players.put(player(STRANGER, "stranger", null));
        players.put(player(WHITELISTED, "friend", null));
        kids = new InMemoryKidStorer();
        kids.addToWhitelist(KID, WHITELISTED);
        policy = new StorerKidChatPolicy(players, kids);
    }

    public void testKidAndStrangerRefused() {
        assertTrue(!policy.allowed(KID, STRANGER));
        assertTrue(!policy.allowed(STRANGER, KID));
    }

    public void testKidAndWhitelistedAllowed() {
        assertTrue(policy.allowed(KID, WHITELISTED));
        assertTrue(policy.allowed(WHITELISTED, KID));
        assertTrue(policy.allowed(KID, PARENT));
    }

    public void testNonKidsAllowed() {
        assertTrue(policy.allowed(STRANGER, WHITELISTED));
        assertTrue(policy.allowed(PARENT, STRANGER));
    }

    public void testUnknownOpponentRefusedForKidAuthor() {
        assertTrue(!policy.allowed(KID, 0));
    }

    public void testUnknownOpponentAllowedForNonKid() {
        assertTrue(policy.allowed(STRANGER, 0));
    }

    public void testStorerErrorFailsClosed() {
        players.failLoads = true;
        assertTrue(!policy.allowed(STRANGER, WHITELISTED));
    }

    public void testWhitelistNotLoadedForNonKids() {
        assertTrue(policy.allowed(STRANGER, WHITELISTED));
        assertEquals(0, kids.whitelistReads);
    }

    public void testSystemPidsAndSamePidAllowedWithoutLoading() {
        players.failLoads = true;
        kids.failReads = true;
        assertTrue(policy.allowed(KID, SystemPids.SYSTEM_PID));
        assertTrue(policy.allowed(SystemPids.COMPUTER_PID, KID));
        assertTrue(policy.allowed(KID, KID));
    }
}
