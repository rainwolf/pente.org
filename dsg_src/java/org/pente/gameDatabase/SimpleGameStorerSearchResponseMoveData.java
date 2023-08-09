/**
 * SimpleGameStorerSearchResponseMoveData.java
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

public class SimpleGameStorerSearchResponseMoveData implements GameStorerSearchResponseMoveData {

    private int move;
    private int rotation;
    private int games;
    private int wins;

    public void setMove(int move) {
        this.move = move;
    }

    public int getMove() {
        return move;
    }

    public void setGames(int games) {
        this.games = games;
    }

    public int getGames() {
        return games;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getWins() {
        return wins;
    }

    public double getPercentage() {
        return ((double) wins) / ((double) games);
    }

    public Object clone(GameStorerSearchResponseMoveData toFill) {

        toFill.setMove(getMove());
        toFill.setGames(getGames());
        toFill.setWins(getWins());

        return toFill;
    }
}