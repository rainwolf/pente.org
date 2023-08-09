/**
 * SimpleGameStorerSearchRequestFilterData.java
 * Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, you can find it online at
 * http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameDatabase;

import java.util.*;

public class SimpleGameStorerSearchRequestFilterData implements GameStorerSearchRequestFilterData {

    private int startGameNum;
    private int endGameNum;
    private int totalGameNum;

    private String player1Name;
    private String player2Name;
    private int player1Seat = 1;
    private int player2Seat = 2;

    private int db = 2; // default for pente.org online db
    private int game;
    private String site;
    private String event;
    private String round;
    private String section;

    private Date afterDate;
    private Date beforeDate;

    private int winner;
    private boolean searchNextMoves = true;

    private int ratingP1Above;
    private int ratingP2Above;

    private boolean excludeTimeOuts;
    private boolean p1OrP2;
    private boolean onlyLive;
    private boolean onlyTurnBased;

    //private static Category cat = Category.getInstance(SimpleGameStorerSearchRequestFilterData.class.getName());


    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((afterDate == null) ? 0 : afterDate.hashCode());
        result = PRIME * result + ((beforeDate == null) ? 0 : beforeDate.hashCode());
        result = PRIME * result + db;
        result = PRIME * result + ((event == null) ? 0 : event.hashCode());
        result = PRIME * result + game;
        result = PRIME * result + ((player1Name == null) ? 0 : player1Name.hashCode());
        result = PRIME * result + player1Seat;
        result = PRIME * result + ((player2Name == null) ? 0 : player2Name.hashCode());
        result = PRIME * result + player2Seat;
        result = PRIME * result + ((round == null) ? 0 : round.hashCode());
        result = PRIME * result + ((section == null) ? 0 : section.hashCode());
        result = PRIME * result + ((site == null) ? 0 : site.hashCode());
        result = PRIME * result + winner;
        result = PRIME * result + ratingP1Above;
        result = PRIME * result + ratingP2Above;
        result = PRIME * result + (excludeTimeOuts ? 1 : 0);
        result = PRIME * result + (p1OrP2 ? 1 : 0);
        result = PRIME * result + (onlyLive ? 1 : 0);
        result = PRIME * result + (onlyTurnBased ? 1 : 0);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final SimpleGameStorerSearchRequestFilterData other = (SimpleGameStorerSearchRequestFilterData) obj;
        if (afterDate == null) {
            if (other.afterDate != null)
                return false;
        } else if (!afterDate.equals(other.afterDate))
            return false;
        if (beforeDate == null) {
            if (other.beforeDate != null)
                return false;
        } else if (!beforeDate.equals(other.beforeDate))
            return false;
        if (db != other.db)
            return false;
        if (event == null) {
            if (other.event != null)
                return false;
        } else if (!event.equals(other.event))
            return false;
        if (game != other.game)
            return false;
        if (player1Name == null) {
            if (other.player1Name != null)
                return false;
        } else if (!player1Name.equals(other.player1Name))
            return false;
        if (player1Seat != other.player1Seat)
            return false;
        if (player2Name == null) {
            if (other.player2Name != null)
                return false;
        } else if (!player2Name.equals(other.player2Name))
            return false;
        if (player2Seat != other.player2Seat)
            return false;
        if (round == null) {
            if (other.round != null)
                return false;
        } else if (!round.equals(other.round))
            return false;
        if (section == null) {
            if (other.section != null)
                return false;
        } else if (!section.equals(other.section))
            return false;
        if (site == null) {
            if (other.site != null)
                return false;
        } else if (!site.equals(other.site))
            return false;
        if (winner != other.winner)
            return false;
        if (ratingP1Above != other.ratingP1Above || ratingP2Above != other.ratingP2Above) {
            return false;
        }
        if (excludeTimeOuts != other.excludeTimeOuts) {
            return false;
        }
        if (p1OrP2 != other.p1OrP2) {
            return false;
        }
        if (onlyLive != other.onlyLive) {
            return false;
        }
        if (onlyTurnBased != other.onlyTurnBased) {
            return false;
        }
        return true;
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException c) {
        }
        return null;
    }

    public void setDb(int dbid) {
        db = dbid;
    }

    public int getDb() {
        return db;
    }

    public void setStartGameNum(int num) {
        this.startGameNum = num;
    }

    public int getStartGameNum() {
        return startGameNum;
    }

    public void setEndGameNum(int num) {
        this.endGameNum = num;
    }

    public int getEndGameNum() {
        return endGameNum;
    }

    public int getNumGames() {
        return endGameNum - startGameNum;
    }

    public void setTotalGameNum(int num) {
        this.totalGameNum = num;
    }

    public int getTotalGameNum() {
        return totalGameNum;
    }


    public void setPlayer1Name(String name) {
        this.player1Name = name;
    }

    public String getPlayer1Name() {
        return player1Name;
    }

    public void setPlayer2Name(String name) {
        this.player2Name = name;
    }

    public String getPlayer2Name() {
        return player2Name;
    }

    public void setPlayer1Seat(int seat) {
        this.player1Seat = seat;
    }

    public int getPlayer1Seat() {
        return player1Seat;
    }

    public void setPlayer2Seat(int seat) {
        this.player2Seat = seat;
    }

    public int getPlayer2Seat() {
        return player2Seat;
    }

    public void setGame(int gameNum) {
        this.game = gameNum;
    }

    public int getGame() {
        return game;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getSite() {
        return site;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getEvent() {
        return event;
    }

    public void setRound(String round) {
        this.round = round;
    }

    public String getRound() {
        return round;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getSection() {
        return section;
    }


    public void setAfterDate(Date date) {
        afterDate = date;
    }

    public Date getAfterDate() {
        return afterDate;
    }

    public void setBeforeDate(Date date) {
        beforeDate = date;
    }

    public Date getBeforeDate() {
        return beforeDate;
    }

    public void setWinner(int winner) {
        this.winner = winner;
    }

    public int getWinner() {
        return winner;
    }

    public void setGetNextMoves(boolean nextMoves) {
        this.searchNextMoves = nextMoves;
    }

    public boolean doGetNextMoves() {
        return searchNextMoves;
    }


    public int getRatingP1Above() {
        return ratingP1Above;
    }

    public void setRatingP1Above(int ratingP1Above) {
        this.ratingP1Above = ratingP1Above;
    }

    public int getRatingP2Above() {
        return ratingP2Above;
    }

    public void setRatingP2Above(int ratingP2Above) {
        this.ratingP2Above = ratingP2Above;
    }

    public boolean isExcludeTimeOuts() {
        return excludeTimeOuts;
    }

    public void setExcludeTimeOuts(boolean excludeTimeOuts) {
        this.excludeTimeOuts = excludeTimeOuts;
    }

    public boolean isP1OrP2() {
        return p1OrP2;
    }

    public void setP1OrP2(boolean p1OrP2) {
        this.p1OrP2 = p1OrP2;
    }

    public boolean isOnlyLive() {
        return onlyLive;
    }

    public void setOnlyLive(boolean onlyLive) {
        this.onlyLive = onlyLive;
    }

    public boolean isOnlyTurnBased() {
        return onlyTurnBased;
    }

    public void setOnlyTurnBased(boolean onlyTurnBased) {
        this.onlyTurnBased = onlyTurnBased;
    }

}