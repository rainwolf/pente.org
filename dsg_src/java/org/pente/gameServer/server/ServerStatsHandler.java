/** ServerStatsHandler.java
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

package org.pente.gameServer.server;

import java.util.*;

import org.pente.gameServer.event.*;

public class ServerStatsHandler {

    private int logins = 0;
    private int games = 0;
    private int maxPlayers = 0;
    private int currentPlayers = 0;
    private int events = 0;

    private Date startDate = new Date();
    
    public synchronized void gamePlayed() {
        games++;
    }

    public synchronized void playerJoined() {
        logins++;
        if (++currentPlayers > maxPlayers) {
            maxPlayers = currentPlayers;
        }        
    }
    public synchronized void playerExited() {
        currentPlayers--;
        if (currentPlayers < 0) {
            currentPlayers = 0;
        }
    }

    public synchronized void eventProcessed() {
        events++;
    }
    
    public int getCurrentPlayers() {
        return currentPlayers;
    }
    
    public synchronized DSGServerStatsEvent handleServerStatsRequest(String player) {

        return new DSGServerStatsEvent(
            logins, games, maxPlayers, events, startDate);
    }
}
