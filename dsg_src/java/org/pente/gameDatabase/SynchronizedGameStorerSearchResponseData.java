/**
 * SynchronizedGameStorerSearchResponseData.java
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

public class SynchronizedGameStorerSearchResponseData implements GameStorerSearchResponseData {

    private GameStorerSearchResponseData responseData;

    public SynchronizedGameStorerSearchResponseData(GameStorerSearchResponseData responseData) {
        this.responseData = responseData;
    }

    public synchronized void setRotation(int rotation) {
        responseData.setRotation(rotation);
    }

    public synchronized int getRotation() {
        return responseData.getRotation();
    }

    public synchronized void setGameStorerSearchRequestData(GameStorerSearchRequestData requestData) {
        responseData.setGameStorerSearchRequestData(requestData);
    }

    public synchronized GameStorerSearchRequestData getGameStorerSearchRequestData() {
        return responseData.getGameStorerSearchRequestData();
    }

    public synchronized void addSearchResponseMoveData(GameStorerSearchResponseMoveData data) {
        responseData.addSearchResponseMoveData(data);
    }

    public synchronized GameStorerSearchResponseMoveData getSearchResponseMoveData(int move) {
        return responseData.getSearchResponseMoveData(move);
    }

    public synchronized Vector searchResponseMoveData() {
        return responseData.searchResponseMoveData();
    }

    public synchronized int getNumSearchResponseMoves() {
        return responseData.getNumSearchResponseMoves();
    }

    public synchronized void addGame(GameData data) {
        responseData.addGame(data);
    }

    public synchronized Vector getGames() {
        return responseData.getGames();
    }

    public synchronized boolean containsGame(GameData d) {
        return responseData.containsGame(d);
    }
}