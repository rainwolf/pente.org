/**
 * SimpleGameStorerSearchRequestData.java
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

import org.pente.game.*;

public class SimpleGameStorerSearchRequestData implements GameStorerSearchRequestData {

    protected GameData gameData;
    protected GameStorerSearchRequestFilterData filterData;
    protected String outFormat;
    protected int responseOrder;
    protected String responseParams;

    public SimpleGameStorerSearchRequestData() {
        gameData = new DefaultGameData();
    }

    public SimpleGameStorerSearchRequestData(MoveData moveData) {
        this();

        for (int i = 0; i < moveData.getNumMoves(); i++) {
            addMove(moveData.getMove(i));
        }
    }

    public SimpleGameStorerSearchRequestData(GameData gameData) {
        this((MoveData) gameData);

        filterData = new SimpleGameStorerSearchRequestFilterData();
        filterData.setSite(gameData.getSite());
        filterData.setEvent(gameData.getEvent());
        filterData.setRound(gameData.getRound());
        filterData.setSection(gameData.getSection());
        filterData.setPlayer1Name(gameData.getPlayer1Data().getUserIDName());
        filterData.setPlayer2Name(gameData.getPlayer2Data().getUserIDName());
        filterData.setWinner(gameData.getWinner());
    }

    public void setGameStorerSearchResponseFormat(String format) {
        this.outFormat = format;
    }

    public String getGameStorerSearchResponseFormat() {
        return outFormat;
    }

    public void setGameStorerSearchResponseParams(String params) {
        responseParams = params;
    }

    public String getGameStorerSearchResponseParams() {
        return responseParams;
    }

    public void setGameStorerSearchResponseOrder(int order) {
        this.responseOrder = order;
    }

    public int getGameStorerSearchResponseOrder() {
        return responseOrder;
    }

    public void setGameStorerSearchRequestFilterData(GameStorerSearchRequestFilterData filterData) {
        this.filterData = filterData;
    }

    public GameStorerSearchRequestFilterData getGameStorerSearchRequestFilterData() {
        return filterData;
    }

    public void addMove(int move) {
        gameData.addMove(move);
    }

    public void undoMove() {
        gameData.undoMove();
    }

    public int getMove(int num) {
        return gameData.getMove(num);
    }

    public int getNumMoves() {
        return gameData.getNumMoves();
    }

    public int[] getMoves() {
        return gameData.getMoves();
    }
}