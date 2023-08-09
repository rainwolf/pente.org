package org.pente.gameServer.tourney.test;

import java.util.List;

import org.apache.log4j.Category;
import org.pente.gameServer.tourney.DoubleEliminationFormat;
import org.pente.gameServer.tourney.Tourney;
import org.pente.gameServer.tourney.TourneyListener;
import org.pente.gameServer.tourney.TourneyMatch;
import org.pente.gameServer.tourney.TourneyRound;
import org.pente.gameServer.tourney.TourneyStorer;

public class DummyTourneyStorer implements TourneyStorer {

    private Category log4j = Category.getInstance(
            DoubleEliminationFormat.class.getName());

    public List getUpcomingTournies() throws Throwable {
        return null;
    }

    public List getCurrentTournies() throws Throwable {
        return null;
    }

    public List getCompletedTournies() throws Throwable {
        return null;
    }

    public void insertTourney(Tourney tourney) throws Throwable {
        log4j.debug("dummy.insertTourney()");
    }

    public Tourney getTourney(int eid) throws Throwable {
        log4j.debug("dummy.getTourney(" + eid + ")");
        return null;
    }

    public Tourney getTourneyDetails(int eid) throws Throwable {
        log4j.debug("dummy.getTourneyDetails(" + eid + ")");
        return null;
    }

    public TourneyMatch getUnplayedMatch(long player1ID, long player2ID, int eid)
            throws Throwable {
        log4j.debug("dummy.getUnplayedMatch()");
        return null;
    }

    public void insertRound(TourneyRound round) throws Throwable {
        log4j.debug("dummy.insertRound(" + round.getRound() + ")");
    }

    public void insertMatch(TourneyMatch tourneyMatch) throws Throwable {
        log4j.debug("dummy.insertMatch(" + tourneyMatch.getPlayer1().getName() +
                ", " + tourneyMatch.getPlayer2().getName() + ")");
    }

    public void updateMatch(TourneyMatch tourneyMatch) throws Throwable {
        log4j.debug("dummy.updateMatch(" + tourneyMatch.getPlayer1().getName() +
                ", " + tourneyMatch.getPlayer2().getName() + "r=" + tourneyMatch.getResult() + ")");
    }

    public void updateMatches(List tourneyMatches, Tourney t) throws Throwable {
        log4j.debug("dummy.updateMatches()");
    }

    public void completeTourney(Tourney tourney) throws Throwable {
        log4j.debug("dummy.completeTourney()");
    }

    public void addPlayerToTourney(long pid, int eid) throws Throwable {

    }

    public void removePlayerFromTourney(long pid, int eid) throws Throwable {

    }

    public List getTourneyPlayers(int eid) throws Throwable {
        return null;
    }

    public List setInitialSeeds(int eid) throws Throwable {
        return null;
    }

    public void addTourneyListener(TourneyListener listener) {

    }

    public void removeTourneyListener(TourneyListener listener) {
    }

}
