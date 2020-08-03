package org.pente.gameServer.tourney;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.core.DSGPlayerStorer;

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
    public TourneyRound createNextRound(Tourney tourney, DSGPlayerStorer dsgPlayerStorer) {
        
        int round = tourney.getNumRounds() + 1;
        TourneyRound lastRound = tourney.getLastRound();
        List<TourneyPlayerData> players = new ArrayList<TourneyPlayerData>();
        
        // add all winners to next round
        for (Iterator it = lastRound.getSections().iterator(); it.hasNext();) {
            TourneySection s = (TourneySection) it.next();
            players.addAll(s.getWinners());
        }

        if (tourney.getFormat() instanceof RoundRobinFormat) {
            // now sort those winners by rating for placement in sections
            Collections.sort(players, new Comparator<TourneyPlayerData>() {
                public int compare(TourneyPlayerData o1, TourneyPlayerData o2) {
                    DSGPlayerData p1 = null, p2 = null;
                    try {
                        p1 = dsgPlayerStorer.loadPlayer(o1.getPlayerID());
                        p2 = dsgPlayerStorer.loadPlayer(o2.getPlayerID());
                    } catch (DSGPlayerStoreException e) {
                        e.printStackTrace();
                    }
                    double p1rating = p1.getPlayerGameData(tourney.getGame()).getRating();
                    double p2rating = p2.getPlayerGameData(tourney.getGame()).getRating();
                    if (p2rating > p1rating) {
                        return 1;
                    } else if (p2rating < p1rating) {
                        return -1;
                    } else {
                        return 0;
                    }
//                return p2rating - p1rating;
//                return p1.getSeed() - p2.getSeed();
                }
            });
        } else {
            // now sort those winners by seeds for placement in sections
            Collections.sort(players, new Comparator<TourneyPlayerData>() {
                public int compare(TourneyPlayerData o1, TourneyPlayerData o2) {
                    TourneyPlayerData p1 = (TourneyPlayerData) o1;
                    TourneyPlayerData p2 = (TourneyPlayerData) o2;
                    return p1.getSeed() - p2.getSeed();
                }
            });
        }
        
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
