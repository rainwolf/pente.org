/**
 * DSGPlayerGameData.java
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

package org.pente.gameServer.core;

import java.util.*;

public interface DSGPlayerGameData extends Cloneable {

    public void setPlayerID(long pid);

    public long getPlayerID();

    public void setGame(int game);

    public int getGame();

    public void setWins(int wins);

    public int getWins();

    public void setLosses(int losses);

    public int getLosses();

    public void setDraws(int draws);

    public int getDraws();

    public int getTotalGames();

    public double getPercentageWins();

    public void setRating(double rating);

    public double getRating();

    public String getRatingGif();

//    public void setRatingFloor(int ratingFloor);
//    public int getRatingFloor();

    public void setStreak(int streak);

    public int getStreak();

    public void setLastGameDate(Date lastGameDate);

    public Date getLastGameDate();

    public static final char YES = 'Y';
    public static final char NO = 'N';

    public void setComputer(char computer);

    public char getComputer();

    public boolean isComputerScore();

    public boolean isHumanScore();

    public static final int WIN = 1;
    public static final int LOSS = 2;
    public static final int DRAW = 3;

    public void gameOver(int result, DSGPlayerGameData opponentPlayerGameData,
                         double k);

    public boolean isProvisional();

    public static final int TOURNEY_WINNER_NONE = 0;
    public static final int TOURNEY_WINNER_GOLD = 1;
    public static final int TOURNEY_WINNER_SILVER = 2;
    public static final int TOURNEY_WINNER_BRONZE = 3;
    public static final int KINGOFTHEHILL_WINNER = 4;

    public void setTourneyWinner(int winner);

    public int getTourneyWinner();

    public boolean isEqual(DSGPlayerGameData data);

    public DSGPlayerGameData getCopy();

    public Object clone();
}



































