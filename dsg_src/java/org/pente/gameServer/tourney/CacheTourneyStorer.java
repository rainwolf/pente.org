package org.pente.gameServer.tourney;

import java.util.*;

import com.jivesoftware.oro.util.Cache;
import org.apache.log4j.*;

import org.pente.game.*;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBSet;
import org.pente.turnBased.Utilities;
import org.pente.turnBased.CacheTBStorer;
import org.pente.gameServer.core.*;

public class CacheTourneyStorer implements TourneyStorer {

    private static final Category log4j = Category.getInstance(
        CacheTourneyStorer.class.getName());
    
    private TourneyStorer backingStorer;
    private Map<Integer, Tourney> tournies = new HashMap<Integer, Tourney>();
    private List<Tourney> upcomingTournies = null;
    private List<Tourney> currentTournies = null;
    private List<Tourney> completedTournies = null;
    private Map<Integer, List<Long>> tourneyPlayerPids = null;
    private List<TourneyListener> listeners = new ArrayList<TourneyListener>();

    private CacheTBStorer tbStorer;
    private CacheDSGPlayerStorer dsgPlayerStorer;

    public void setDsgPlayerStorer(CacheDSGPlayerStorer dsgPlayerStorer) { this.dsgPlayerStorer = dsgPlayerStorer; }
    public void setTBStorer(CacheTBStorer tbStorer) {
        this.tbStorer = tbStorer;
    }

    public CacheTourneyStorer(TourneyStorer backingStorer) {
        this.backingStorer = backingStorer;
    }


    public void addTourneyListener(TourneyListener listener) {
        listeners.add(listener);
    }
    public void removeTourneyListener(TourneyListener listener) {
        listeners.remove(listener);
    }
    private void notifyListeners(TourneyEvent event) {
        for (Iterator it = listeners.iterator(); it.hasNext();) {
            TourneyListener tl = (TourneyListener) it.next();
            tl.tourneyEventOccurred(event);
        }
    }
    
    public synchronized void flushCache() {
        tournies.clear();
    }

    public synchronized void insertTourney(Tourney tourney) throws Throwable { 
        backingStorer.insertTourney(tourney);
        
        log4j.debug("insertTourney(" + tourney.getEventID() + "), cached");
        tournies.put(new Integer(tourney.getEventID()), tourney);
        upcomingTournies = null;
    }

    public List<Tourney> getUpcomingTournies() throws Throwable {
        // more complicated to cache, not that important anyways
        if (upcomingTournies == null) {
            upcomingTournies = backingStorer.getUpcomingTournies();
        } else {
            Date today = new Date();
            for (Iterator<Tourney> iterator = upcomingTournies.iterator(); iterator.hasNext();) {
                Tourney t = iterator.next();
                Tourney fullTourney = getTourney(t.getEventID());
                if (fullTourney.getSignupEndDate().before(today)) {
                    currentTournies.add(t);
                    iterator.remove();
                }
            }
        }
        return upcomingTournies;
    }


    public List<Tourney> getCurrentTournies() throws Throwable {
        // more complicated to cache, not that important anyways
        if (currentTournies == null) {
            currentTournies = backingStorer.getCurrentTournies();
        } else {
            Date today = new Date();
            for (Iterator<Tourney> iterator = currentTournies.iterator(); iterator.hasNext();) {
                Tourney t = iterator.next();
                Tourney fullTourney = getTourney(t.getEventID());
                if (fullTourney.getEndDate() != null && fullTourney.getEndDate().before(today)) {
                    iterator.remove();
                }
            }
        }
        return currentTournies;
    }
    public List<Tourney> getCompletedTournies() throws Throwable {
        // more complicated to cache, not that important anyways
        if (completedTournies == null) {
            completedTournies = backingStorer.getCompletedTournies();
        }
        return completedTournies;
    }
    
    public void completeTourney(Tourney tourney) throws Throwable { 
        log4j.debug("completeTourney(" + tourney.getEventID() + ")");
        
        tourney.setEndDate(new Date());
        backingStorer.completeTourney(tourney);
        
        // notify listeners that it's complete
        // used for speed-tournies to notify main room
        notifyListeners(new TourneyEvent(tourney.getEventID(),
            TourneyEvent.COMPLETE));
        completedTournies = null;
    }


    public synchronized Tourney getTourney(int eid) throws Throwable {
        // cache tourney data, including round/section/match data
        
        log4j.debug("getTourney(" + eid + ")");
        Tourney t = (Tourney) tournies.get(new Integer(eid));
        
        if (t != null) {
            log4j.debug("return cached copy");
        } else {
            if (!tournies.containsKey(new Integer(eid))) {
                log4j.debug("not cached, caching");
                t = backingStorer.getTourney(eid);
                tournies.put(new Integer(eid), t);
            }
        }
        
        return t;
    }
    
    public synchronized void addPlayerToTourney(long pid, int eid) throws Throwable {
        log4j.debug("addPlayerToTourney(" + pid + ", " + eid + ")");
        
        // don't update cache since pull current ratings with each query
        // could make cached by caching pid's only, then pulling
        // ratings from cacheplayerstorer
        backingStorer.addPlayerToTourney(pid, eid);
        
        if (tourneyPlayerPids != null) {
            List<Long> playerPids = tourneyPlayerPids.get(eid);
            if (playerPids != null) {
                playerPids.add(pid);
            }
        }
        
        notifyListeners(new TourneyEvent(eid, TourneyEvent.PLAYER_REGISTER,
            new Long(pid)));
    }
    public synchronized void removePlayerFromTourney(long pid, int eid) throws Throwable {
        log4j.debug("removePlayerFromTourney(" + pid + ", " + eid + ")");
        backingStorer.removePlayerFromTourney(pid, eid);

        if (tourneyPlayerPids != null) {
            List<Long> playerPids = tourneyPlayerPids.get(eid);
            if (playerPids != null) {
                playerPids.remove(pid);
            }
        }

        notifyListeners(new TourneyEvent(eid, TourneyEvent.PLAYER_DROP,
                new Long(pid)));
    }

 
    public List getTourneyPlayers(int eid) throws Throwable {
        // don't cache since pull current ratings with each query
        // could make cached by caching pid's only, then pulling
        // ratings from cacheplayerstorer
        return backingStorer.getTourneyPlayers(eid);
    }

    @Override
    public synchronized List<Long> getTourneyPlayerPids(int eid) throws Throwable {
        if (tourneyPlayerPids == null) {
            tourneyPlayerPids = new HashMap<>();
        }
        List<Long> playerPids = tourneyPlayerPids.get(eid);
        if (playerPids == null) {
            playerPids = backingStorer.getTourneyPlayerPids(eid);
            tourneyPlayerPids.put(eid, playerPids);
        }
        
        return playerPids;
    }

    public Tourney getTourneyDetails(int eid) throws Throwable {
        log4j.debug("getTourneyDetails(" + eid + ")");
        return getTourney(eid);
    }


    public synchronized TourneyMatch getUnplayedMatch(long player1ID, 
        long player2ID, int eid)
        throws Throwable {
        
        log4j.debug("getUnplayedMatch(" + player1ID + ", " + player2ID + ", " + eid + ")");
        
        Tourney t = getTourney(eid);
        for (Iterator sections = t.getLastRound().getSections().iterator();
             sections.hasNext();) {
            TourneySection s = (TourneySection) sections.next();
            log4j.debug("checking section " + s.getSection());
            TourneyMatch m = s.getUnplayedMatch(player1ID, player2ID);
            if (m != null) {
                return m;
            }
        }

        return null;
    }

    public List setInitialSeeds(int eid) throws Throwable {
        // not necessary to cache yet
        return backingStorer.setInitialSeeds(eid);
    }

    public synchronized void insertRound(TourneyRound round) throws Throwable {
        log4j.debug("insertRound(" + round.getRound() + ")");
        //backingStorer.insertRound(round, eid);
        
        int eid = -1;
        for (Iterator sections = round.getSections().iterator(); sections.hasNext();) {
            TourneySection s = (TourneySection) sections.next();
            for (Iterator matches = s.getMatches().iterator(); matches.hasNext();) {
                TourneyMatch m = (TourneyMatch) matches.next();
                insertMatch(m);
                eid = m.getEvent();
            }
        }

        
        // notify listeners of new round
        // used for speed-tournies to notify main room
        notifyListeners(new TourneyEvent(eid, TourneyEvent.NEW_ROUND));
        
        // don't need to cache round, should have already been done by client
    }
  
    public synchronized void insertMatch(TourneyMatch tourneyMatch) throws Throwable {
        log4j.debug("insertMatch(" + tourneyMatch.getMatchID() + ")");
        backingStorer.insertMatch(tourneyMatch);
        Tourney t = getTourney(tourneyMatch.getEvent());
        if (t.getGame() > 50 && tourneyMatch.getPlayer1().getPlayerID() != 0 &&
                tourneyMatch.getPlayer2().getPlayerID() != 0 &&
                tourneyMatch.getPlayer1().getPlayerID() < tourneyMatch.getPlayer2().getPlayerID()) {
            this.tbStorer.createTournamentSet(t.getGame(), tourneyMatch.getPlayer1().getPlayerID(), tourneyMatch.getPlayer2().getPlayerID(), 
                                                t.getInitialTime(), t.getEventID());
        }        
    }


    /** update a group of matches and then check if round needs to be updated
     *  right now only called from admin management screen
     */
    public synchronized void updateMatches(List tourneyMatches, Tourney t) throws Throwable {

        log4j.debug("updateMatches()");
        if (tourneyMatches != null) {
            for (Iterator it = tourneyMatches.iterator(); it.hasNext();) {
                TourneyMatch m = (TourneyMatch) it.next();
                updateMatchOnly(m);
            }
        }
        checkRoundStatus(t);
    }

    /** update a single match and then check if round needs to be updated
     *  right now only called from servertable
     */
    public synchronized void updateMatch(
        TourneyMatch tourneyMatch) throws Throwable {

        log4j.debug("updateMatch(" + tourneyMatch.getMatchID() + ")");

        updateMatchOnly(tourneyMatch);
        
        Tourney t = getTourney(tourneyMatch.getEvent());
        checkRoundStatus(t);
    }
    
    private void updateMatchOnly(TourneyMatch tourneyMatch) throws Throwable {
        log4j.debug("updateMatchOnly(" + tourneyMatch.getMatchID() + ")");
        backingStorer.updateMatch(tourneyMatch);

        Tourney t = getTourney(tourneyMatch.getEvent());
        TourneySection s = t.getRound(tourneyMatch.getRound()).getSection(tourneyMatch.getSection());        

        // reinit section to show these results, and do anything else needed
        s.init();
        
        // don't really like this here, but haven't figured out where else to
        // do it
        
        // how do we tell tbstorer to create set?
        if (!tourneyMatch.isBye() && t.getFormat() instanceof SingleEliminationFormat) {
            log4j.debug("single elimination match, see if we need to create more matches because of tie");
            SingleEliminationSection s2 = (SingleEliminationSection) s;
            SingleEliminationFormat f = (SingleEliminationFormat)
                t.getFormat();            
            // if players have tied, need to create new matches for players
            // in this section
            SingleEliminationMatch m = s2.getSingleEliminationMatch(tourneyMatch);
            log4j.debug("get result of matches = " + m.getResult());
            if (m.getResult() == TourneyMatch.RESULT_TIE) {
                TourneyMatch more[] = f.createMoreMatchesAfterTie(tourneyMatch);
                insertMatch(more[0]);
                insertMatch(more[1]);
                s.addMatch(more[0]);
                s.addMatch(more[1]);
            }
        }
    }

    private void checkRoundStatus(Tourney t) throws Throwable {

        if (t.isComplete()) {
            completeTourney(t);
            
        }
        else if (t.getLastRound().isComplete()) {
            TourneyRound newRound = t.createNextRound();
            insertRound(newRound);
        }
    }
}
