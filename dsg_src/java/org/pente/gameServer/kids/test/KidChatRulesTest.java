package org.pente.gameServer.kids.test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;
import org.pente.gameServer.core.SystemPids;
import org.pente.gameServer.kids.KidChatRules;
import org.pente.gameServer.kids.KidChatRules.Party;

/** Who a kid may chat with: both sides must accept the other. */
public class KidChatRulesTest extends TestCase {

    public KidChatRulesTest(String name) {
        super(name);
    }

    private static Set<Long> set(Long... pids) {
        return new HashSet<>(Arrays.asList(pids));
    }

    private static Party adult(long pid) {
        return new Party(pid, false, false, null, Collections.<Long>emptySet());
    }

    private static Party kid(long pid, long parentPid, Set<Long> whitelist) {
        return new Party(pid, false, false, parentPid, whitelist);
    }

    private static final Party S = adult(200);
    private static final Party W = adult(300);
    private static final Party A = new Party(400, true, false, null, null);
    private static final Party P1 = adult(1);
    private static final Party P2 = adult(2);

    private static Party k() {
        return kid(100, 1, Collections.<Long>emptySet());
    }

    public void testTwoNonKidsAllowed() {
        assertTrue(KidChatRules.allowed(S, W));
    }

    public void testKidAndStrangerRefusedBothDirections() {
        Party K = k();
        assertTrue(!KidChatRules.allowed(K, S));
        assertTrue(!KidChatRules.allowed(S, K));
    }

    public void testKidAndWhitelistedAllowed() {
        Party K = kid(100, 1, set(300L));
        assertTrue(KidChatRules.allowed(K, W));
        assertTrue(KidChatRules.allowed(W, K));
    }

    public void testKidAndOwnParentAllowed() {
        assertTrue(KidChatRules.allowed(k(), P1));
    }

    public void testKidAndOtherKidsParentRefused() {
        assertTrue(!KidChatRules.allowed(k(), P2));
    }

    public void testKidAndAdminAllowed() {
        assertTrue(KidChatRules.allowed(k(), A));
        assertTrue(KidChatRules.allowed(A, k()));
    }

    public void testKidAndSystemPidAllowed() {
        assertTrue(KidChatRules.allowed(k(), adult(SystemPids.SYSTEM_PID)));
    }

    public void testKidAndComputerPidAllowed() {
        assertTrue(KidChatRules.allowed(k(), adult(SystemPids.COMPUTER_PID)));
    }

    public void testKidAndComputerFlagAllowed() {
        Party computer = new Party(600, false, true, null, null);
        assertTrue(KidChatRules.allowed(k(), computer));
    }

    public void testKidToKidNeedsBothWhitelists() {
        Party K = kid(100, 1, set(500L));
        Party K2 = kid(500, 2, Collections.<Long>emptySet());
        assertTrue(!KidChatRules.allowed(K, K2));
        assertTrue(!KidChatRules.allowed(K2, K));

        Party K2b = kid(500, 2, set(100L));
        assertTrue(KidChatRules.allowed(K, K2b));
        assertTrue(KidChatRules.allowed(K2b, K));
    }

    public void testSamePidAllowed() {
        Party K = k();
        assertTrue(KidChatRules.allowed(K, K));
    }

    public void testGuestRefused() {
        assertTrue(!KidChatRules.allowed(k(), adult(5001)));
    }

    public void testNullWhitelistIsEmpty() {
        assertTrue(new Party(100, false, false, 1L, null).whitelist.isEmpty());
    }
}
