package org.pente.gameServer.tourney.test;

import java.util.*;

import org.apache.log4j.*;
import org.pente.gameServer.tourney.*;

public class DoubleEliminationDriver {

    private static Category log4j = Category.getInstance(
            DoubleEliminationDriver.class.getName());

    public static void main(String[] args) throws Throwable {

        BasicConfigurator.configure();

        CacheTourneyStorer tourneyStorer = new CacheTourneyStorer(
                new DummyTourneyStorer());

        for (int k = 0; k < Integer.parseInt(args[0]); k++) {

            Tourney t = new Tourney();
            t.setEventID(1);
            t.setFormat(new DoubleEliminationFormat());

            tourneyStorer.insertTourney(t);
            List players = createPlayers(4);
            TourneyRound r = t.createFirstRound(players);
            List<String> matchups = r.getMatchStrings();
            for (String matchup : matchups) {
                log4j.info(matchup);
            }

            long startTime = System.currentTimeMillis();

            do {
                // complete all matches, using somewhat random determination
                for (Iterator it = r.getSections().iterator(); it.hasNext(); ) {
                    SingleEliminationSection s = (SingleEliminationSection) it.next();
                    for (int i = 0; i < s.getNumTotalMatches(); i++) {
                        TourneyMatch m = (TourneyMatch) s.getMatches().get(i);
                        if (m == null) {
                            boolean f = false;
                        }
                        if (m.getPlayer2() == null) continue;//skip bye
                        double rDiff = m.getPlayer1().getRating() - m.getPlayer2().getRating();
                        // for every 50 ratings points diff, add an extra 5% chance of winning
                        double rMult = rDiff / 50 * .05;
                        if (rMult > 1) rMult = 1;
                        else if (rMult < -1) rMult = -1;
                        // bigger ratings diff, means less chance for p2 to win
                        double rand = Math.random();
                        int result = (int) (rand - rMult + 1.5);
                        if (result < 1) {
                            result = 1;
                        } else if (result > 2) {
                            result = 2;
                        }
                        m.setResult(result);
                        s.init();

                        // in case of tie, add
                        SingleEliminationMatch m2 = s.getSingleEliminationMatch(m);
                        if (m2.getResult() == TourneyMatch.RESULT_TIE) {
                            DoubleEliminationFormat f = (DoubleEliminationFormat)
                                    t.getFormat();
                            TourneyMatch more[] = f.createMoreMatchesAfterTie(m);
                            s.addMatch(more[0]);
                            s.addMatch(more[1]);
                        }
                    }
                }

                log4j.info("round " + t.getNumRounds() + " complete");
                matchups = r.getMatchStrings();
                for (Iterator strs = matchups.iterator(); strs.hasNext(); ) {
                    log4j.info((String) strs.next());
                }

                if (t.isComplete()) break;

                log4j.info("create round " + (t.getNumRounds() + 1));
                r = t.createNextRound();
                matchups = r.getMatchStrings();
                for (Iterator strs = matchups.iterator(); strs.hasNext(); ) {
                    log4j.info((String) strs.next());
                }

            } while (true);

            log4j.info("tourney complete, winner=" + t.getWinner());
            log4j.info("total time = " + (System.currentTimeMillis() - startTime));
            DoubleEliminationFormat f = (DoubleEliminationFormat)
                    t.getFormat();
            log4j.info("called getSection " + f.getSectionCount + " times.");
        }
    }


    private static List createPlayers(int numPlayers) {
        List players = new ArrayList(numPlayers);
        for (int i = 0; i < numPlayers; i++) {
            TourneyPlayerData p = new TourneyPlayerData();
            p.setName("player" + i);
            p.setPlayerID(1L + i);
            int rand = (int) (Math.random() * 200);
            p.setRating(1200 + i * 20 + rand);
            p.setTotalGames(21);
            players.add(p);
        }
        Collections.sort(players, new Comparator() {
            public int compare(Object o1, Object o2) {
                TourneyPlayerData p1 = (TourneyPlayerData) o1;
                TourneyPlayerData p2 = (TourneyPlayerData) o2;
                return p2.getRating() - p1.getRating();
            }
        });
        for (int i = 0; i < numPlayers; i++) {
            TourneyPlayerData p = (TourneyPlayerData) players.get(i);
            p.setSeed(i + 1);
            p.setName("p" + p.getSeed());
        }
        return players;
    }
}
