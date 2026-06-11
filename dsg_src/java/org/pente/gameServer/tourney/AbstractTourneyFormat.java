package org.pente.gameServer.tourney;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.core.DSGPlayerStorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public abstract class AbstractTourneyFormat implements TourneyFormat {

    abstract TourneyRound createRound(List<TourneyPlayerData> players, Tourney tourney, int round);

    /**
     * Creates the next round of play
     * for a tournament based on last round results
     */
    public TourneyRound createNextRound(Tourney tourney, DSGPlayerStorer dsgPlayerStorer) {

        int round = tourney.getNumRounds() + 1;
        TourneyRound lastRound = tourney.getLastRound();
        List<TourneyPlayerData> players = new ArrayList<TourneyPlayerData>();

        // add all winners to next round
        for (TourneySection s : lastRound.getSections()) {
            players.addAll(s.getWinners());
        }

        if (tourney.getFormat() instanceof RoundRobinFormat) {
            // Pre-load ratings once, up front. The comparator must apply a single
            // consistent criterion to every pair: mixing rating-order for loaded
            // players with seed-order for failed loads breaks transitivity and
            // makes TimSort throw IllegalArgumentException. If any load fails we
            // fall back to seed order for the WHOLE sort.
            Map<Long, Double> ratings = new HashMap<>();
            boolean allLoaded = true;
            for (TourneyPlayerData p : players) {
                try {
                    ratings.put(p.getPlayerID(),
                            dsgPlayerStorer.loadPlayer(p.getPlayerID())
                                    .getPlayerGameData(tourney.getGame()).getRating());
                } catch (DSGPlayerStoreException e) {
                    e.printStackTrace();
                    allLoaded = false;
                    break;
                }
            }
            if (allLoaded) {
                // sort by rating, descending
                Collections.sort(players, (o1, o2) -> {
                    double p1rating = ratings.get(o1.getPlayerID());
                    double p2rating = ratings.get(o2.getPlayerID());
                    if (p2rating > p1rating) {
                        return 1;
                    } else if (p2rating < p1rating) {
                        return -1;
                    } else {
                        return 0;
                    }
                });
            } else {
                // couldn't load all ratings; use a single consistent fallback
                Collections.sort(players, (o1, o2) -> o1.getSeed() - o2.getSeed());
            }
        } else {
            // now sort those winners by seeds for placement in sections
            Collections.sort(players, (o1, o2) -> {
                TourneyPlayerData p1 = (TourneyPlayerData) o1;
                TourneyPlayerData p2 = (TourneyPlayerData) o2;
                return p1.getSeed() - p2.getSeed();
            });
        }

        // run standard round creation code
        return createRound(players, tourney, round);
    }


    public boolean isTourneyComplete(Tourney tourney) {
        if (tourney.getEndDate() != null) return true;

        if (tourney.getNumRounds() == 0) return false;
        for (TourneyRound r : tourney.getRounds()) {
            if (!r.isComplete()) return false;
        }

        return tourney.getLastRound().getWinners().size() == 1;
    }
}
