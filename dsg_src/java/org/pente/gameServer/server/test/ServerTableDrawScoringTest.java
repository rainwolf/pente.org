package org.pente.gameServer.server.test;

import junit.framework.TestCase;

import org.pente.game.DefaultGameData;
import org.pente.game.DefaultPlayerData;
import org.pente.game.Game;
import org.pente.game.GameData;
import org.pente.game.GridStateFactory;
import org.pente.game.PlayerData;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.LiveSet;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.server.ServerTable;
import org.pente.gameServer.tourney.TourneyMatch;

/**
 * A drawn live game must end with winner 0 everywhere downstream, and a
 * two-game set is scored by points (win = 1, draw = 1/2, loss = 0).
 * <p>
 * The gameOver() cases drive the real ServerTable.gameOver() through a
 * subclass that stubs out timers, broadcasting and the database update and
 * records what would have been passed on.
 */
public class ServerTableDrawScoringTest extends TestCase {

    private static final long ALICE = 11;
    private static final long BOB = 22;

    public ServerTableDrawScoringTest(String name) {
        super(name);
    }

    // ---- scoreTwoGameSet: pure set scoring -------------------------------

    public void testWinWinIsSetWinForThatPlayer() {
        assertEquals(1, ServerTable.scoreTwoGameSet(ALICE, BOB, ALICE, ALICE));
        assertEquals(2, ServerTable.scoreTwoGameSet(ALICE, BOB, BOB, BOB));
    }

    public void testSplitIsSetDraw() {
        assertEquals(0, ServerTable.scoreTwoGameSet(ALICE, BOB, ALICE, BOB));
        assertEquals(0, ServerTable.scoreTwoGameSet(ALICE, BOB, BOB, ALICE));
    }

    public void testDrawPlusWinIsSetWinForTheWinner() {
        assertEquals(1, ServerTable.scoreTwoGameSet(ALICE, BOB, 0, ALICE));
        assertEquals(1, ServerTable.scoreTwoGameSet(ALICE, BOB, ALICE, 0));
        assertEquals(2, ServerTable.scoreTwoGameSet(ALICE, BOB, 0, BOB));
        assertEquals(2, ServerTable.scoreTwoGameSet(ALICE, BOB, BOB, 0));
    }

    public void testDrawDrawIsSetDraw() {
        assertEquals(0, ServerTable.scoreTwoGameSet(ALICE, BOB, 0, 0));
    }

    // ---- liveTourneyResult: TourneyMatch result for a live game ----------

    public void testLiveTourneyDrawIsTie() {
        assertEquals(TourneyMatch.RESULT_TIE, ServerTable.liveTourneyResult(0, false));
        assertEquals(TourneyMatch.RESULT_TIE, ServerTable.liveTourneyResult(0, true));
    }

    public void testLiveTourneyWinKeepsSeatSwapCorrection() {
        assertEquals(TourneyMatch.RESULT_P1_WINS, ServerTable.liveTourneyResult(1, false));
        assertEquals(TourneyMatch.RESULT_P2_WINS, ServerTable.liveTourneyResult(2, false));
        assertEquals(TourneyMatch.RESULT_P2_WINS, ServerTable.liveTourneyResult(1, true));
        assertEquals(TourneyMatch.RESULT_P1_WINS, ServerTable.liveTourneyResult(2, true));
    }

    // ---- gameOver(): draw -> winner 0 ------------------------------------

    public void testSingleGameSetDrawHasNoWinner() {
        LiveSet set = newSet();
        CapturingTable t = new CapturingTable(GridStateFactory.RENJU_GAME, set, "alice", "bob");

        // accepted renju draw offer: callers pass seat 1 as "winner"
        t.end(true, "alice", "bob", false);

        assertEquals(0, t.dbWinner);
        assertEquals(0, set.getWinner());
        assertEquals(LiveSet.STATUS_COMPLETED, set.getStatus());
    }

    public void testSingleGameSetTimeoutDrawHasNoWinner() {
        LiveSet set = newSet();
        CapturingTable t = new CapturingTable(GridStateFactory.RENJU_GAME, set, "alice", "bob");

        // renju timeout draw: seat 1 timed out, seat 2 passed as "winner"
        t.end(true, "bob", "alice", true);

        assertEquals(0, t.dbWinner);
        assertEquals(0, set.getWinner());
    }

    public void testSingleGameSetWinUnchanged() {
        LiveSet set = newSet();
        CapturingTable t = new CapturingTable(GridStateFactory.RENJU_GAME, set, "alice", "bob");

        t.end(false, "bob", "alice", false);

        assertEquals(2, t.dbWinner);
        assertEquals(2, set.getWinner());
        assertEquals("bob", t.dbWinnerPlayer);
        assertEquals("alice", t.dbLoserPlayer);
    }

    public void testUnratedDrawHasNoWinner() {
        CapturingTable t = new CapturingTable(GridStateFactory.PENTE_GAME, null, "alice", "bob");

        t.end(true, "alice", "bob", false);

        assertEquals(0, t.dbWinner);
    }

    // ---- gameOver(): two-game set scoring --------------------------------
    // set p1 = alice, p2 = bob; game 1 was played alice (seat 1) vs bob

    public void testGame1WinGame2DrawIsSetWinEvenWhenLoserSitsInSeat1() {
        LiveSet set = newSetAfterGame1(1); // alice won game 1
        // game 2 seats swapped: bob is seat 1, so a draw names bob "winner"
        CapturingTable t = new CapturingTable(GridStateFactory.GOMOKU_GAME, set, "bob", "alice");

        t.end(true, "bob", "alice", false);

        assertEquals(0, t.dbWinner);
        assertEquals(1, set.getWinner());
        assertEquals(LiveSet.STATUS_COMPLETED, set.getStatus());
        assertEquals("alice", t.dbWinnerPlayer);
        assertEquals("bob", t.dbLoserPlayer);
        assertTrue(t.reason, t.reason.indexOf("alice, wins the set!") != -1);
    }

    public void testGame1DrawGame2WinIsSetWinForGame2Winner() {
        LiveSet set = newSetAfterGame1(0); // game 1 drawn
        CapturingTable t = new CapturingTable(GridStateFactory.GOMOKU_GAME, set, "bob", "alice");

        t.end(false, "bob", "alice", false);

        assertEquals(2, set.getWinner());
        assertEquals("bob", t.dbWinnerPlayer);
        assertEquals("alice", t.dbLoserPlayer);
        assertTrue(t.reason, t.reason.indexOf("bob, wins the set!") != -1);
    }

    public void testDrawDrawIsSetDrawInGameOver() {
        LiveSet set = newSetAfterGame1(0);
        CapturingTable t = new CapturingTable(GridStateFactory.GOMOKU_GAME, set, "bob", "alice");

        t.end(true, "bob", "alice", false);

        assertEquals(0, t.dbWinner);
        assertEquals(0, set.getWinner());
        assertTrue(t.reason, t.reason.indexOf("set is a draw") != -1);
    }

    public void testSplitIsSetDrawInGameOver() {
        LiveSet set = newSetAfterGame1(2); // bob won game 1
        CapturingTable t = new CapturingTable(GridStateFactory.GOMOKU_GAME, set, "bob", "alice");

        t.end(false, "alice", "bob", false);

        assertEquals(0, set.getWinner());
        assertTrue(t.reason, t.reason.indexOf("set is a draw") != -1);
    }

    public void testWinWinUnchanged() {
        LiveSet set = newSetAfterGame1(1); // alice won game 1
        CapturingTable t = new CapturingTable(GridStateFactory.GOMOKU_GAME, set, "bob", "alice");

        t.end(false, "alice", "bob", false);

        assertEquals(2, t.dbWinner); // alice sits in seat 2 for game 2
        assertEquals(1, set.getWinner());
        assertEquals("alice", t.dbWinnerPlayer);
        assertEquals("bob", t.dbLoserPlayer);
        assertTrue(t.reason, t.reason.indexOf("alice, wins the set!") != -1);
    }

    // ---- helpers ---------------------------------------------------------

    private static LiveSet newSet() {
        LiveSet set = new LiveSet();
        set.setSid(1);
        set.setP1Pid(ALICE);
        set.setP2Pid(BOB);
        set.setStatus(LiveSet.STATUS_ACTIVE);
        return set;
    }

    /** set with game 1 recorded, alice in seat 1; g1Winner is 0, 1 or 2 */
    private static LiveSet newSetAfterGame1(int g1Winner) {
        LiveSet set = newSet();
        GameData g1 = new DefaultGameData();
        g1.setGameID(100);
        g1.setPlayer1Data(playerData(ALICE, "alice"));
        g1.setPlayer2Data(playerData(BOB, "bob"));
        g1.setWinner(g1Winner);
        set.setG1(g1);
        set.setStatus(LiveSet.STATUS_ONE_GAME_COMPLETED);
        return set;
    }

    private static PlayerData playerData(long pid, String name) {
        PlayerData p = new DefaultPlayerData();
        p.setUserID(pid);
        p.setUserIDName(name);
        return p;
    }

    private static DSGPlayerData player(String name) {
        SimpleDSGPlayerData p = new SimpleDSGPlayerData();
        p.setName(name);
        p.setPlayerID(name.equals("alice") ? ALICE : BOB);
        p.setPlayerType(DSGPlayerData.HUMAN);
        return p;
    }

    /** ServerTable whose side effects are stubbed out and recorded. */
    private static class CapturingTable extends ServerTable {
        String dbWinnerPlayer;
        String dbLoserPlayer;
        int dbWinner = -99;
        String reason;

        CapturingTable(Game g, LiveSet liveSet, String seat1, String seat2) {
            game = g;
            rated = liveSet != null;
            set = liveSet;
            playingPlayers[1] = player(seat1);
            playingPlayers[2] = player(seat2);
            sittingPlayers[1] = playingPlayers[1];
            sittingPlayers[2] = playingPlayers[2];
        }

        void end(boolean draw, String winnerPlayer, String loserPlayer, boolean timeup) {
            gameOver(draw, winnerPlayer, loserPlayer, false, timeup, false);
        }

        protected void resetTableGameOver() {
        }

        protected void sendTimers(String toPlayer) {
        }

        protected void changeGameState(int newState, String reason, String winner, int gameInSet) {
            this.reason = reason;
        }

        protected void updateDatabaseAfterGameOverInSeparateThread(
                String winnerPlayer, String loserPlayer, int winner,
                LiveSet localSet, String status) {
            dbWinnerPlayer = winnerPlayer;
            dbLoserPlayer = loserPlayer;
            dbWinner = winner;
        }

        protected boolean noHumanPlayersInTable() {
            return false;
        }
    }
}
