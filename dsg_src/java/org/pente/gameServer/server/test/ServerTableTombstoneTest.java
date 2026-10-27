package org.pente.gameServer.server.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.Vector;

import junit.framework.TestCase;

import org.pente.game.Game;
import org.pente.game.GameEventData;
import org.pente.game.GridState;
import org.pente.game.GridStateFactory;
import org.pente.game.MySQLGameVenueStorer;
import org.pente.game.SimpleGameEventData;
import org.pente.game.Time;
import org.pente.game.TombstoneCodes;
import org.pente.game.TombstoneState;
import org.pente.gameServer.client.GameTimer;
import org.pente.gameServer.client.GameTimerListener;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.ServerData;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.event.DSGEvent;
import org.pente.gameServer.event.DSGMoveTableErrorEvent;
import org.pente.gameServer.event.DSGMoveTableEvent;
import org.pente.gameServer.event.DSGGameStateTableEvent;
import org.pente.gameServer.event.DSGTableErrorEvent;
import org.pente.gameServer.event.DSGPingEvent;
import org.pente.gameServer.event.DSGCancelReplyTableEvent;
import org.pente.gameServer.event.DSGTombstoneSkipDeadTableErrorEvent;
import org.pente.gameServer.event.DSGTombstoneSkipDeadTableEvent;
import org.pente.gameServer.event.DSGUndoReplyTableEvent;
import org.pente.gameServer.event.DSGUndoRequestTableErrorEvent;
import org.pente.gameServer.event.DSGUndoRequestTableEvent;
import org.pente.gameServer.server.ActivityLogger;
import org.pente.gameServer.server.PingManager;
import org.pente.gameServer.server.Resources;
import org.pente.gameServer.server.ServerTable;

/**
 * Tombstone Pente live table behaviour: clocks in the dead-stone phase,
 * single-code move broadcasts, phase-aware validity. Extended by later tasks.
 */
public class ServerTableTombstoneTest extends TestCase {

    static final int INC = 10;

    public ServerTableTombstoneTest(String name) {
        super(name);
    }

    private static void assertFalse(boolean b) {
        assertTrue(!b);
    }

    private static void assertFalse(String m, boolean b) {
        assertTrue(m, !b);
    }

    /** No thread; time only changes through increment/adjust/reset. */
    static final class FakeTimer implements GameTimer {
        long millis;
        int startMinutes, startSeconds;
        int goCalls, stopCalls, resetCalls, incrementCalls;
        boolean running;

        public void addGameTimerListener(GameTimerListener l) {
        }

        public void removeGameTimerListener(GameTimerListener l) {
        }

        public void setStartMinutes(int m) {
            startMinutes = m;
        }

        public int getStartMinutes() {
            return startMinutes;
        }

        public void setStartSeconds(int s) {
            startSeconds = s;
        }

        public int getStartSeconds() {
            return startSeconds;
        }

        public void increment(int s) {
            incrementCalls++;
            millis += s * 1000L;
        }

        public void incrementMillis(int ms) {
            millis += ms;
        }

        public void adjust(int m, int s) {
            millis = (m * 60L + s) * 1000L;
        }

        public void adjust(int m, int s, int ms) {
            millis = (m * 60L + s) * 1000L + ms;
        }

        public int getMinutes() {
            return (int) (millis / 60000L);
        }

        public int getSeconds() {
            return (int) ((millis / 1000L) % 60L);
        }

        public long getMillis() {
            return millis;
        }

        public void reset() {
            resetCalls++;
            millis = (startMinutes * 60L + startSeconds) * 1000L;
        }

        public void go() {
            goCalls++;
            running = true;
        }

        public boolean isRunning() {
            return running;
        }

        public void stop() {
            stopCalls++;
            running = false;
        }

        public void destroy() {
        }
    }

    static final class TombTable extends ServerTable {
        final ServerTableKidTest.Router router = new ServerTableKidTest.Router();
        final List<String> gameOvers = new ArrayList<String>();

        TombTable(boolean timed, int initialMinutes, int... offeredGameIds) {
            tableNum = 3;
            dsgEventRouter = router;
            playersInMainRoom = new Vector<DSGPlayerData>();
            serverData = new ServerData();
            for (int id : offeredGameIds) {
                SimpleGameEventData ev = new SimpleGameEventData();
                ev.setGame(id);
                ev.setName(MySQLGameVenueStorer.LIVE_EVENT);
                serverData.getGameEvents().add(ev);
            }
            pingManager = new PingManager() {
                public void receivePingEvent(DSGPingEvent e) {
                }

                public long getPingTime(String p) {
                    return 0;
                }

                public void addPlayer(String p) {
                }

                public void removePlayer(String p) {
                }

                public void destroy() {
                }
            };
            activityLogger = new ActivityLogger(new Resources()) {
                public void updateGameState(long s, int t, long h, int[] m) {
                }
            };
            this.timed = timed;
            this.initialMinutes = initialMinutes;
            this.incrementalSeconds = INC;
            timers = new GameTimer[]{null, new FakeTimer(), new FakeTimer()};
            for (int i = 1; i <= 2; i++) {
                timers[i].setStartMinutes(initialMinutes);
                timers[i].setStartSeconds(initialMinutes == 0 ? INC : 0);
                timers[i].reset();
            }
            seat("alice", 1);
            seat("bob", 2);
            game = GridStateFactory.TOMBSTONE_GAME;
            gridState = GridStateFactory.createGridState(GridStateFactory.TOMBSTONE, 19, 19);
            state = DSGGameStateTableEvent.GAME_IN_PROGRESS;
        }

        private void seat(String name, int seat) {
            SimpleDSGPlayerData d = new SimpleDSGPlayerData();
            d.setPlayerID(seat);
            d.setName(name);
            d.setPlayerType(DSGPlayerData.HUMAN);
            d.setStatus(DSGPlayerData.ACTIVE);
            playersInTable.add(d);
            playersInMainRoom.add(d);
            sittingPlayers[seat] = d;
            playingPlayers[seat] = d;
        }

        protected void gameOver(boolean draw, String w, String l, boolean resign,
                                boolean timeup, boolean force) {
            gameOvers.add(draw + "|" + w + "|" + l);
            state = DSGGameStateTableEvent.NO_GAME_IN_PROGRESS;
        }

        protected void sendTimers(String toPlayer) {
        }

        FakeTimer timer(int seat) {
            return (FakeTimer) timers[seat];
        }

        List<Time> times() {
            return moveTimes;
        }

        GridState grid() {
            return gridState;
        }

        Game currentGame() {
            return game;
        }

        int initialMinutes() {
            return initialMinutes;
        }

        void state(int s) {
            state = s;
        }

        void undoFlag(boolean b) {
            undoRequested = b;
        }

        boolean undoFlag() {
            return undoRequested;
        }

        void cancelFlag(boolean b) {
            cancelRequested = b;
        }

        void addSpectator(String name) {
            SimpleDSGPlayerData d = new SimpleDSGPlayerData();
            d.setName(name);
            d.setPlayerType(DSGPlayerData.HUMAN);
            playersInTable.add(d);
        }

        void useGame(Game g) {
            game = g;
            gridState = GridStateFactory.createGridState(g.getId(), 19, 19);
        }
    }

    static void turn(TombTable t, String p, int own, int deadPointOrMinus1) {
        t.handleMove(p, own);
        if (deadPointOrMinus1 >= 0) {
            t.handleMove(p, TombstoneCodes.deadCode(deadPointOrMinus1));
        }
    }

    static <T extends DSGEvent> List<T> to(TombTable t, String name, Class<T> type) {
        return t.router.to(name, type);
    }

    public void testOwnThenDeadIncrementsOnce() {
        TombTable t = new TombTable(true, 5, 33, 34);
        t.handleMove("alice", 180);
        t.handleMove("bob", 200);
        assertEquals(2, t.times().size());
        assertEquals(2, t.grid().getCurrentPlayer());
        assertEquals(0, t.timer(2).incrementCalls);
        assertEquals(1, t.timer(2).goCalls);            // started after the centre only
        t.handleMove("bob", TombstoneCodes.deadCode(201));
        assertEquals(3, t.times().size());
        assertEquals(1, t.timer(2).incrementCalls);
        assertEquals(5, t.times().get(2).getMinutes());
        assertEquals(10, t.times().get(2).getSeconds());
        assertEquals(1, t.timer(1).goCalls);
    }

    public void testEachPlacementIsBroadcastAsOneCode() {
        TombTable t = new TombTable(true, 5, 33, 34);
        turn(t, "alice", 180, -1);
        turn(t, "bob", 200, 201);
        List<DSGMoveTableEvent> seen = to(t, "alice", DSGMoveTableEvent.class);
        assertEquals(3, seen.size());
        assertEquals(180, seen.get(0).getMove());
        assertEquals(200, seen.get(1).getMove());
        assertEquals(361 + 201, seen.get(2).getMove());
        for (DSGMoveTableEvent e : seen) assertEquals(1, e.getMoves().length);
    }

    public void testOwnCodeInDeadPhaseIsInvalidMove() {
        TombTable t = new TombTable(true, 5, 33, 34);
        turn(t, "alice", 180, -1);
        t.handleMove("bob", 200);
        t.handleMove("bob", 201);
        List<DSGMoveTableErrorEvent> errs = to(t, "bob", DSGMoveTableErrorEvent.class);
        assertEquals(1, errs.size());
        assertEquals(DSGTableErrorEvent.INVALID_MOVE, errs.get(0).getError());
        assertEquals(2, t.grid().getNumMoves());
    }

    public void testOpponentInDeadPhaseIsNotTurn() {
        TombTable t = new TombTable(true, 5, 33, 34);
        turn(t, "alice", 180, -1);
        t.handleMove("bob", 200);
        t.handleMove("alice", TombstoneCodes.deadCode(5));
        assertEquals(DSGTableErrorEvent.NOT_TURN,
            to(t, "alice", DSGMoveTableErrorEvent.class).get(0).getError());
    }

    public void testOwnPassThenDeadOnPointZero() {
        TombTable t = new TombTable(true, 5, 33, 34);
        turn(t, "alice", 180, -1);
        turn(t, "bob", TombstoneCodes.OWN_PASS, 0);          // dead code 361
        assertEquals(3, t.grid().getNumMoves());
        assertEquals(361, t.grid().getMove(2));
        assertEquals(3, t.times().size());
        assertEquals(1, t.grid().getCurrentPlayer());
    }

    public void testZeroMinuteOwnEntryDoesNotResetClock() {
        TombTable t = new TombTable(true, 0, 33, 34);
        turn(t, "alice", 180, -1);
        int resets = t.timer(2).resetCalls, goes = t.timer(2).goCalls;
        t.handleMove("bob", 200);
        assertEquals(resets, t.timer(2).resetCalls);
        assertEquals(goes, t.timer(2).goCalls);
        t.handleMove("bob", TombstoneCodes.deadCode(201));
        assertEquals(resets + 1, t.timer(2).resetCalls);
    }

    public void testWinningOwnStoneEndsGameAtOnce() {
        TombTable t = new TombTable(true, 5, 33, 34);
        turn(t, "alice", 180, -1);                            // K10 row: 180..184
        turn(t, "bob", 0, 340);
        turn(t, "alice", 181, 341);
        turn(t, "bob", 1, 342);
        turn(t, "alice", 182, 343);
        turn(t, "bob", 2, 344);
        turn(t, "alice", 183, 345);
        turn(t, "bob", 3, 346);
        t.handleMove("alice", 184);
        assertEquals(Arrays.asList("false|alice|bob"), t.gameOvers);
        assertEquals(t.grid().getNumMoves(), t.times().size());
    }

    public void testOwnThenSkipClosesTurnWithOneIncrement() {
        TombTable t = new TombTable(true, 5, 33, 34);
        turn(t, "alice", 180, -1);
        t.handleMove("bob", 200);
        t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent("bob", 3, false));
        assertEquals(1, t.grid().getCurrentPlayer());
        assertEquals(2, t.times().size());
        assertEquals(5, t.times().get(1).getMinutes());
        assertEquals(10, t.times().get(1).getSeconds());
        assertEquals(1, t.timer(2).incrementCalls);
        assertTrue(t.timer(1).isRunning());
        List<DSGTombstoneSkipDeadTableEvent> a = to(t, "alice", DSGTombstoneSkipDeadTableEvent.class);
        assertEquals(1, a.size());
        assertFalse(a.get(0).isSilent());
        assertEquals("bob", a.get(0).getPlayer());
        assertEquals(1, to(t, "bob", DSGTombstoneSkipDeadTableEvent.class).size());
    }

    public void testSixEmptyTurnsDrawViaSkip() {
        TombTable t = new TombTable(true, 5, 33, 34);
        turn(t, "alice", 180, -1);
        String[] movers = {"bob", "alice", "bob", "alice", "bob", "alice"};
        for (int i = 0; i < 6; i++) {
            t.handleMove(movers[i], TombstoneCodes.OWN_PASS);
            assertTrue("draws only on a skip", t.gameOvers.isEmpty());
            t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent(movers[i], 3, false));
        }
        assertEquals(Arrays.asList("true|alice|bob"), t.gameOvers);
    }

    private static void assertSkipError(Consumer<TombTable> setup, String sender, int code) {
        TombTable t = new TombTable(true, 5, 33, 34);
        t.handleMove("alice", 180);
        setup.accept(t);
        if (t.grid().getNumMoves() == 0) {
            t.handleMove("alice", 180);
        }
        int moves = t.grid().getNumMoves();
        int times = t.times().size();
        t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent(sender, 3, false));
        List<DSGTombstoneSkipDeadTableErrorEvent> errs =
            to(t, sender, DSGTombstoneSkipDeadTableErrorEvent.class);
        assertEquals(1, errs.size());
        assertEquals(code, errs.get(0).getError());
        for (String other : new String[]{"alice", "bob"}) {
            if (!other.equals(sender)) {
                assertEquals(0, to(t, other, DSGTombstoneSkipDeadTableErrorEvent.class).size());
            }
        }
        assertEquals(moves, t.grid().getNumMoves());
        assertEquals(times, t.times().size());
    }

    public void testSkipErrorCodes() {
        assertSkipError(table -> {}, "carol", DSGTableErrorEvent.NOT_IN_TABLE);
        assertSkipError(table -> table.addSpectator("sam"), "sam", DSGTableErrorEvent.NOT_SITTING);
        assertSkipError(table -> table.state(DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN),
            "bob", DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN);
        assertSkipError(table -> table.state(DSGGameStateTableEvent.NO_GAME_IN_PROGRESS),
            "bob", DSGTableErrorEvent.NO_GAME_IN_PROGRESS);
        assertSkipError(table -> table.state(DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET),
            "bob", DSGTableErrorEvent.NO_GAME_IN_PROGRESS);
        assertSkipError(table -> table.useGame(GridStateFactory.PENTE_GAME), "bob",
            DSGTableErrorEvent.INVALID_MOVE);
        assertSkipError(table -> table.handleMove("bob", 200), "alice", DSGTableErrorEvent.NOT_TURN);
        assertSkipError(table -> {}, "bob", DSGTableErrorEvent.NOT_AWAITING_DEAD_STONE);
    }

    public void testSkipDeclinesPendingUndoAndCancel() {
        TombTable t = new TombTable(true, 5, 33, 34);
        turn(t, "alice", 180, -1);
        t.handleMove("bob", 200);
        t.undoFlag(true);
        t.cancelFlag(true);
        t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent("bob", 3, false));
        assertFalse(t.undoFlag());
        List<DSGUndoReplyTableEvent> r = to(t, "alice", DSGUndoReplyTableEvent.class);
        assertEquals(1, r.size());
        assertFalse(r.get(0).getAccepted());
        assertEquals(1, to(t, "alice", DSGCancelReplyTableEvent.class).size());
    }

    public void testUntimedSkipTouchesNoClock() {
        TombTable t = new TombTable(false, 5, 33, 34);
        turn(t, "alice", 180, -1);
        t.handleMove("bob", 200);
        t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent("bob", 3, false));
        assertEquals(0, t.times().size());
        for (int s = 1; s <= 2; s++) {
            assertEquals(0, t.timer(s).goCalls + t.timer(s).stopCalls + t.timer(s).incrementCalls);
        }
        assertEquals(1, t.grid().getCurrentPlayer());
    }

    public void testZeroMinuteSkipResetsClockOnce() {
        TombTable t = new TombTable(true, 0, 33, 34);
        turn(t, "alice", 180, -1);
        t.handleMove("bob", 200);
        int resets = t.timer(2).resetCalls;
        t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent("bob", 3, false));
        assertEquals(resets + 1, t.timer(2).resetCalls);
        assertEquals(0, t.timer(2).incrementCalls);
        assertEquals(0, t.times().get(1).getMinutes());
        assertEquals(10, t.times().get(1).getSeconds());
    }

    public void testUndoTwoEntryTurn() {
        TombTable t = new TombTable(true, 5, 33, 34);
        turn(t, "alice", 180, -1);
        turn(t, "bob", 200, 201);
        t.handleUndoRequest(new DSGUndoRequestTableEvent("bob", 3));
        t.handleUndoReply(new DSGUndoReplyTableEvent("alice", 3, true));
        assertEquals(1, t.grid().getNumMoves());
        assertEquals(1, t.times().size());
        assertEquals(2, t.grid().getCurrentPlayer());
        assertEquals(5, t.timer(2).getMinutes());
        assertEquals(0, t.timer(2).getSeconds());            // increment taken back
    }

    public void testUndoOwnOnlyTurnClosedBySkip() {
        TombTable t = new TombTable(true, 5, 33, 34);
        turn(t, "alice", 180, -1);
        t.handleMove("bob", 200);
        t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent("bob", 3, false));
        t.handleUndoRequest(new DSGUndoRequestTableEvent("bob", 3));
        t.handleUndoReply(new DSGUndoReplyTableEvent("alice", 3, true));
        assertEquals(1, t.grid().getNumMoves());
        assertEquals(1, t.times().size());
        assertFalse(((TombstoneState) t.grid()).isAwaitingDeadStone());
    }

    public void testUndoRestoresAcceptorsPostIncrementTimeAfterSkip() {
        TombTable t = new TombTable(true, 5, 33, 34);
        turn(t, "alice", 180, -1);
        t.handleMove("bob", 200);
        t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent("bob", 3, false)); // bob 5:10
        turn(t, "alice", 220, 221);                                                      // alice 5:10
        t.handleUndoRequest(new DSGUndoRequestTableEvent("alice", 3));
        t.handleUndoReply(new DSGUndoReplyTableEvent("bob", 3, true));
        assertEquals(2, t.grid().getNumMoves());
        assertEquals(2, t.times().size());
        assertEquals(5, t.timer(2).getMinutes());
        assertEquals(10, t.timer(2).getSeconds());           // bob restored post-increment
        assertEquals(5, t.timer(1).getMinutes());
        assertEquals(0, t.timer(1).getSeconds());            // alice loses her increment
    }

    public void testNoUndoMidTurnOrOfCentre() {
        TombTable t = new TombTable(true, 5, 33, 34);
        turn(t, "alice", 180, -1);
        t.handleUndoRequest(new DSGUndoRequestTableEvent("alice", 3));
        t.handleMove("bob", 200);
        t.handleUndoRequest(new DSGUndoRequestTableEvent("bob", 3));
        List<DSGUndoRequestTableErrorEvent> a = to(t, "alice", DSGUndoRequestTableErrorEvent.class);
        List<DSGUndoRequestTableErrorEvent> b = to(t, "bob", DSGUndoRequestTableErrorEvent.class);
        assertEquals(DSGTableErrorEvent.CANT_UNDO, a.get(0).getError());
        assertEquals(DSGTableErrorEvent.CANT_UNDO, b.get(0).getError());
        assertFalse(t.undoFlag());
    }
}
