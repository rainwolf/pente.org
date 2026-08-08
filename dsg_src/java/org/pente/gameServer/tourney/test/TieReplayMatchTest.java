package org.pente.gameServer.tourney.test;

import junit.framework.TestCase;

import org.pente.gameServer.tourney.DoubleEliminationFormat;
import org.pente.gameServer.tourney.SingleEliminationFormat;
import org.pente.gameServer.tourney.TourneyMatch;
import org.pente.gameServer.tourney.TourneyPlayerData;

/**
 * Single-game-set games (e.g. TB Renju, 81) replay a tie as ONE new match
 * with the seats swapped -- the single=true overload. The 1-arg call produces
 * an UNSWAPPED more[0], so calling it for single-game sets repeats colours and
 * can re-tie forever, stalling the tourney.
 */
public class TieReplayMatchTest extends TestCase {

    public TieReplayMatchTest(String name) {
        super(name);
    }

    /** Minimal player builder, same idiom as CacheTourneyStorerRedisTest. */
    private TourneyPlayerData playerWithPid(long pid) {
        TourneyPlayerData p = new TourneyPlayerData();
        p.setPlayerID(pid);
        p.setName("p" + pid);
        return p;
    }

    private TourneyMatch tiedMatch(TourneyPlayerData p1, TourneyPlayerData p2) {
        TourneyMatch m = new TourneyMatch();
        m.setEvent(5000);
        m.setRound(1);
        m.setSection(1);
        m.setSeq(1);
        m.setPlayer1(p1);
        m.setPlayer2(p2);
        m.setResult(TourneyMatch.RESULT_TIE);
        return m;
    }

    public void testSingleReplaySwapsSeatsAndBumpsSeq() {

        TourneyPlayerData p1 = playerWithPid(1001L);
        TourneyPlayerData p2 = playerWithPid(1002L);
        TourneyMatch original = tiedMatch(p1, p2);

        TourneyMatch[] more = new SingleEliminationFormat()
                .createMoreMatchesAfterTie(original, true);

        assertEquals(1002L, more[0].getPlayer1().getPlayerID());
        assertEquals(1001L, more[0].getPlayer2().getPlayerID());
        assertEquals(original.getSeq() + 1, more[0].getSeq());
        assertEquals(TourneyMatch.RESULT_UNFINISHED, more[0].getResult());
        assertEquals(original.getEvent(), more[0].getEvent());
        assertEquals(original.getRound(), more[0].getRound());
        assertNull(more[1]);
    }

    /**
     * The 1-arg call -- what the tie branch used for every game -- leaves
     * more[0] unswapped. This is exactly why single-game sets needed the
     * single=true overload: taking only more[0] repeated the same colours.
     */
    public void testOneArgReplayLeavesFirstMatchUnswapped() {

        TourneyPlayerData p1 = playerWithPid(1001L);
        TourneyPlayerData p2 = playerWithPid(1002L);
        TourneyMatch original = tiedMatch(p1, p2);

        TourneyMatch[] more = new SingleEliminationFormat()
                .createMoreMatchesAfterTie(original);

        assertEquals(1001L, more[0].getPlayer1().getPlayerID());
        assertEquals(1002L, more[0].getPlayer2().getPlayerID());
        assertEquals(1002L, more[1].getPlayer1().getPlayerID());
        assertEquals(1001L, more[1].getPlayer2().getPlayerID());
    }

    /**
     * DoubleEliminationFormat extends SingleEliminationFormat, so double-elim
     * tourneys DO reach the tie branch in CacheTourneyStorer.applyMatchTo. It
     * overrides the 1-arg createMoreMatchesAfterTie but NOT the 2-arg one, so
     * the branch's new 2-arg call dispatches to the inherited implementation.
     * That is safe only while the override carries no double-elim-specific
     * logic -- pin that here so a future divergence fails loudly instead of
     * being silently bypassed.
     */
    public void testDoubleElimOverrideMatchesInheritedTwoArgFalse() {

        TourneyPlayerData p1 = playerWithPid(1001L);
        TourneyPlayerData p2 = playerWithPid(1002L);

        TourneyMatch[] override = new DoubleEliminationFormat()
                .createMoreMatchesAfterTie(tiedMatch(p1, p2));
        TourneyMatch[] inherited = new DoubleEliminationFormat()
                .createMoreMatchesAfterTie(tiedMatch(p1, p2), false);

        for (int i = 0; i < 2; i++) {
            assertEquals("more[" + i + "] event",
                    override[i].getEvent(), inherited[i].getEvent());
            assertEquals("more[" + i + "] round",
                    override[i].getRound(), inherited[i].getRound());
            assertEquals("more[" + i + "] section",
                    override[i].getSection(), inherited[i].getSection());
            assertEquals("more[" + i + "] seq",
                    override[i].getSeq(), inherited[i].getSeq());
            assertEquals("more[" + i + "] player1",
                    override[i].getPlayer1().getPlayerID(),
                    inherited[i].getPlayer1().getPlayerID());
            assertEquals("more[" + i + "] player2",
                    override[i].getPlayer2().getPlayerID(),
                    inherited[i].getPlayer2().getPlayerID());
        }
    }
}
