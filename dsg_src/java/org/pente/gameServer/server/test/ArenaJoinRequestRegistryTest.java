package org.pente.gameServer.server.test;

import junit.framework.TestCase;
import org.pente.gameServer.event.*;
import org.pente.gameServer.server.ArenaJoinRequestRegistry;

import java.util.*;

/**
 * ArenaJoinRequestRegistry rules R1-R11 (spec 2026-10-10), driven without
 * tables or sockets: an ArenaEventLog stands in for the player router.
 */
public class ArenaJoinRequestRegistryTest extends TestCase {

    private static final int A = 1;   // alice's table
    private static final int C = 2;   // carol's table

    private ArenaEventLog log;
    private ArenaJoinRequestRegistry reg;

    public ArenaJoinRequestRegistryTest(String name) {
        super(name);
    }

    protected void setUp() {
        log = new ArenaEventLog();
        reg = new ArenaJoinRequestRegistry(log);
        reg.publishTable(A, "alice", players("alice"), true, false);
        reg.publishTable(C, "carol", players("carol"), true, false);
        log.clear();
    }

    private static List<String> players(String... names) {
        return Arrays.asList(names);
    }

    private static List<Integer> tables(Integer... numbers) {
        return Arrays.asList(numbers);
    }

    // ---- table state ----------------------------------------------------

    public void testNewTableOwnerGetsEmptySnapshot() {
        ArenaEventLog fresh = new ArenaEventLog();
        ArenaJoinRequestRegistry r = new ArenaJoinRequestRegistry(fresh);

        r.publishTable(7, "zed", players("zed"), true, false);

        assertEquals(players(), fresh.lastSnapshot("zed", 7));
        assertTrue(r.isOpen(7));
    }

    // ---- R1/R2: creating a request ----------------------------------------

    public void testRequestGoesToOwnerAndRequesterSeesIt() {
        reg.request(A, "bob", true);

        assertEquals(players("bob"), reg.pendingRequesters(A));
        assertEquals(players("bob"), log.lastSnapshot("alice", A));
        assertEquals(tables(A), log.lastMyRequests("bob"));
        assertEquals(players(), log.reasons("bob"));
    }

    public void testSnapshotIsOrderedBySeqAndSeqIsNeverReused() {
        reg.request(A, "bob", true);
        reg.request(A, "dave", true);
        assertEquals(players("bob", "dave"), log.lastSnapshot("alice", A));
        List<Long> before = log.lastSnapshotSeqs("alice", A);
        assertTrue(before.get(0) < before.get(1));

        reg.withdraw(A, "bob");
        reg.request(A, "erin", true);

        assertEquals(players("dave", "erin"), log.lastSnapshot("alice", A));
        List<Long> after = log.lastSnapshotSeqs("alice", A);
        assertEquals(before.get(1), after.get(0));
        assertTrue(after.get(1) > before.get(1));
    }

    public void testRequesterMayRequestSeveralTables() {
        reg.request(A, "bob", true);
        reg.request(C, "bob", true);

        assertEquals(tables(A, C), log.lastMyRequests("bob"));
        assertEquals(new TreeSet<>(tables(A, C)), reg.pendingTables("bob"));
    }

    public void testDuplicateRequestIsRefusedAndStillAnswered() {
        reg.request(A, "bob", true);
        reg.request(A, "bob", true);

        assertEquals(players("DUPLICATE@" + A), log.reasons("bob"));
        assertEquals(players("bob"), reg.pendingRequesters(A));
        assertEquals(2, log.to("bob", DSGArenaMyRequestsEvent.class).size());
        assertEquals(tables(A), log.lastMyRequests("bob"));
    }

    public void testRequesterOutsideMainRoomIsRefused() {
        reg.request(A, "bob", false);

        assertEquals(players("NOT_AVAILABLE@" + A), log.reasons("bob"));
        assertEquals(players(), reg.pendingRequesters(A));
        assertEquals(tables(), log.lastMyRequests("bob"));
    }

    public void testOwnerAndSeatedPlayersAreRefused() {
        reg.publishTable(5, "zed", players("zed"), true, false);

        reg.request(A, "alice", true);
        reg.request(A, "zed", true);

        assertEquals(players("NOT_AVAILABLE@" + A), log.reasons("alice"));
        assertEquals(players("NOT_AVAILABLE@" + A), log.reasons("zed"));
        assertEquals(players(), reg.pendingRequesters(A));
    }

    public void testTableThatIsNotOpenRefuses() {
        // alice is alone but her game is paused: not open
        reg.publishTable(A, "alice", players("alice"), false, false);

        reg.request(A, "bob", true);

        assertEquals(players("NOT_AVAILABLE@" + A), log.reasons("bob"));
    }

    public void testUnknownTableRefusesWithEmptyOwner() {
        reg.request(42, "bob", true);

        List<DSGArenaRequestEndedEvent> ended = log.to("bob", DSGArenaRequestEndedEvent.class);
        assertEquals(1, ended.size());
        assertEquals(DSGArenaRequestEndedEvent.NOT_AVAILABLE, ended.get(0).getReason());
        assertEquals("", ended.get(0).getOwner());
        assertEquals("bob", ended.get(0).getPlayer());
        assertEquals(tables(), log.lastMyRequests("bob"));
    }

    public void testGuestIsRefusedAtRatedTableOnly() {
        reg.publishTable(6, "rita", players("rita"), true, true);

        reg.request(6, "guest12", true);
        reg.request(A, "guest12", true);

        assertEquals(players("GUEST_RATED@6"), log.reasons("guest12"));
        assertEquals(players("guest12"), reg.pendingRequesters(A));
    }

    // ---- R3: withdraw -----------------------------------------------------

    public void testWithdrawRemovesRequestAndBlocksReRequest() {
        reg.request(A, "bob", true);

        reg.withdraw(A, "bob");

        assertEquals(players(), reg.pendingRequesters(A));
        assertEquals(players(), log.lastSnapshot("alice", A));
        assertEquals(tables(), log.lastMyRequests("bob"));
        assertTrue(reg.isBlocked(A, "bob"));

        reg.request(A, "bob", true);

        assertEquals(players("BLOCKED@" + A), log.reasons("bob"));
        assertEquals(players(), reg.pendingRequesters(A));
    }

    public void testWithdrawWithNothingPendingIsAnsweredAndDoesNotBlock() {
        reg.withdraw(A, "bob");

        assertEquals(tables(), log.lastMyRequests("bob"));
        assertTrue(!reg.isBlocked(A, "bob"));
        reg.request(A, "bob", true);
        assertEquals(players("bob"), reg.pendingRequesters(A));
    }

    // ---- R4: decline ------------------------------------------------------

    public void testDeclineRemovesBlocksAndTellsRequester() {
        reg.request(A, "bob", true);

        reg.decline(A, "alice", "bob");

        List<DSGArenaRequestEndedEvent> ended = log.to("bob", DSGArenaRequestEndedEvent.class);
        assertEquals(1, ended.size());
        assertEquals(DSGArenaRequestEndedEvent.DECLINED, ended.get(0).getReason());
        assertEquals("alice", ended.get(0).getOwner());
        assertEquals("bob", ended.get(0).getPlayer());
        assertEquals(tables(), log.lastMyRequests("bob"));
        assertEquals(players(), log.lastSnapshot("alice", A));

        reg.request(A, "bob", true);

        assertEquals(players("DECLINED@" + A, "BLOCKED@" + A), log.reasons("bob"));
    }

    public void testBlockIsPerTable() {
        reg.request(A, "bob", true);
        reg.decline(A, "alice", "bob");

        reg.request(C, "bob", true);

        assertEquals(players("bob"), reg.pendingRequesters(C));
    }

    public void testDeclineOfPlayerWhoIsNotPendingOnlyAnswersOwner() {
        reg.request(A, "bob", true);
        log.clear();

        reg.decline(A, "alice", "nobody");
        reg.decline(A, "alice", null);

        assertEquals(2, log.to("alice", DSGArenaJoinRequestsEvent.class).size());
        assertEquals(players("bob"), log.lastSnapshot("alice", A));
        assertTrue(!reg.isBlocked(A, "nobody"));
        assertEquals(0, log.to("nobody", DSGEvent.class).size());
    }

    // ---- R9: only the owner decides -------------------------------------

    public void testNonOwnerDeclineIsIgnoredAndAnsweredWithEmptySnapshot() {
        reg.request(A, "bob", true);

        reg.decline(A, "dave", "bob");

        assertEquals(players("bob"), reg.pendingRequesters(A));
        assertEquals(players(), log.lastSnapshot("dave", A));
        assertEquals(players(), log.reasons("bob"));
        assertTrue(!reg.isBlocked(A, "bob"));
    }
}
