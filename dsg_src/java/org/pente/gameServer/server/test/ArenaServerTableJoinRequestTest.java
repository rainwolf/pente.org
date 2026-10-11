package org.pente.gameServer.server.test;

import junit.framework.TestCase;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.LiveSet;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.event.*;
import org.pente.gameServer.server.ArenaJoinRequestRegistry;
import org.pente.gameServer.server.ArenaServerTable;

import java.util.*;

/**
 * ArenaServerTable's join-request hooks (spec 2026-10-10, R2-R11), driven
 * through the real ServerTable code. Timers, threads and the database are
 * stubbed out; events land in an ArenaEventLog.
 */
public class ArenaServerTableJoinRequestTest extends TestCase {

    private static final int A = 1;
    private static final int C = 2;
    private static final String[] EVERYONE = {"alice", "bob", "carol", "dave", "erin", "zed"};

    private ArenaEventLog log;
    private ArenaJoinRequestRegistry reg;

    public ArenaServerTableJoinRequestTest(String name) {
        super(name);
    }

    protected void setUp() {
        log = new ArenaEventLog();
        reg = new ArenaJoinRequestRegistry(log);
    }

    // ---- scaffolding ------------------------------------------------------

    /** Collects what the table queues on its own pump instead of running it. */
    static final class Pump implements DSGEventListener {
        final List<DSGEvent> queued = new ArrayList<>();

        public void eventOccurred(DSGEvent dsgEvent) {
            queued.add(dsgEvent);
        }
    }

    static DSGPlayerData human(String name) {
        SimpleDSGPlayerData p = new SimpleDSGPlayerData();
        p.setName(name);
        p.setPlayerType(DSGPlayerData.HUMAN);
        return p;
    }

    /** An unrated public arena table whose main room holds EVERYONE. */
    static final class TestArenaTable extends ArenaServerTable {
        final Pump pump = new Pump();

        TestArenaTable(int table, ArenaJoinRequestRegistry registry, ArenaEventLog log) {
            tableNum = table;
            joinRequests = registry;
            dsgEventRouter = log;
            synchronizedTableListener = pump;
            playersInMainRoom = new Vector<>();
            for (String name : EVERYONE) {
                playersInMainRoom.add(human(name));
            }
            tableType = DSGChangeStateTableEvent.TABLE_TYPE_PUBLIC;
            state = DSGGameStateTableEvent.NO_GAME_IN_PROGRESS;
            timed = false;
            rated = false;
        }

        // side effects that need timers, threads or the database
        protected void startPressPlayTimer() {
        }

        protected void resetTableGameOver() {
        }

        protected void sendTimers(String toPlayer) {
        }

        protected void stopTimers() {
        }

        protected void startWaitingForPlayerToReturnTimer() {
        }

        protected void updateDatabaseAfterGameOverInSeparateThread(
                String winnerPlayer, String loserPlayer, int winner,
                LiveSet localSet, String status) {
        }

        // drivers
        /** Delivers the oldest queued join, as the pump would. */
        void landQueuedJoin() {
            DSGJoinTableEvent join = (DSGJoinTableEvent) pump.queued.remove(0);
            handleJoin(join.getPlayer());
        }

        void leaveMainRoom(String player) {
            handleMainRoomExit(player);
        }

        boolean seats(String player) {
            return isPlayerInTable(player);
        }

        int playerCount() {
            return playersInTable.size();
        }

        int number() {
            return tableNum;
        }

        void beginGame() {
            playingPlayers[1] = sittingPlayers[1];
            playingPlayers[2] = sittingPlayers[2];
            changeGameState(DSGGameStateTableEvent.GAME_IN_PROGRESS, "game started", 0);
        }

        void waitingTimeIsUp() {
            waitingForPlayerToReturnTimeUp = true;
        }

        int gameState() {
            return state;
        }

        /** Puts a player inside the ServerTable without the join path, so getOwner() sees them. */
        void holds(String player) {
            playersInTable.add(human(player));
        }

        /** Records a boot the way handleBoot does (ServerTable.java:3117): no rejoin for 5 minutes. */
        void bootedRecently(String player) {
            bootTimes.put(player, System.currentTimeMillis() + 1000L * 60 * 5);
        }

        /** A boot whose 5 minutes have passed: ServerTable leaves the entry, it just stops counting. */
        void bootExpired(String player) {
            bootTimes.put(player, System.currentTimeMillis() - 1);
        }
    }

    private static List<String> names(String... names) {
        return Arrays.asList(names);
    }

    private static List<Integer> nums(Integer... numbers) {
        return Arrays.asList(numbers);
    }

    private static void request(TestArenaTable t, String player) {
        t.handleArenaRequestJoin(new DSGArenaRequestJoinTableEvent(player, t.number()));
    }

    private static void accept(TestArenaTable t, String owner, String player) {
        t.handleArenaAcceptJoin(new DSGArenaAcceptTableJoinEvent(owner, t.number(), player));
    }

    /** A table whose state is put straight into the registry (no join hook needed). */
    private TestArenaTable seededTable(int table, String owner) {
        TestArenaTable t = new TestArenaTable(table, reg, log);
        reg.publishTable(table, owner, names(owner), true, false);
        log.clear();
        return t;
    }

    /** seededTable with the owner also inside the ServerTable, for checks that read getOwner(). */
    private TestArenaTable ownedTable(int table, String owner) {
        TestArenaTable t = seededTable(table, owner);
        t.holds(owner);
        return t;
    }

    // ---- Task 6: actions go through the registry --------------------------

    public void testRequestUsesTheTablesMainRoomView() {
        TestArenaTable a = seededTable(A, "alice");
        a.leaveMainRoom("bob");

        request(a, "bob");
        request(a, "dave");

        assertEquals(names("NOT_AVAILABLE@" + A), log.reasons("bob"));
        assertEquals(names("dave"), reg.pendingRequesters(A));
        assertEquals(names("dave"), log.lastSnapshot("alice", A));
    }

    public void testWithdrawGoesThroughRegistry() {
        TestArenaTable a = seededTable(A, "alice");
        request(a, "bob");

        a.handleArenaWithdrawJoin(new DSGArenaWithdrawJoinRequestEvent("bob", A));

        assertTrue(reg.isBlocked(A, "bob"));
        assertEquals(nums(), log.lastMyRequests("bob"));
        request(a, "bob");
        assertEquals(names("BLOCKED@" + A), log.reasons("bob"));
    }

    public void testDeclineGoesThroughRegistry() {
        TestArenaTable a = seededTable(A, "alice");
        request(a, "bob");

        a.handleArenaRejectJoin(new DSGArenaRejectTableJoinEvent("alice", A, "bob", null));

        assertEquals(names("DECLINED@" + A), log.reasons("bob"));
        assertEquals(names(), log.lastSnapshot("alice", A));
    }

    public void testAcceptQueuesClaimedJoinOnTheTablesOwnPump() {
        TestArenaTable a = seededTable(A, "alice");
        request(a, "bob");

        accept(a, "alice", "bob");

        assertEquals(1, a.pump.queued.size());
        DSGJoinTableEvent join = (DSGJoinTableEvent) a.pump.queued.get(0);
        assertEquals("bob", join.getPlayer());
        assertEquals(A, join.getTable());
        assertEquals(Integer.valueOf(A), reg.claimedTable("bob"));
    }

    public void testNonOwnerAcceptQueuesNothing() {
        TestArenaTable a = seededTable(A, "alice");
        request(a, "bob");

        accept(a, "bob", "bob");

        assertEquals(0, a.pump.queued.size());
        assertEquals(names(), log.lastSnapshot("bob", A));
        assertEquals(names("bob"), reg.pendingRequesters(A));
    }

    public void testOldIncrementalEventsAreNoLongerSent() {
        TestArenaTable a = seededTable(A, "alice");
        request(a, "bob");
        a.handleArenaRejectJoin(new DSGArenaRejectTableJoinEvent("alice", A, "bob", null));
        request(a, "dave");
        accept(a, "alice", "dave");

        assertEquals(0, log.countAll(DSGArenaRequestJoinTableEvent.class));
        assertEquals(0, log.countAll(DSGArenaRejectTableJoinEvent.class));
    }

    // ---- Task 6: R2 boot check, on the table's own pump ---------------------

    public void testBootedRequesterIsRefusedWithOwnerNamed() {
        TestArenaTable a = ownedTable(A, "alice");
        a.bootedRecently("bob");

        request(a, "bob");

        assertEquals(names("BOOTED@" + A), log.reasons("bob"));
        DSGArenaRequestEndedEvent ended = log.to("bob", DSGArenaRequestEndedEvent.class).get(0);
        assertEquals("bob", ended.getPlayer());
        assertEquals("alice", ended.getOwner());
        assertEquals(names(), reg.pendingRequesters(A));
        assertNull(log.lastSnapshot("alice", A));   // the registry never saw the request
    }

    public void testBootRefusalStillAnswersMyRequests() {
        TestArenaTable a = ownedTable(A, "alice");
        TestArenaTable c = ownedTable(C, "carol");
        request(c, "bob");
        a.bootedRecently("bob");
        log.clear();

        request(a, "bob");

        assertEquals(names("BOOTED@" + A), log.reasons("bob"));
        assertEquals(1, log.to("bob", DSGArenaMyRequestsEvent.class).size());   // R11
        assertEquals(nums(C), log.lastMyRequests("bob"));
        assertEquals(names("bob"), reg.pendingRequesters(C));                    // untouched
    }

    public void testExpiredBootNoLongerRefuses() {
        TestArenaTable a = ownedTable(A, "alice");
        a.bootExpired("bob");

        request(a, "bob");

        assertEquals(names(), log.reasons("bob"));
        assertEquals(names("bob"), reg.pendingRequesters(A));
        assertEquals(names("bob"), log.lastSnapshot("alice", A));
        assertEquals(nums(A), log.lastMyRequests("bob"));
    }

    // ---- Task 7: join, leave and removal hooks ----------------------------

    private TestArenaTable tableCreatedBy(int table, String owner) {
        TestArenaTable t = new TestArenaTable(table, reg, log);
        t.handleJoin(owner);
        return t;
    }

    public void testCreatingTableOpensItAndGivesOwnerEmptySnapshot() {
        TestArenaTable a = tableCreatedBy(A, "alice");

        assertTrue(a.seats("alice"));
        assertTrue(reg.isOpen(A));
        assertEquals(names(), log.lastSnapshot("alice", A));
    }

    public void testClaimedJoinSeatsRequesterAndEndsEveryoneElse() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        TestArenaTable c = tableCreatedBy(C, "carol");
        request(a, "bob");
        request(a, "dave");
        request(c, "bob");
        accept(a, "alice", "bob");
        log.clear();

        a.landQueuedJoin();

        assertTrue(a.seats("bob"));
        assertEquals(2, a.playerCount());
        assertTrue(!reg.isOpen(A));
        assertNull(reg.claimedTable("bob"));
        assertEquals(names("TABLE_FULL@" + A), log.reasons("dave"));
        assertEquals(names(), log.reasons("bob"));                 // R7: silent
        assertEquals(names(), log.lastSnapshot("carol", C));
        assertEquals(nums(), log.lastMyRequests("bob"));
    }

    public void testAcceptThenRequesterLeavesBeforeJoinLands() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        request(a, "bob");
        accept(a, "alice", "bob");
        a.leaveMainRoom("bob");                // the exit reached this pump first
        log.clear();

        a.landQueuedJoin();

        assertTrue(!a.seats("bob"));
        assertEquals(1, a.playerCount());      // no phantom null player
        assertNull(reg.claimedTable("bob"));
        assertEquals(names("NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
        assertTrue(reg.isOpen(A));
    }

    public void testAcceptThenRequesterSitsDownElsewhereBeforeJoinLands() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        request(a, "bob");
        accept(a, "alice", "bob");
        tableCreatedBy(7, "bob");
        log.clear();

        a.landQueuedJoin();

        assertTrue(!a.seats("bob"));
        assertEquals(names("NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
    }

    /**
     * Review Focus 6: the request passed R2's boot check, then a boot was
     * recorded on this table before the claimed join landed. admitJoin lets
     * the claimed join through, then ServerTable.handleJoin answers BOOTED.
     */
    public void testClaimedJoinRefusedByTheTableReleasesTheClaim() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        TestArenaTable c = tableCreatedBy(C, "carol");
        request(a, "bob");
        accept(a, "alice", "bob");
        a.bootedRecently("bob");               // after the request was created
        log.clear();

        a.landQueuedJoin();

        assertTrue(!a.seats("bob"));
        assertEquals(1, log.to("bob", DSGJoinTableErrorEvent.class).size());
        assertNull(reg.claimedTable("bob"));
        assertEquals(names("NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
        assertEquals(names(), log.lastSnapshot("alice", A));
        assertTrue(reg.isOpen(A));
        // neither the table nor bob stays stuck on the claim
        request(a, "dave");
        assertEquals(names("dave"), reg.pendingRequesters(A));
        request(c, "bob");
        assertEquals(names("bob"), reg.pendingRequesters(C));
    }

    public void testAcceptThenTableRemovedBeforeJoinLands() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        TestArenaTable c = tableCreatedBy(C, "carol");
        request(a, "bob");
        request(c, "bob");
        accept(a, "alice", "bob");
        log.clear();

        a.destroy();   // Server.removeTable stopped the pump: the queued join is dropped

        assertEquals(names("TABLE_CLOSED@" + A), log.reasons("bob"));
        assertNull(reg.claimedTable("bob"));
        accept(c, "carol", "bob");
        assertEquals(1, c.pump.queued.size());
    }

    public void testDoubleAcceptAcrossTablesQueuesOneJoin() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        TestArenaTable c = tableCreatedBy(C, "carol");
        request(a, "bob");
        request(c, "bob");

        accept(a, "alice", "bob");
        accept(c, "carol", "bob");

        assertEquals(1, a.pump.queued.size());
        assertEquals(0, c.pump.queued.size());
        assertEquals(names("NO_LONGER_AVAILABLE@" + C), log.reasons("carol"));
        a.landQueuedJoin();
        assertEquals(names(), log.lastSnapshot("carol", C));   // bob's request at C ends when he sits at A
    }

    public void testRequesterDisconnectRemovesRequests() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        request(a, "bob");
        log.clear();

        a.leaveMainRoom("bob");

        assertEquals(names(), log.lastSnapshot("alice", A));
        assertEquals(names(), log.reasons("bob"));
    }

    public void testClosingTableEndsPendingRequests() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        request(a, "bob");
        request(a, "dave");
        log.clear();

        a.destroy();

        assertEquals(names("TABLE_CLOSED@" + A), log.reasons("bob"));
        assertEquals(names("TABLE_CLOSED@" + A), log.reasons("dave"));
        assertTrue(reg.pendingTables("bob").isEmpty());
    }

    // ---- Task 8: R6b reopen transitions -------------------------------------

    /** alice's table with bob accepted and seated, and a game running */
    private TestArenaTable gameInProgress() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        request(a, "bob");
        accept(a, "alice", "bob");
        a.landQueuedJoin();
        a.beginGame();
        log.clear();
        return a;
    }

    public void testTableStaysClosedWhilePausedForReturningPlayer() {
        TestArenaTable a = gameInProgress();

        a.leaveMainRoom("bob");   // bob disconnects mid-game

        assertEquals(DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN, a.gameState());
        assertEquals(1, a.playerCount());
        assertTrue(!reg.isOpen(A));
        request(a, "erin");
        assertEquals(names("NOT_AVAILABLE@" + A), log.reasons("erin"));
    }

    public void testOwnerLeavingFinishedGameHandsOverAnOpenTable() {
        TestArenaTable a = gameInProgress();
        a.handleResign(new DSGResignTableEvent("bob", A));
        assertEquals(DSGGameStateTableEvent.NO_GAME_IN_PROGRESS, a.gameState());
        assertTrue(!reg.isOpen(A));   // still two players
        log.clear();

        a.handleExit("alice", false);

        assertTrue(reg.isOpen(A));
        assertEquals(1, log.to("bob", DSGArenaJoinRequestsEvent.class).size());
        assertEquals(names(), log.lastSnapshot("bob", A));
        request(a, "erin");
        assertEquals(names("erin"), log.lastSnapshot("bob", A));
    }

    public void testPlayerLeavingAfterGameEndsReopensTable() {
        TestArenaTable a = gameInProgress();
        a.handleResign(new DSGResignTableEvent("bob", A));
        log.clear();

        a.handleExit("bob", false);

        assertTrue(reg.isOpen(A));
        assertEquals(names(), log.lastSnapshot("alice", A));
        request(a, "erin");
        assertEquals(names("erin"), log.lastSnapshot("alice", A));
    }

    /**
     * Review Focus 8: handleBoot runs exit (which reopens the table, R6b,
     * and wipes registry memory) and then records bootTimes. The boot
     * outlives the reopen, so bob is still refused with BOOTED.
     */
    public void testBootThatReopensTheTableStillRefusesTheBootedPlayer() {
        TestArenaTable a = gameInProgress();
        a.handleResign(new DSGResignTableEvent("bob", A));
        log.clear();

        a.handleBoot(new DSGBootTableEvent("alice", A, "bob"));

        assertTrue(!a.seats("bob"));
        assertTrue(reg.isOpen(A));
        assertEquals(names(), log.lastSnapshot("alice", A));
        request(a, "bob");
        assertEquals(names("BOOTED@" + A), log.reasons("bob"));
        assertEquals("alice", log.to("bob", DSGArenaRequestEndedEvent.class).get(0).getOwner());
        assertEquals(nums(), log.lastMyRequests("bob"));
        assertEquals(names(), reg.pendingRequesters(A));
        request(a, "erin");   // the reopen still lets everyone else in
        assertEquals(names("erin"), log.lastSnapshot("alice", A));
    }

    /**
     * Through real tables the memory is already empty here: filling to two
     * players wiped it (R6) and a table that is not open takes no requests.
     * What R6b adds at this level is the owner's fresh snapshot and that the
     * table takes requests again; the wipe itself is pinned at registry level
     * by testPausedTableReopensWithMemoryWiped (Task 4).
     */
    public void testWaitingGameCancelledReopensTable() {
        TestArenaTable a = gameInProgress();
        a.leaveMainRoom("bob");
        request(a, "erin");           // refused while paused
        a.waitingTimeIsUp();
        log.clear();

        a.handleForceCancelResign(new DSGForceCancelResignTableEvent(
                "alice", A, DSGForceCancelResignTableEvent.CANCEL));

        assertEquals(DSGGameStateTableEvent.NO_GAME_IN_PROGRESS, a.gameState());
        assertTrue(reg.isOpen(A));
        assertEquals(names(), log.lastSnapshot("alice", A));
        request(a, "erin");
        assertEquals(names("erin"), reg.pendingRequesters(A));
    }

    public void testWaitingGameForceResignedReopensTable() {
        TestArenaTable a = gameInProgress();
        a.leaveMainRoom("bob");
        a.waitingTimeIsUp();
        log.clear();

        a.handleForceCancelResign(new DSGForceCancelResignTableEvent(
                "alice", A, DSGForceCancelResignTableEvent.RESIGN));

        assertEquals(DSGGameStateTableEvent.NO_GAME_IN_PROGRESS, a.gameState());
        assertTrue(reg.isOpen(A));
        assertEquals(names(), log.lastSnapshot("alice", A));
        request(a, "erin");
        assertEquals(names("erin"), reg.pendingRequesters(A));
    }

    /**
     * Review Focus 3. Passes before this task (nothing published on state
     * change) and must keep passing after it: the guard is what holds.
     */
    public void testDestroyedTableNeverRepublishesIntoReusedNumber() {
        TestArenaTable old = tableCreatedBy(A, "alice");
        old.destroy();
        TestArenaTable reused = tableCreatedBy(A, "zed");

        old.beginGame();   // a timer thread on the old object still runs startGame()

        assertTrue(reg.isOpen(A));
        request(reused, "erin");
        assertEquals(names("erin"), log.lastSnapshot("zed", A));
    }
}
