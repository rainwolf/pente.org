/**
 * PlayerListComponent.java
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

package org.pente.gameServer.client;

import java.util.Enumeration;

import org.pente.gameServer.core.*;
import org.pente.gameServer.core.DSGPlayerData;

public interface PlayerListComponent extends PlayerDataChangeListener {

    public void addGetStatsListener(PlayerActionListener getStatsListener);

    public void removeGetStatsListener(PlayerActionListener getStatsListener);

    public void clearPlayers();

    public Enumeration getPlayers();

    public void addPlayer(DSGPlayerData playerData);

    public void removePlayer(String playerName);

    public String getSelectedPlayer();

    public void setGame(int game);

    public void setOwner(String player);

    public void setTableName(String tableName);

    public void showNumPlayers(boolean show);
}