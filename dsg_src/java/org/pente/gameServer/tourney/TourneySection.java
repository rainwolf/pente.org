package org.pente.gameServer.tourney;

import java.util.*;

import org.apache.log4j.*;

import org.pente.turnBased.*;

public abstract class TourneySection {

    private static final Category log4j = Category.getInstance(
            TourneyMatch.class.getName());

    private int section;
    List<Long> dropoutPlayers = new ArrayList<Long>();
    TourneyRound round;

    public TourneySection(int section) {
        this.section = section;
    }

    public void setTourneyRound(TourneyRound round) {
        this.round = round;
    }

    public int getSection() {
        return section;
    }

    public void setSection(int section) {
        this.section = section;
    }

    public abstract int getNumPlayers();

    public abstract List<TourneyPlayerData> getPlayers();

    public int getNumTotalMatches() {
        return getMatches().size();
    }

    public int getNumCompleteMatches() {
        int sz = 0;
        for (Iterator ms = getMatches().iterator(); ms.hasNext(); ) {
            TourneyMatch m = (TourneyMatch) ms.next();
            if (m.hasBeenPlayed()) {
                sz++;
            }
        }
        return sz;
    }

    public boolean isEmpty() {
        return getMatches().isEmpty();
    }

    public boolean isComplete() {
        if (isEmpty()) return false;
        for (Iterator matches = getMatches().iterator(); matches.hasNext(); ) {
            TourneyMatch m = (TourneyMatch) matches.next();
            if (!m.hasBeenPlayed()) {
                return false;
            }
        }
        return true;
    }

    public abstract void addMatch(TourneyMatch match);

    public abstract List<TourneyMatch> getMatches();

    private List<TBSet> sets = new ArrayList<TBSet>();

    public List<TBSet> getSets() {
        return sets;
    }

    public void addSet(TBSet set) {
        sets.add(set);
    }

    public TourneyMatch getUnplayedMatch(long player1ID, long player2ID) {
        log4j.debug("section.getUnplayedMatch()");
        for (Iterator it = getMatches().iterator(); it.hasNext(); ) {
            TourneyMatch m = (TourneyMatch) it.next();
            log4j.debug("examine match " + m.getMatchID());
            if (!m.hasBeenPlayed() &&
                    m.getPlayer1().getPlayerID() == player1ID &&
                    m.getPlayer2().getPlayerID() == player2ID) {
                return m;
            }
        }
        return null;
    }

    public abstract void init();

    public abstract List<TourneyPlayerData> getWinners();

    /**
     * return list of affected matches
     */
    public List<TourneyMatch> forfeitPlayers(long pids[], boolean dropout[]) {
        List<TourneyMatch> updateMatches = new ArrayList<>();
        for (Iterator<TourneyMatch> it = getMatches().iterator(); it.hasNext(); ) {
            TourneyMatch m = it.next();
            // don't update already completed matches
            if (m.getResult() != TourneyMatch.RESULT_UNFINISHED) continue;

            if (m.isBye()) {
                if (contains(pids, m.getPlayer1().getPlayerID())) {
                    m.setForfeit(true);
                    updateMatches.add(m);
                }
            } else if (contains(pids, m.getPlayer1().getPlayerID())) {
                if (contains(pids, m.getPlayer2().getPlayerID())) {
                    //dbl-forfeit
                    m.setForfeit(true);
                    m.setResult(TourneyMatch.RESULT_DBL_FORFEIT);
                } else {
                    m.setResult(2);
                    m.setForfeit(true);
                }
                updateMatches.add(m);
            } else if (contains(pids, m.getPlayer2().getPlayerID())) {
                m.setResult(1);
                m.setForfeit(true);
                updateMatches.add(m);
            }
        }

        // save list of players who will dropout
        for (int i = 0; i < pids.length; i++) {
            if (dropout[i]) {
                dropoutPlayers.add(pids[i]);
            }
        }

        // init will be called later
        return updateMatches;
    }

    public boolean contains(long pids[], long pid) {
        for (int i = 0; i < pids.length; i++) {
            if (pids[i] == pid) return true;
        }
        return false;
    }

    public abstract void updateAlreadyPlayed(int alreadyPlayed[][]);
}
