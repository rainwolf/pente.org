package org.pente.gameServer.server.test;

import junit.framework.TestCase;
import org.pente.gameServer.event.*;
import org.pente.gameServer.server.ArenaJoinRequestRegistry;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

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

    // ---- R5: accept claims ------------------------------------------------

    /** Stands in for queueing the DSGJoinTableEvent on the table's pump. */
    private static final class JoinQueue implements Runnable {
        int runs;

        public void run() {
            runs++;
        }
    }

    public void testAcceptClaimsRequesterAndQueuesJoin() {
        reg.request(A, "bob", true);
        reg.request(C, "bob", true);
        JoinQueue q = new JoinQueue();

        assertTrue(reg.accept(A, "alice", "bob", q));

        assertEquals(1, q.runs);
        assertEquals(Integer.valueOf(A), reg.claimedTable("bob"));
        assertEquals(players(), log.lastSnapshot("alice", A));
        assertEquals(tables(C), log.lastMyRequests("bob"));      // R5: no other request touched
        assertEquals(players("bob"), reg.pendingRequesters(C));
    }

    public void testNonOwnerAcceptIsIgnoredAndAnsweredWithEmptySnapshot() {
        reg.request(A, "bob", true);
        JoinQueue q = new JoinQueue();

        assertTrue(!reg.accept(A, "bob", "bob", q));

        assertEquals(0, q.runs);
        assertNull(reg.claimedTable("bob"));
        assertEquals(players(), log.lastSnapshot("bob", A));
        assertEquals(players("bob"), reg.pendingRequesters(A));
    }

    public void testAcceptOfPlayerWhoIsNotPendingIsNoLongerAvailable() {
        JoinQueue q = new JoinQueue();

        assertTrue(!reg.accept(A, "alice", "ghost", q));
        assertTrue(!reg.accept(A, "alice", null, q));

        assertEquals(0, q.runs);
        assertEquals(players("NO_LONGER_AVAILABLE@" + A, "NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
        assertEquals("ghost", log.to("alice", DSGArenaRequestEndedEvent.class).get(0).getPlayer());
        // never null: Gson would drop the key and a client would render "undefined"
        assertEquals("", log.to("alice", DSGArenaRequestEndedEvent.class).get(1).getPlayer());
        assertEquals(2, log.to("alice", DSGArenaJoinRequestsEvent.class).size());
    }

    public void testDoubleAcceptOfSameRequesterClaimsOnce() {
        reg.request(A, "bob", true);
        reg.request(C, "bob", true);
        JoinQueue qa = new JoinQueue();
        JoinQueue qc = new JoinQueue();

        assertTrue(reg.accept(A, "alice", "bob", qa));
        assertTrue(!reg.accept(C, "carol", "bob", qc));

        assertEquals(1, qa.runs);
        assertEquals(0, qc.runs);
        assertEquals(players("NO_LONGER_AVAILABLE@" + C), log.reasons("carol"));
        assertEquals(players("bob"), log.lastSnapshot("carol", C));   // still pending there (R5)
    }

    public void testOutstandingClaimRefusesNewRequestsToTableAndFromRequester() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());

        reg.request(A, "dave", true);
        reg.request(C, "bob", true);

        assertEquals(players("NOT_AVAILABLE@" + A), log.reasons("dave"));
        assertEquals(players("NOT_AVAILABLE@" + C), log.reasons("bob"));
    }

    public void testOwnerDoubleTapOnTwoRequestersClaimsOnlyTheFirst() {
        reg.request(A, "bob", true);
        reg.request(A, "dave", true);
        JoinQueue qb = new JoinQueue();
        JoinQueue qd = new JoinQueue();
        assertTrue(reg.accept(A, "alice", "bob", qb));
        log.clear();

        assertTrue(!reg.accept(A, "alice", "dave", qd));

        assertEquals(0, qd.runs);
        assertEquals(players("dave"), log.lastSnapshot("alice", A));   // answered (R11)
        assertEquals(players(), log.reasons("alice"));
        assertEquals(players("dave"), reg.pendingRequesters(A));

        // bob's join lands: the table fills and dave's request ends
        reg.playerJoinedTable(A, "bob");
        reg.publishTable(A, "alice", players("alice", "bob"), true, false);

        assertEquals(players("TABLE_FULL@" + A), log.reasons("dave"));
    }

    /** R10: two owners accepting the same requester at once claim exactly once. */
    public void testConcurrentAcceptsOfSameRequesterClaimOnce() throws Exception {
        for (int round = 0; round < 200; round++) {
            final ArenaJoinRequestRegistry r = new ArenaJoinRequestRegistry(new ArenaEventLog());
            r.publishTable(A, "alice", players("alice"), true, false);
            r.publishTable(C, "carol", players("carol"), true, false);
            r.request(A, "bob", true);
            r.request(C, "bob", true);
            final AtomicInteger joins = new AtomicInteger();
            final CountDownLatch go = new CountDownLatch(1);
            Thread ta = new Thread(() -> {
                awaitQuietly(go);
                r.accept(A, "alice", "bob", joins::incrementAndGet);
            });
            Thread tc = new Thread(() -> {
                awaitQuietly(go);
                r.accept(C, "carol", "bob", joins::incrementAndGet);
            });
            ta.start();
            tc.start();
            go.countDown();
            ta.join();
            tc.join();

            assertEquals("round " + round, 1, joins.get());
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- R6: the claimed join lands ---------------------------------------

    public void testClaimedJoinIsAdmittedWhileRequesterIsAvailable() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());

        assertTrue(reg.admitJoin(A, "bob", true));
        reg.playerJoinedTable(A, "bob");

        assertNull(reg.claimedTable("bob"));
    }

    public void testClaimedJoinIsRefusedWhenRequesterLeftMainRoom() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());
        log.clear();

        assertTrue(!reg.admitJoin(A, "bob", false));

        assertNull(reg.claimedTable("bob"));
        assertEquals(players("NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
        assertEquals("bob", log.to("alice", DSGArenaRequestEndedEvent.class).get(0).getPlayer());
        assertEquals(players(), log.lastSnapshot("alice", A));
        // the table takes requests again
        reg.request(A, "dave", true);
        assertEquals(players("dave"), reg.pendingRequesters(A));
    }

    public void testClaimedJoinIsRefusedWhenRequesterSatDownElsewhere() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());
        // bob creates his own table 7 before the join lands at A
        assertTrue(reg.admitJoin(7, "bob", true));
        reg.playerJoinedTable(7, "bob");
        reg.publishTable(7, "bob", players("bob"), true, false);
        assertEquals(Integer.valueOf(A), reg.claimedTable("bob"));   // kept so A can refuse it

        assertTrue(!reg.admitJoin(A, "bob", true));

        assertNull(reg.claimedTable("bob"));
        assertEquals(players("NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
    }

    public void testUnclaimedJoinIsAlwaysAdmitted() {
        assertTrue(reg.admitJoin(A, "zed", false));
    }

    /** Review Focus 6: admitted, then ServerTable.handleJoin answered BOOTED (or threw). */
    public void testClaimedJoinTheTableTurnsAwayReleasesClaimAndTellsOwner() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());
        assertTrue(reg.admitJoin(A, "bob", true));
        log.clear();

        reg.joinFailed(A, "bob");

        assertNull(reg.claimedTable("bob"));
        assertEquals(players("NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
        assertEquals("bob", log.to("alice", DSGArenaRequestEndedEvent.class).get(0).getPlayer());
        assertEquals(players(), log.lastSnapshot("alice", A));
        // neither the table nor the requester is stuck on the claim
        reg.request(A, "dave", true);
        assertEquals(players("dave"), reg.pendingRequesters(A));
        reg.request(C, "bob", true);
        assertEquals(players("bob"), reg.pendingRequesters(C));
    }

    public void testFailedJoinWithoutAClaimThereChangesNothing() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());
        log.clear();

        reg.joinFailed(A, "zed");
        reg.joinFailed(C, "bob");   // bob's claim is at A, not C

        assertEquals(Integer.valueOf(A), reg.claimedTable("bob"));
        assertEquals(0, log.countAll(DSGEvent.class));
    }

    /** Review Focus 7: the table filled by another path before the claimed join landed. */
    public void testJoinQueuedByAClaimThatTheTableFullReleasedIsRefused() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());
        // zed's plain join was ahead of bob's in A's queue and fills the table
        assertTrue(reg.admitJoin(A, "zed", true));
        reg.playerJoinedTable(A, "zed");
        reg.publishTable(A, "alice", players("alice", "zed"), true, false);
        assertEquals(players("TABLE_FULL@" + A), log.reasons("bob"));
        assertNull(reg.claimedTable("bob"));

        assertTrue(!reg.admitJoin(A, "bob", true));   // bob's queued join: not seated as a spectator
        assertTrue(reg.admitJoin(A, "bob", true));    // a later plain join of his own is not affected
    }

    public void testJoiningAnyTableEndsRequestsSilently() {
        reg.request(A, "bob", true);
        reg.request(C, "bob", true);
        log.clear();

        reg.playerJoinedTable(9, "bob");

        assertEquals(players(), reg.pendingRequesters(A));
        assertEquals(players(), reg.pendingRequesters(C));
        assertEquals(players(), log.lastSnapshot("alice", A));
        assertEquals(players(), log.lastSnapshot("carol", C));
        assertEquals(players(), log.reasons("bob"));
        assertEquals(tables(), log.lastMyRequests("bob"));
    }

    public void testTableReachingTwoPlayersEndsPendingWithTableFull() {
        reg.request(A, "bob", true);
        reg.request(A, "dave", true);
        reg.request(C, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());
        log.clear();

        reg.playerJoinedTable(A, "bob");
        reg.publishTable(A, "alice", players("alice", "bob"), true, false);

        assertEquals(players("TABLE_FULL@" + A), log.reasons("dave"));
        assertEquals("alice", log.to("dave", DSGArenaRequestEndedEvent.class).get(0).getOwner());
        assertEquals(tables(), log.lastMyRequests("dave"));
        assertEquals(players(), log.lastSnapshot("alice", A));
        assertEquals(players(), log.reasons("bob"));               // R7: silent
        assertEquals(players(), log.lastSnapshot("carol", C));
        assertEquals(tables(), log.lastMyRequests("bob"));
        assertTrue(!reg.isOpen(A));
    }

    public void testFullTableForgetsItsBlockedSet() {
        reg.request(A, "dave", true);
        reg.decline(A, "alice", "dave");

        reg.publishTable(A, "alice", players("alice", "bob"), true, false);

        assertTrue(!reg.isBlocked(A, "dave"));
    }

    // ---- R6b: reopening -----------------------------------------------------

    public void testOwnerLeavingFullTableReopensItForTheNewOwner() {
        reg.publishTable(A, "alice", players("alice", "bob"), true, false);
        log.clear();

        reg.publishTable(A, "bob", players("bob"), true, false);

        assertTrue(reg.isOpen(A));
        assertEquals(1, log.to("bob", DSGArenaJoinRequestsEvent.class).size());
        assertEquals(players(), log.lastSnapshot("bob", A));
        reg.request(A, "dave", true);
        assertEquals(players("dave"), log.lastSnapshot("bob", A));
    }

    /** Registry-level: drive a paused, then cancelled, one-player table directly. */
    public void testPausedTableReopensWithMemoryWiped() {
        reg.request(A, "dave", true);
        reg.decline(A, "alice", "dave");
        reg.publishTable(A, "alice", players("alice"), false, false);   // waiting for player to return
        log.clear();
        reg.request(A, "erin", true);
        assertEquals(players("NOT_AVAILABLE@" + A), log.reasons("erin"));

        reg.publishTable(A, "alice", players("alice"), true, false);    // set cancelled

        assertEquals(players(), log.lastSnapshot("alice", A));
        assertTrue(!reg.isBlocked(A, "dave"));
        reg.request(A, "dave", true);
        assertEquals(players("dave"), reg.pendingRequesters(A));
    }

    public void testOwnerChangeWhileGameRunsSendsNewOwnerASnapshot() {
        reg.publishTable(A, "alice", players("alice", "bob"), false, false);
        log.clear();

        reg.publishTable(A, "bob", players("bob"), false, false);   // alice left mid-game

        assertTrue(!reg.isOpen(A));
        assertEquals(players(), log.lastSnapshot("bob", A));
    }

    public void testSeqKeepsGrowingAcrossReopen() {
        reg.request(A, "bob", true);
        long bobFirst = log.lastSnapshotSeqs("alice", A).get(0);
        reg.publishTable(A, "alice", players("alice", "zed"), true, false);   // full
        reg.publishTable(A, "alice", players("alice"), true, false);          // reopened

        reg.request(A, "dave", true);
        reg.request(A, "bob", true);

        assertEquals(players("dave", "bob"), log.lastSnapshot("alice", A));
        List<Long> seqs = log.lastSnapshotSeqs("alice", A);
        assertTrue(seqs.get(0) > bobFirst);
        assertTrue(seqs.get(1) > seqs.get(0));
    }
}
