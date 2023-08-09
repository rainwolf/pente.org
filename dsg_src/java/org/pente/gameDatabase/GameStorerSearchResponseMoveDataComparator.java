/**
 * GameStorerSearchResponseMoveDataComparator.java
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

import java.util.Comparator;

import org.pente.game.*;

public class GameStorerSearchResponseMoveDataComparator implements Comparator {

    public static final int SORT_POSITION = 0;
    public static final int SORT_GAMES = 1;
    public static final int SORT_PERCENTAGE = 2;

    private int sortField;

    public GameStorerSearchResponseMoveDataComparator(int sortField) {
        this.sortField = sortField;
    }

    public int compare(Object obj1, Object obj2) {

        if (!(obj1 instanceof GameStorerSearchResponseMoveData) ||
                !(obj2 instanceof GameStorerSearchResponseMoveData)) {
            throw new IllegalArgumentException("Invalid objects");
        }

        int compareResult = 0;
        GameStorerSearchResponseMoveData moveData1 = (GameStorerSearchResponseMoveData) obj1;
        GameStorerSearchResponseMoveData moveData2 = (GameStorerSearchResponseMoveData) obj2;

        switch (sortField) {

            case SORT_POSITION:
                compareResult = comparePositions(moveData1, moveData2);
                break;

            case SORT_GAMES:
                compareResult = -compareGames(moveData1, moveData2);
                if (compareResult == 0) {
                    compareResult = -comparePercentages(moveData1, moveData2);
                }
                if (compareResult == 0) {
                    compareResult = -comparePositions(moveData1, moveData2);
                }
                break;

            case SORT_PERCENTAGE:
                compareResult = -comparePercentages(moveData1, moveData2);
                if (compareResult == 0) {
                    compareResult = -compareGames(moveData1, moveData2);
                }
                if (compareResult == 0) {
                    compareResult = -comparePositions(moveData1, moveData2);
                }
        }

        return compareResult;
    }

    public int comparePositions(GameStorerSearchResponseMoveData moveData1, GameStorerSearchResponseMoveData moveData2) {

        String move1 = PGNGameFormat.formatCoordinates(moveData1.getMove());
        String alpha1 = move1.substring(0, 1);
        int numeric1 = Integer.parseInt(move1.substring(1));

        String move2 = PGNGameFormat.formatCoordinates(moveData2.getMove());
        String alpha2 = move2.substring(0, 1);
        int numeric2 = Integer.parseInt(move2.substring(1));

        if (alpha1.equals(alpha2)) {
            return new Integer(numeric1).compareTo(new Integer(numeric2));
        } else {
            return move1.compareTo(move2);
        }
    }

    public int compareGames(GameStorerSearchResponseMoveData moveData1, GameStorerSearchResponseMoveData moveData2) {
        return new Integer(moveData1.getGames()).compareTo(new Integer(moveData2.getGames()));
    }

    public int comparePercentages(GameStorerSearchResponseMoveData moveData1, GameStorerSearchResponseMoveData moveData2) {
        return new Double(moveData1.getPercentage()).compareTo(new Double(moveData2.getPercentage()));
    }
}