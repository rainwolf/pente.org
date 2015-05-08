/** SynchronizedGameStorerSearchResponseMoveData.java
 *  Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, you can find it online at
 *  http://www.gnu.org/copyleft/gpl.txt
 */


package org.pente.gameDatabase;

public class SynchronizedGameStorerSearchResponseMoveData extends SimpleGameStorerSearchResponseMoveData {

    public synchronized void setMove(int move) {
        super.setMove(move);
    }
    public synchronized int getMove() {
        return super.getMove();
    }

    public synchronized void setGames(int games) {
        super.setGames(games);
    }
    public synchronized int getGames() {
        return super.getGames();
    }

    public synchronized void setWins(int wins) {
        super.setWins(wins);
    }
    public synchronized int getWins() {
        return super.getWins();
    }

    public synchronized double getPercentage() {
        return super.getPercentage();
    }

    public synchronized Object clone() {

        GameStorerSearchResponseMoveData moveData = new SynchronizedGameStorerSearchResponseMoveData();

        moveData.setMove(super.getMove());
        moveData.setGames(super.getGames());
        moveData.setWins(super.getWins());

        return moveData;
    }
}