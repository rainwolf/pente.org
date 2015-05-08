package org.pente.gameServer.tourney;

import java.util.List;

public interface TourneyStorer {

    public List getUpcomingTournies() throws Throwable;
    public List getCurrentTournies() throws Throwable;
    public List getCompletedTournies() throws Throwable;

    public void insertTourney(Tourney tourney) throws Throwable;
    public Tourney getTourney(int eid) throws Throwable;
    public Tourney getTourneyDetails(int eid) throws Throwable;
    public TourneyMatch getUnplayedMatch(long player1ID, long player2ID, int eid)
        throws Throwable;
    public void insertRound(TourneyRound round) throws Throwable;
    public void insertMatch(TourneyMatch tourneyMatch) throws Throwable;
    public void updateMatch(TourneyMatch tourneyMatch) throws Throwable;
    public void updateMatches(List tourneyMatches, Tourney t) throws Throwable;
    public void completeTourney(Tourney tourney) throws Throwable;
    
    public void addPlayerToTourney(long pid, int eid) throws Throwable;
    public void removePlayerFromTourney(long pid, int eid) throws Throwable;
    public List getTourneyPlayers(int eid) throws Throwable;
    public List setInitialSeeds(int eid) throws Throwable;

    public void addTourneyListener(TourneyListener listener);
    public void removeTourneyListener(TourneyListener listener);
}
