/**
 * SortedGameStorerSearchResponseData.java
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

import org.pente.game.*;

public class SortedGameStorerSearchResponseData extends SimpleGameStorerSearchResponseData {

    private int moveSortField;
    private int maxMoves;
    private int maxGames;

    private SortedMap<GameStorerSearchResponseMoveData, GameStorerSearchResponseMoveData> moveData;
    private List<GameData> games;

    public SortedGameStorerSearchResponseData(int moveSortField) {
        this.moveSortField = moveSortField;
        GameStorerSearchResponseMoveDataComparator moveComparator =
                new GameStorerSearchResponseMoveDataComparator(moveSortField);
        this.moveData = new TreeMap<GameStorerSearchResponseMoveData, GameStorerSearchResponseMoveData>(moveComparator);

        this.games = new ArrayList<>();
    }

    public SortedGameStorerSearchResponseData(int moveSortField, int maxMoves, int maxGames) {
        this(moveSortField);
        this.maxMoves = maxMoves;
        this.maxGames = maxGames;
    }

    public SortedGameStorerSearchResponseData(int moveSortField, int maxMoves, int maxGames,
                                              Hashtable<?, GameStorerSearchResponseMoveData> data) {
        this(moveSortField, maxMoves, maxGames);

        Enumeration<GameStorerSearchResponseMoveData> e = data.elements();
        while (e.hasMoreElements()) {
            GameStorerSearchResponseMoveData obj = e.nextElement();
            moveData.put(obj, obj);
        }
    }

    public SortedGameStorerSearchResponseData getBlankCopy() {
        return new SortedGameStorerSearchResponseData(moveSortField, maxMoves, maxGames);
    }

    public void addSearchResponseMoveData(GameStorerSearchResponseMoveData data) {

        moveData.put(data, data);

        if (maxMoves > 0 && moveData.size() > maxMoves) {
            moveData.remove(moveData.lastKey());
        }
    }

    public GameStorerSearchResponseMoveData getSearchResponseMoveData(int move) {
        Iterator iter = moveData.values().iterator();
        while (iter.hasNext()) {
            GameStorerSearchResponseMoveData moveData = (GameStorerSearchResponseMoveData) iter.next();
            if (moveData.getMove() == move) {
                return moveData;
            }
        }

        return null;
    }

    public Vector<GameStorerSearchResponseMoveData> searchResponseMoveData() {
        return new Vector<>(moveData.values());
    }

    public int getNumSearchResponseMoves() {
        return moveData.size();
    }

    public void addGame(GameData data) {
        games.add(data);
    }

    public Vector<GameData> getGames() {
        return new Vector<>(games);
    }
}