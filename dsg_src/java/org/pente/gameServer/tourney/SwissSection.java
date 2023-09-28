package org.pente.gameServer.tourney;

import java.util.*;

import org.apache.log4j.*;

public class SwissSection extends TourneySection {

    private static final Category log4j = Category.getInstance(
            SwissSection.class.getName());

    List players = new ArrayList();
    List matches = new ArrayList();
    List swissMatches = new ArrayList();


    public SwissSection(int section) {
        super(section);
    }

    // custom swiss methods
    void updatePlayerData(Tourney tourney) {
        // reset player data
        for (Iterator it = tourney.getRound(1).getPlayers().iterator(); it.hasNext(); ) {
            TourneyPlayerData p = (TourneyPlayerData) it.next();
            p.reset();
            p.setRandom();
        }

        // update match wins for all players
        for (Iterator it = tourney.getRounds().iterator(); it.hasNext(); ) {
            TourneyRound r = (TourneyRound) it.next();
            SwissSection s = (SwissSection) r.getSection(1);
            for (Iterator it2 = s.getSwissMatches().iterator(); it2.hasNext(); ) {
                SingleEliminationMatch m = (SingleEliminationMatch) it2.next();
                m.getPlayer1().incrementMatchWins(m.getPlayer1Wins());
                m.getPlayer1().incrementMatchLosses(m.getPlayer2Wins());
                if (!m.isBye()) {
                    m.getPlayer2().incrementMatchWins(m.getPlayer2Wins());
                    m.getPlayer2().incrementMatchLosses(m.getPlayer1Wins());
                }
                if (m.isForfeit()) {
                    log4j.debug("isforfeit");
                    if (m.getPlayer1Wins() == 0) {
                        log4j.debug("increment forfeits for " + m.getPlayer1().getName());
                        m.getPlayer1().incrementForfeits();
                    }
                    if (!m.isBye() && m.getPlayer2Wins() == 0) {
                        log4j.debug("increment forfeits for " + m.getPlayer2().getName());
                        m.getPlayer2().incrementForfeits();
                    }
                }
            }
        }

        // update opponent wins for all players
        for (Iterator it = tourney.getRounds().iterator(); it.hasNext(); ) {
            TourneyRound r = (TourneyRound) it.next();
            SwissSection s = (SwissSection) r.getSection(1);
            for (Iterator it2 = s.getSwissMatches().iterator(); it2.hasNext(); ) {
                SingleEliminationMatch m = (SingleEliminationMatch) it2.next();
                // byes increment 2*round num per win
                if (m.isBye()) {
                    m.getPlayer1().incrementByes();
                    //TODO, diff kinds of byes
                    //if (m.getResult() == 1) {
                    m.getPlayer1().incrementOpponentWins(
                            2 * tourney.getNumRounds() - 2);
                    //}
                } else {
                    m.getPlayer1().incrementOpponentWins(
                            m.getPlayer1Wins() *
                                    (m.getPlayer2().getMatchWins() - m.getPlayer2Wins()));
                    m.getPlayer2().incrementOpponentWins(
                            m.getPlayer2Wins() *
                                    (m.getPlayer1().getMatchWins() - m.getPlayer1Wins()));
                }
            }
        }
    }

    /**
     * returns players in correct order of ranking in tournament
     * by 1. wins
     * 2. opponent wins
     * 3. random number
     */
    public List getPlayersRanked(Tourney tourney) {

        updatePlayerData(tourney);

        List<TourneyPlayerData> ranked = new ArrayList<TourneyPlayerData>(players);

        // remove players from tourney with 2 forfeits
        // or players who were dropped
        for (Iterator<TourneyPlayerData> it = ranked.iterator(); it.hasNext(); ) {
            TourneyPlayerData p = it.next();
            if (p.getNumForfeits() == 2) {
                log4j.debug("2 forfeits, remove " + p.getName());
                it.remove();
            } else if (dropoutPlayers.contains(p.getPlayerID())) {
                log4j.debug("player dropped out, remove " + p.getName());
                it.remove();
            }
        }
        Collections.sort(ranked, (p1, p2) -> {
            int diffWins = p2.getMatchWins() - p1.getMatchWins();
            int diffOppWins = p2.getOpponentWins() - p1.getOpponentWins();
            if (diffWins != 0) {
                return diffWins;
            } else if (diffOppWins != 0) {
                return diffOppWins;
            } else {
                if (p2.getRandom() > p1.getRandom()) return 1;
                else return -1;
            }
        });

        return ranked;
    }

    public List getPlayers() {
        return players;
    }

    public int getNumPlayers() {
        return players.size();
    }

    // keep matches sorted by p1,p2, sorting done in init
    public void addMatch(TourneyMatch match) {
        matches.add(match);
    }

    public List getMatches() {
        return matches;
    }

    public List getSwissMatches() {
        return swissMatches;
    }


    public void init() {

        // sort matches by player names using p1+p2 (to group games in a match together)
        Collections.sort(matches, (o1, o2) -> {
            TourneyMatch m1 = (TourneyMatch) o1;
            TourneyMatch m2 = (TourneyMatch) o2;
            if (m1.isBye()) return 1;
            else if (m2.isBye()) return -1;

            String m1Names = "";
            String m2Names = "";
            String m1p1 = m1.getPlayer1().getName();
            String m1p2 = m1.getPlayer2().getName();
            String m2p1 = m2.getPlayer1().getName();
            String m2p2 = m2.getPlayer2().getName();
            if (m1p1.compareTo(m1p2) < 0) {
                m1Names = m1p1 + m1p2;
            } else {
                m1Names = m1p2 + m1p1;
            }
            if (m2p1.compareTo(m2p2) < 0) {
                m2Names = m2p1 + m2p2;
            } else {
                m2Names = m2p2 + m2p1;
            }
            return m1Names.compareTo(m2Names);
        });

        // seems kind of wasteful to re-run all of this every time a game completes
        swissMatches = new ArrayList();

        //look through matches, determine winners, counts if match complete
        SingleEliminationMatch currentMatch = null;
        for (int i = 0; i < matches.size(); i++) {
            TourneyMatch m = (TourneyMatch) matches.get(i);

            if (m.isBye()) {
                if (currentMatch != null) {
                    currentMatch.updateResult();
                }
                currentMatch = new SingleEliminationMatch(true);
                currentMatch.setPlayer1(m.getPlayer1());
                currentMatch.incrementPlayer1Wins(4);
                swissMatches.add(currentMatch);
                currentMatch = null;
            }
            // see if we need to create a new match
            else if (currentMatch == null || !currentMatch.samePlayers(m)) {

                // if the current match doesn't already have a result
                // (would have a result if not finished or double-forfeit)
                // then assign a winner based on whoever won more games
                // this assumes that player has 2 more wins, should work out
                if (currentMatch != null) {
                    currentMatch.updateResult();
                }

                currentMatch = new SingleEliminationMatch(false);
                // assign seats for match based on seed, lowest seed=p1
                if (m.getPlayer1().getSeed() < m.getPlayer2().getSeed()) {
                    currentMatch.setPlayer1(m.getPlayer1());
                    currentMatch.setPlayer2(m.getPlayer2());
                } else {
                    currentMatch.setPlayer1(m.getPlayer2());
                    currentMatch.setPlayer2(m.getPlayer1());
                }

                swissMatches.add(currentMatch);
            }

            // if any game in the match is a forfeit
            if (m.isForfeit()) {
                currentMatch.setForfeit(true);
            }

            if (m.isBye()) {
                continue;
            }

            // if we find a double-forfeit, that is the result for the 
            // whole match, just skip the rest of the results for these players
            if (currentMatch.getResult() == TourneyMatch.RESULT_DBL_FORFEIT ||
                    m.getResult() == TourneyMatch.RESULT_DBL_FORFEIT) {
                currentMatch.setResult(TourneyMatch.RESULT_DBL_FORFEIT);
            } else if (m.getResult() == TourneyMatch.RESULT_UNFINISHED) {
                currentMatch.setResult(TourneyMatch.RESULT_UNFINISHED);
            }
            // determine who gets the win and update it
            else if (m.getResult() == 1) {
                if (m.getPlayer1().getSeed() < m.getPlayer2().getSeed()) {
                    currentMatch.incrementPlayer1Wins();
                } else {
                    currentMatch.incrementPlayer2Wins();
                }
            } else if (m.getResult() == 2) {
                if (m.getPlayer2().getSeed() < m.getPlayer1().getSeed()) {
                    currentMatch.incrementPlayer1Wins();
                } else {
                    currentMatch.incrementPlayer2Wins();
                }
            }
        }
        if (currentMatch != null) {
            currentMatch.updateResult();
        }

        players.clear();
        for (Iterator it = swissMatches.iterator(); it.hasNext(); ) {
            SingleEliminationMatch m = (SingleEliminationMatch) it.next();
            players.add(m.getPlayer1());
            if (!m.isBye()) players.add(m.getPlayer2());
        }
    }


    public List getWinners() {
        // all players play all rounds
        // round will have to determine if highest player is actual winner
        return getPlayersRanked(this.round.getTourney());
    }


    public void updateAlreadyPlayed(int[][] alreadyPlayed) {
        for (int i = 0; i < matches.size(); i += 4) {
            TourneyMatch m = (TourneyMatch) matches.get(i);
            if (m.isBye()) continue;
            alreadyPlayed[m.getPlayer1().getSeed()][m.getPlayer2().getSeed()]++;
            alreadyPlayed[m.getPlayer2().getSeed()][m.getPlayer1().getSeed()]++;
        }
    }
}
