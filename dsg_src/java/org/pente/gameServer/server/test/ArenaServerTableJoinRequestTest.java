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
}
