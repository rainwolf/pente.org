package org.pente.gameServer.tourney;

import java.util.*;

public class SingleEliminationSection extends TourneySection {

    List players = new ArrayList();
    List<TourneyMatch> matches = new ArrayList<TourneyMatch>();

    /**
     * single elimination matches group together the above matches
     * and provide an overall summary of the match, matches above should
     * really be called games.
     */
    private List singleEliminationMatches = null;

    public SingleEliminationSection(int section) {
        super(section);
    }

    public List getPlayers() {
        return players;
    }

    public int getNumPlayers() {
        return players.size();
    }

    // maintain sorted order of matches by
    // min(p1 seed, p2 seed), max(p1 seed, p2 seed), seq
    // bye always in last match
    // this way games vs. 2 players all together for easier winner determination
    public void addMatch(TourneyMatch match) {
        if (matches.isEmpty() || match.isBye()) {
            matches.add(match);
        } else {
            int min = match.getMinSeed();
            int max = match.getMaxSeed();
            int seq = match.getSeq();
            int insertAt = 0;
            for (insertAt = 0; insertAt < matches.size(); insertAt++) {
                TourneyMatch m = (TourneyMatch) matches.get(insertAt);
                if (m.isBye()) { //we've reached the end, insert before the bye
                    break;
                }
                if (min < m.getMinSeed()) {
                    break;
                } else if (min == m.getMinSeed()) {
                    if (max < m.getMaxSeed()) {
                        break;
                    } else if (max == m.getMaxSeed()) {
                        if (seq <= m.getSeq()) {
                            break;
                        }
                    }
                }
            }
            matches.add(insertAt, match);
        }
    }

    public List<TourneyMatch> getMatches() {
        return matches;
    }


    public List getSingleEliminationMatches() {
        return singleEliminationMatches;
    }

    public SingleEliminationMatch getSingleEliminationMatch(
            TourneyMatch lastMatchPlayed) {

        for (Iterator it = singleEliminationMatches.iterator(); it.hasNext(); ) {
            SingleEliminationMatch m = (SingleEliminationMatch) it.next();
            if (m.samePlayers(lastMatchPlayed)) {
                return m;
            }
        }
        return null;
    }


    public void init() {
        // seems kind of wasteful to re-run all of this every time a game completes
        singleEliminationMatches = new ArrayList();
        players.clear();

        //look through matches, determine winners, counts if match complete
        SingleEliminationMatch currentMatch = null;
        for (int i = 0; i < matches.size(); i++) {
            TourneyMatch m = (TourneyMatch) matches.get(i);

            // setup player list
            if (!players.contains(m.getPlayer1())) {
                players.add(m.getPlayer1());
            }
            if (!m.isBye()) {
                if (!players.contains(m.getPlayer2())) {
                    players.add(m.getPlayer2());
                }
            }

            // see if we need to create a new match
            if (currentMatch == null || m.isBye() || !currentMatch.samePlayers(m)) {

                // if the current match doesn't already have a result
                // (would have a result if not finished or double-forfeit)
                // then assign a winner based on whoever won more games
                // this assumes that player has 2 more wins, should work out
                if (currentMatch != null) {
                    currentMatch.updateResult();
                }
                if (m.isBye()) {
                    currentMatch = new SingleEliminationMatch(true);
                    currentMatch.setPlayer1(m.getPlayer1());
                } else {
                    currentMatch = new SingleEliminationMatch(false);
                    // assign seats for match based on seed, lowest seed=p1
                    if (m.getPlayer1().getSeed() < m.getPlayer2().getSeed()) {
                        currentMatch.setPlayer1(m.getPlayer1());
                        currentMatch.setPlayer2(m.getPlayer2());
                    } else {
                        currentMatch.setPlayer1(m.getPlayer2());
                        currentMatch.setPlayer2(m.getPlayer1());
                    }
                }


                singleEliminationMatches.add(currentMatch);
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
    }

    // depends on init having been run
    public List getWinners() {
        List winners = new ArrayList();
        for (Iterator it = singleEliminationMatches.iterator(); it.hasNext(); ) {
            SingleEliminationMatch m = (SingleEliminationMatch) it.next();
            if ((m.isBye() && !m.isForfeit()) || m.getResult() == 1) {
                winners.add(m.getPlayer1());
            } else if (m.getResult() == 2) {
                winners.add(m.getPlayer2());
            } else if (m.getResult() == TourneyMatch.RESULT_TIE) {
                winners.add(m.getPlayer1());
                winners.add(m.getPlayer2());
            }
        }

        return winners;
    }

    public void updateAlreadyPlayed(int alreadyPlayed[][]) {
        for (Iterator it = singleEliminationMatches.iterator(); it.hasNext(); ) {
            SingleEliminationMatch m = (SingleEliminationMatch) it.next();
            if (m.isBye()) continue;
            alreadyPlayed[m.getPlayer1().getSeed()][m.getPlayer2().getSeed()]++;
            alreadyPlayed[m.getPlayer2().getSeed()][m.getPlayer1().getSeed()]++;
        }
    }

    public int getNumTotalMatches() {
        return getSingleEliminationMatches().size();
    }

    public int getNumCompleteMatches() {
        int sz = 0;
        for (Iterator ms = getSingleEliminationMatches().iterator(); ms.hasNext(); ) {
            SingleEliminationMatch m = (SingleEliminationMatch) ms.next();
            if (m.isComplete()) {
                sz++;
            }
        }
        return sz;
    }
}
