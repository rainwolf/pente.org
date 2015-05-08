package org.pente.gameServer.tourney;

/** not the same type as TourneyMatch
 *  this is used just to group matches together in a single-elimination match
 *  TourneyMatch really should be called TourneyGame...
 */
public class SingleEliminationMatch {
    private TourneyPlayerData player1;
    private TourneyPlayerData player2;
    private int player1Wins;
    private int player2Wins;
    
    private boolean forfeit;
    private int result = -1;
    private boolean bye;
    public SingleEliminationMatch(boolean bye) {
        this.bye = bye;
    }
    public boolean isBye() {
        return bye;
    }
    public TourneyPlayerData getPlayer1() {
        return player1;
    }
    public void setPlayer1(TourneyPlayerData player1) {
        this.player1 = player1;
    }
    public int getPlayer1Wins() {
        return player1Wins;
    }
    public void incrementPlayer1Wins() {
        this.player1Wins++;
    }
    public void incrementPlayer1Wins(int count) {
        this.player1Wins += count;
    }
    public TourneyPlayerData getPlayer2() {
        return player2;
    }
    public void setPlayer2(TourneyPlayerData player2) {
        this.player2 = player2;
    }
    public int getPlayer2Wins() {
        return player2Wins;
    }
    public void incrementPlayer2Wins() {
        this.player2Wins++;
    }
    public int getResult() {
        return result;
    }
    public boolean isForfeit() {
        return forfeit;
    }
    public void setForfeit(boolean forfeit) {
        this.forfeit = forfeit;
    }
    public boolean isComplete() {
    	if (isBye()) return true;
    	if (getResult() != TourneyMatch.RESULT_UNFINISHED &&
    		getResult() != TourneyMatch.RESULT_TIE) return true;
    	return false;
    }
    
    public void updateResult() {
        if (getResult() == -1) {
            // forfeits for byes leave result=0
            if (isForfeit() && isBye()) {
                setResult(0);
            }
            else if (getPlayer1Wins() > getPlayer2Wins()) {
                setResult(1);
            }
            else if(getPlayer2Wins() > getPlayer1Wins()) {
                setResult(2);
            }
            else {
                setResult(TourneyMatch.RESULT_TIE);
            }
        }
    }
    public void setResult(int result) {
        this.result = result;
    }
    public String getResultStr() {
        String result = null;
        if (isBye()) {
            result =  "bye";
            if (isForfeit()) {
                result += " (forfeit)";
            }
        }
        else if (getResult() == TourneyMatch.RESULT_UNFINISHED) {
            result =  "vs.";
        }
        else if (getResult() == 1) {
            result =  "defeats";
            if (isForfeit()) {
                result += " (forfeit)";
            }
        }
        else if (getResult() == 2) {
            result =  "loses to";
            if (isForfeit()) {
                result += " (forfeit)";
            }
        }
        else if (getResult() == TourneyMatch.RESULT_TIE) {
            result =  "tied with";
        }
        else if (getResult() == TourneyMatch.RESULT_DBL_FORFEIT) {
            result =  "double forfeit";
        }
        else {
            result =  "vs.";
        }

        return result;
    }
    public String getMatchStr() {
        if (!isBye()) {
            String r = getPlayer1().getName() + "(" + getPlayer1().getSeed() + ")" +
            " " + getResultStr() + " " + 
                      getPlayer2().getName() + "(" + getPlayer2().getSeed() + ")";
            if (getPlayer1Wins() > 0 || getPlayer2Wins() > 0) {
                r += "  " + getPlayer1Wins() + "-" + getPlayer2Wins();
            }
            return r;
        }
        else {
            return getPlayer1().getName() + "(" + getPlayer1().getSeed() + 
                   ") " + getResultStr();
        }
    }
    
    public boolean samePlayers(TourneyMatch other) {
        if (other.getPlayer1().getPlayerID() == player1.getPlayerID() &&
            other.getPlayer2().getPlayerID() == player2.getPlayerID()) return true;
        if (other.getPlayer1().getPlayerID() == player2.getPlayerID() &&
            other.getPlayer2().getPlayerID() == player1.getPlayerID()) return true;
        return false;
    }
}