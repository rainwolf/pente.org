package org.pente.gameServer.tourney;

import org.apache.log4j.*;

public class TourneyMatch {

    private static final Category log4j = Category.getInstance(
            TourneyMatch.class.getName());

    private long matchID;
    private int event;
    private int round;
    private int section;
    private int seq;
    private long gid;
    private TourneyPlayerData player1;
    private TourneyPlayerData player2;

    public static final int RESULT_UNFINISHED = 0;
    public static final int RESULT_P1_WINS = 1;
    public static final int RESULT_P2_WINS = 2;
    public static final int RESULT_DBL_FORFEIT = 3;
    public static final int RESULT_TIE = 4;

    //1,2 = player 1,2 wins
    //4 = dbl-forfeit
    private int result;
    private boolean forfeit;


    public long getGid() {
        return gid;
    }

    public void setGid(long gid) {
        this.gid = gid;
    }

    public TourneyPlayerData getPlayer1() {
        return player1;
    }

    public void setPlayer1(TourneyPlayerData player1) {
        this.player1 = player1;
    }

    public TourneyPlayerData getPlayer2() {
        return player2;
    }

    public void setPlayer2(TourneyPlayerData player2) {
        this.player2 = player2;
    }

    public boolean isBye() {
        return player2 == null;
    }

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }

    // considering a bye as having been played for now
    public boolean hasBeenPlayed() {
        return result != 0 || isBye();
    }

    public boolean isForfeit() {
        return forfeit;
    }

    public void setForfeit(boolean forfeit) {
        this.forfeit = forfeit;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int getSection() {
        return section;
    }

    public void setSection(int section) {
        this.section = section;
    }

    public int getEvent() {
        return event;
    }

    public void setEvent(int event) {
        this.event = event;
    }

    public long getMatchID() {
        return matchID;
    }

    public void setMatchID(long matchID) {
        this.matchID = matchID;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public int getMaxSeed() {
        return Math.max(player1.getSeed(), player2.getSeed());
    }

    public int getMinSeed() {
        return Math.min(player1.getSeed(), player2.getSeed());
    }

    public boolean samePlayers(TourneyMatch other) {
        if (other.getPlayer1().getPlayerID() == player1.getPlayerID() &&
                other.getPlayer2().getPlayerID() == player2.getPlayerID()) return true;
        if (other.getPlayer1().getPlayerID() == player2.getPlayerID() &&
                other.getPlayer2().getPlayerID() == player1.getPlayerID()) return true;
        return false;
    }

    public boolean equals(Object o) {
        if (!(o instanceof TourneyMatch)) return false;
        TourneyMatch m = (TourneyMatch) o;
        return m.matchID == matchID;
    }

    public int hashCode() {
        return (int) getMatchID();
    }
}
