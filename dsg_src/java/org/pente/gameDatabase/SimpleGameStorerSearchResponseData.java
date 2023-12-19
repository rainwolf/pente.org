/**
 * SimpleGameStorerSearchResponseData.java
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

public class SimpleGameStorerSearchResponseData implements GameStorerSearchResponseData {

    protected GameStorerSearchRequestData requestData;
    protected Vector<GameStorerSearchResponseMoveData> searchResultMoves;
    protected Vector<GameData> matchedGames;
    protected int rotation;

    public SimpleGameStorerSearchResponseData() {
        searchResultMoves = new Vector<>();
        matchedGames = new Vector<>();
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    public int getRotation() {
        return rotation;
    }

    public void setGameStorerSearchRequestData(GameStorerSearchRequestData requestData) {
        this.requestData = requestData;
    }

    public GameStorerSearchRequestData getGameStorerSearchRequestData() {
        return requestData;
    }

    public void addSearchResponseMoveData(GameStorerSearchResponseMoveData data) {
        if (!searchResultMoves.contains(data)) {
            searchResultMoves.addElement(data);
        }
    }

    public GameStorerSearchResponseMoveData getSearchResponseMoveData(int move) {

        for (int i = 0; i < searchResultMoves.size(); i++) {
            GameStorerSearchResponseMoveData moveData = searchResultMoves.elementAt(i);
            if (moveData.getMove() == move) {
                return moveData;
            }
        }

        return null;
    }

    public Vector<GameStorerSearchResponseMoveData> searchResponseMoveData() {
        return searchResultMoves;
    }

    public int getNumSearchResponseMoves() {
        return searchResultMoves.size();
    }

    public void addGame(GameData data) {
        matchedGames.addElement(data);
    }

    public Vector<GameData> getGames() {
        return matchedGames;
    }

    public boolean containsGame(GameData d) {
        for (int i = 0; i < matchedGames.size(); i++) {
            GameData dd = matchedGames.elementAt(i);
            if (dd.getGameID() == d.getGameID()) return true;
        }
        return false;
    }
}