/**
 * ServerMainRoom.java
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

import java.util.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.event.*;
import org.pente.gameServer.tourney.*;

public class TournamentServerMainRoom extends ServerMainRoom {

    public TournamentServerMainRoom(
            Server server,
            Resources resources,
            DSGEventToPlayerRouter dsgEventRouter) throws Throwable {

        super(server, resources, dsgEventRouter);
    }

    public void handleJoin(DSGJoinMainRoomEvent joinEvent) {

        // this shouldn't happen because ServerPlayer checks before logging in a player
        if (isPlayerInMainRoom(joinEvent.getPlayer())) {
            dsgEventRouter.routeEvent(
                    new DSGJoinMainRoomErrorEvent(joinEvent.getPlayer(),
                            DSGMainRoomErrorEvent.ALREADY_IN_MAIN_ROOM),
                    joinEvent.getPlayer());
        } else {
            playersInMainRoom.put(joinEvent.getPlayer(), joinEvent.getDSGPlayerData());

            sendPlayerList(joinEvent.getPlayer());

            broadcast(joinEvent);

            server.routeEventToAllTables(joinEvent);

            ((TournamentServer) server).matchOnJoin(joinEvent.getDSGPlayerData());

        }

    }

    public void handleExit(DSGExitMainRoomEvent exitEvent) {

        if (isPlayerInMainRoom(exitEvent.getPlayer())) {

            playersInMainRoom.remove(exitEvent.getPlayer());

            server.routeEventToAllTables(exitEvent);

            broadcast(new DSGExitMainRoomEvent(exitEvent.getPlayer(), exitEvent.wasBooted()));
        }

        if (server.getTourney().isComplete()) {
            if (playersInMainRoom.size() == 0) {
                resources.removeServer(server.getServerData().getServerId());
            }
        }
    }

    public void handleTourneyEvent(TourneyEvent event) {
        Tourney tourney = server.getTourney();
        if (tourney == null || event.getEid() != tourney.getEventID()) return;
        if (!tourney.isSpeed()) return;


        if (event.getType() == TourneyEvent.NEW_ROUND) {
            ((TournamentServer) server).initNewRound();
        }

        super.handleTourneyEvent(event);
    }

}