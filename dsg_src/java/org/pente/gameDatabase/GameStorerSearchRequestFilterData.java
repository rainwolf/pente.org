/**
 * GameStorerSearchRequestFilterData.java
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

public interface GameStorerSearchRequestFilterData extends Cloneable {

    public void setStartGameNum(int num);

    public int getStartGameNum();

    public void setEndGameNum(int num);

    public int getEndGameNum();

    public int getNumGames();

    public void setTotalGameNum(int num);

    public int getTotalGameNum();


    public void setPlayer1Name(String name);

    public String getPlayer1Name();

    public void setPlayer2Name(String name);

    public String getPlayer2Name();

    public static int SEAT_ALL = 0;
    public static int SEAT_1 = 1;
    public static int SEAT_2 = 2;

    public void setPlayer1Seat(int seat);

    public int getPlayer1Seat();

    public void setPlayer2Seat(int seat);

    public int getPlayer2Seat();

    public void setDb(int dbid);

    public int getDb();

    public void setGame(int gameNum);

    public int getGame();

    public void setSite(String site);

    public String getSite();

    public void setEvent(String event);

    public String getEvent();

    public void setRound(String round);

    public String getRound();

    public void setSection(String section);

    public String getSection();


    public void setAfterDate(Date date);

    public Date getAfterDate();

    public void setBeforeDate(Date date);

    public Date getBeforeDate();

    public void setWinner(int winner);

    public int getWinner();

    public void setGetNextMoves(boolean nextMoves);

    public boolean doGetNextMoves();

    public int getRatingP1Above();

    public void setRatingP1Above(int ratingP1Above);

    public int getRatingP2Above();

    public void setRatingP2Above(int ratingP2Above);

    public boolean isExcludeTimeOuts();

    public void setExcludeTimeOuts(boolean excludeTimeOuts);

    public boolean isP1OrP2();

    public void setP1OrP2(boolean p1OrP2);

    public boolean isOnlyLive();

    public void setOnlyLive(boolean onlyLive);

    public boolean isOnlyTurnBased();

    public void setOnlyTurnBased(boolean onlyTurnBased);

    public Object clone();
}