/**
 * Server.java
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

package org.pente.gameServer.server;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.ServerData;
import org.pente.gameServer.event.DSGArenaCreateTableEvent;
import org.pente.gameServer.event.DSGEvent;
import org.pente.gameServer.event.DSGJoinTableEvent;

import java.util.Collection;


/**
 * A simple class to contain the necessary components that make up the server
 */
public class ArenaServer extends Server {

    public ArenaServer(Resources resources,
                       ServerData serverData) throws Throwable {

        super(resources, serverData);
    }

    public void routeEventToTable(DSGEvent event, int tableNum) {
        if (event instanceof DSGArenaCreateTableEvent) {
            DSGJoinTableEvent joinEvent = new DSGJoinTableEvent();
            try {
                tableNum = createNewTable((DSGArenaCreateTableEvent) event);
                ((DSGArenaCreateTableEvent) event).setTable(tableNum);
            } catch (Throwable t) {
                log4j.error("Problem creating new ArenaServer table.", t);
            }
            joinEvent.setPlayer(((DSGArenaCreateTableEvent) event).getPlayer());
            joinEvent.setTable(tableNum);
            event = joinEvent;
        }
        synchronized (tables) {
            if (tableNum < 1 || tableNum >= tables.size() || tables.get(tableNum) == null) {
                log4j.error("Invalid table: " + tableNum + " for event " + event);
                return;
            }
            tables.get(tableNum).eventOccurred(event);
        }
    }

    public int createNewTable(DSGArenaCreateTableEvent joinEvent) throws Throwable {
        int newTableNum = -1;
        Collection<DSGPlayerData> mainRoomPlayers = mainRoom.getPlayersInMainRoom();
        synchronized (tables) {

            for (int i = 1; i < tables.size(); i++) {
                SynchronizedServerTable t = tables.get(i);
                if (t == null) {
                    newTableNum = i;
                    break;
                }
            }
            // if all tables full, add to list
            if (newTableNum == -1) {
                newTableNum = tables.size();
            }

            SynchronizedServerTable newT = new SynchronizedServerTable(this,
                    resources,
                    newTableNum, dsgEventToPlayerRouter, dsgPlayerStorer,
                    pingManager, fileGameStorer, gameStorer, playerStorer,
                    serverStatsHandler, returnEmailStorer, mainRoomPlayers,
                    activityLogger, joinEvent);

            if (newTableNum == tables.size()) {
                tables.add(newT);
            } else {
                tables.set(newTableNum, newT);
            }
        }

        return newTableNum;
    }
}