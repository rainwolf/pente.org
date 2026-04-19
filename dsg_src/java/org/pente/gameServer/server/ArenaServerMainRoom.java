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

import org.pente.gameServer.event.DSGMainRoomErrorEvent;
import org.pente.gameServer.event.DSGTextMainRoomErrorEvent;

public class ArenaServerMainRoom extends ServerMainRoom {

    public void handleText(String player, String text) {
        if (!isPlayerInMainRoom(player)) {
            dsgEventRouter.routeEvent(
                    new DSGTextMainRoomErrorEvent(player, text, DSGMainRoomErrorEvent.NOT_IN_MAIN_ROOM),
                    player);
        } else {
            super.handleText(player, text);
        }
    }

    public ArenaServerMainRoom(
            Server server,
            Resources resources,
            DSGEventToPlayerRouter dsgEventRouter) throws Throwable {

        super(server, resources, dsgEventRouter);
    }
}
