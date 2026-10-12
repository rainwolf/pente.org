package org.pente.subscriptions.play.test;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.pente.gameServer.tourney.Tourney;
import org.pente.gameServer.tourney.TourneyPlayerData;
import org.pente.gameServer.tourney.TourneyRound;
import org.pente.gameServer.tourney.VacationPurchaseRule;

/**
 * The hideVacation rule shared by subscribe.jsp and the Play store (spec 2026-10-11 §4.4).
 *
 * Run: ant test-play-billing
 */
public class VacationPurchaseRuleTest extends TestCase {

    public VacationPurchaseRuleTest(String name) {
        super(name);
    }

    private static Tourney tourney(int game, int incrementalTime, String... lastRoundNames) {
        Tourney t = new Tourney(1);
        t.setGame(game);
        t.setIncrementalTime(incrementalTime);
        if (lastRoundNames.length > 0) {
            final List<TourneyPlayerData> players = new ArrayList<TourneyPlayerData>();
            for (String name : lastRoundNames) {
                TourneyPlayerData p = new TourneyPlayerData();
                p.setName(name);
                players.add(p);
            }
            t.addRound(new TourneyRound(1) {
                public List<TourneyPlayerData> getPlayers() {
                    return players;
                }
            });
        }
        return t;
    }

    public void testPlayerInLastRoundOfIncrementalTurnBasedTourneyIsBlocked() {
        assertTrue(VacationPurchaseRule.blocksVacation(tourney(51, 1, "alice", "bob"), "bob"));
    }

    public void testOtherPlayersAndOtherTourneysAreNotBlocked() {
        assertTrue(!VacationPurchaseRule.blocksVacation(tourney(51, 1, "alice"), "bob"));
        assertTrue(!VacationPurchaseRule.blocksVacation(tourney(1, 1, "bob"), "bob"));
        assertTrue(!VacationPurchaseRule.blocksVacation(tourney(51, 0, "bob"), "bob"));
        assertTrue(!VacationPurchaseRule.blocksVacation(tourney(51, 1), "bob"));
        assertTrue(!VacationPurchaseRule.blocksVacation(null, "bob"));
        assertTrue(!VacationPurchaseRule.blocksVacation(tourney(51, 1, "bob"), null));
    }

    public void testNameMatchIsExactLikeSubscribeJsp() {
        assertTrue(!VacationPurchaseRule.blocksVacation(tourney(51, 1, "Bob"), "bob"));
    }
}
