package org.pente.turnBased.test;

import junit.framework.TestCase;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.tourney.TourneyMatch;
import org.pente.turnBased.CacheTBStorer;
import org.pente.turnBased.TBGame;

/**
 * A drawn TB tourney game must map to RESULT_TIE (4), never to raw
 * getWinner()==0, which collides with RESULT_UNFINISHED and stalls the
 * round forever (hasBeenPlayed() is result != 0).
 */
public class TourneyTieMappingTest extends TestCase {

    public TourneyTieMappingTest(String name) {
        super(name);
    }

    private TBGame completedGame(int winner, boolean swapped) {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_DPENTE);
        g.setPlayer1Pid(1001L);
        g.setPlayer2Pid(1002L);
        // Same state/setWinner sequence as
        // TBGamePassDrawTest.testTimeoutDrawDerivation: set the completed
        // state first, then setWinner(0) marks a draw.
        g.setState(TBGame.STATE_COMPLETED);
        if (swapped) {
            // Real seat-swap API (TBGameSeatsSwappedTest.testDPenteFamilySwapRestoresOriginalPids):
            // physically swaps the pids and flips seatsSwapped() to true.
            g.dPenteSwap(true);
        }
        g.setWinner(winner);
        return g;
    }

    public void testDrawMapsToResultTie() {
        TBGame g = completedGame(0, false);
        assertTrue(g.isDraw());
        assertEquals(TourneyMatch.RESULT_TIE, CacheTBStorer.tourneyResult(g));
    }

    public void testDrawWithSwappedSeatsStillTie() {
        TBGame g = completedGame(0, true);
        assertEquals(TourneyMatch.RESULT_TIE, CacheTBStorer.tourneyResult(g));
    }

    public void testP1WinUnswapped() {
        assertEquals(TourneyMatch.RESULT_P1_WINS,
                CacheTBStorer.tourneyResult(completedGame(1, false)));
    }

    public void testP1WinSwappedFlipsToP2() {
        assertEquals(TourneyMatch.RESULT_P2_WINS,
                CacheTBStorer.tourneyResult(completedGame(1, true)));
    }
}
