package org.pente.gameServer.tourney;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;


public abstract class AbstractTourneyFormat implements TourneyFormat {

    abstract TourneyRound createRound(List<TourneyPlayerData> players, Tourney tourney, int round);
    
    /** Creates the next round of play
     *  for a tournament based on last round results
     */
    public TourneyRound createNextRound(Tourney tourney) {
        
        int round = tourney.getNumRounds() + 1;
        TourneyRound lastRound = tourney.getLastRound();
        List<TourneyPlayerData> players = new ArrayList<TourneyPlayerData>();
        
        // add all winners to next round
        for (Iterator it = lastRound.getSections().iterator(); it.hasNext();) {
            TourneySection s = (TourneySection) it.next();
            players.addAll(s.getWinners());
        }
        // now sort those winners by seeds for placement in sections
        Collections.sort(players, new Comparator<TourneyPlayerData>() {
            public int compare(TourneyPlayerData o1, TourneyPlayerData o2) {
                TourneyPlayerData p1 = (TourneyPlayerData) o1;
                TourneyPlayerData p2 = (TourneyPlayerData) o2;
                return p1.getSeed() - p2.getSeed();
            }
        });
        
        // run standard round creation code
        return createRound(players, tourney, round);
    }
    

    public boolean isTourneyComplete(Tourney tourney) {
        if (tourney.getEndDate() != null) return true;
        
        if (tourney.getNumRounds() == 0) return false;
        for (Iterator rounds = tourney.getRounds().iterator(); rounds.hasNext();) {
            TourneyRound r = (TourneyRound) rounds.next();
            if (!r.isComplete()) return false;
        }
        
        return tourney.getLastRound().getWinners().size() == 1;
    }
}
