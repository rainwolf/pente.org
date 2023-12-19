package org.pente.gameServer.tourney;

import java.util.*;

public class RoundRobinFormat extends AbstractTourneyFormat {

    private static final int MAX_PLAYERS_IN_SECTION = 4;

    public String getName() {
        return "Round-Robin";
    }

    public TourneyRound createFirstRound(List<TourneyPlayerData> players, Tourney tourney) {
        return createRound(players, tourney, 1);
    }
    // createNextRound implements in AbstractTourneyFormat

    /**
     * Expects the players to have been seeded already
     * and expects them to be sorted accordingly
     *
     * @param players List of TourneyPlayerData
     * @return TourneyRound
     */
    TourneyRound createRound(List<TourneyPlayerData> players, Tourney tourney, int rnd) {
        TourneyRound round = new TourneyRound(rnd);
        round.setTourney(tourney);

        int maxPlayersInSection = MAX_PLAYERS_IN_SECTION;
        int numSections = 0;
        if (players.size() <= 6) {
            numSections = 1;
            maxPlayersInSection = 6;
        } else {
            // number of sections depends on # players and max section size of 5
            numSections = (players.size() + MAX_PLAYERS_IN_SECTION - 1) /
                    MAX_PLAYERS_IN_SECTION;
        }

        // initially place player data in appropriate section
        @SuppressWarnings("unchecked")
        List<TourneyPlayerData>[] sections = new List[numSections];

        // create new sections for round
        for (int i = 0; i < numSections; i++) {
            round.addSection(new RoundRobinSection(i + 1));
            sections[i] = new ArrayList<>();
        }

//        // place players into sections according to seeding and down/back alg.
//        int currentPlayer = 0;        
//        outer: for (int i = 0; i < maxPlayersInSection; i++) {
//            // if we are down to the last players it might not come out evenly
//            // across all sections, so if we were supposed to place players
//            // from the bottom up, instead place from top down
//            boolean lastRun = currentPlayer + numSections > players.size();
//            for (int j = 0; j < numSections; j++) {
//                int placement = 0;
//                if (i % 2 == 0) {
//                    placement = j;
//                } else if (lastRun) {
//                    placement = players.size() - currentPlayer - 1;
//                } else {
//                    placement = numSections - 1 - j;
//                }
//                TourneyPlayerData p = (TourneyPlayerData) players.get(currentPlayer++);
//                sections[placement].add(p);
//                if (currentPlayer == players.size()) break outer;
//            }
//        }

//        shuffle the layers of the sections to let luck help prevent repeated match ups.
        if (numSections > 1) {
            for (int i = 0; i < maxPlayersInSection; i++) {
                int ub = Math.min(i * numSections + numSections, players.size());
                List<TourneyPlayerData> list = players.subList(i * numSections, ub);
                Collections.shuffle(list);
                for (int j = 0; j < list.size(); j++) {
                    sections[j].add(list.get(j));
                }
            }
        } else {
            sections[0].addAll(players);
        }

        // now for each section, create matches
        for (int i = 0; i < numSections; i++) {
            for (int j = 0; j < sections[i].size(); j++) {
                TourneyPlayerData p1 = sections[i].get(j);
                for (int k = 0; k < sections[i].size(); k++) {
                    if (j == k) continue;
                    TourneyPlayerData p2 = sections[i].get(k);
                    TourneyMatch m = new TourneyMatch();
                    m.setPlayer1(p1);
                    m.setPlayer2(p2);
                    m.setEvent(tourney.getEventID());
                    m.setRound(rnd);
                    m.setSection(i + 1);
                    m.setSeq(1);
                    round.getSection(i + 1).addMatch(m);
                }
            }
        }

        round.init();

        return round;
    }
}
