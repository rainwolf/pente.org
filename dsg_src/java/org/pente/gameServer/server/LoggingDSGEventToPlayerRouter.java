/**
 * LoggingDSGEventToPlayerRouter.java
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

import org.apache.log4j.*;

import org.pente.gameServer.event.*;

public class LoggingDSGEventToPlayerRouter implements DSGEventToPlayerRouter {

    private static Category log4j = Category.getInstance(
            LoggingDSGEventToPlayerRouter.class.getName());

    private long serverId;
    private ServerStatsHandler serverStatsHandler;
    private DSGEventToPlayerRouter baseRouter;

    public LoggingDSGEventToPlayerRouter(
            long serverId,
            ServerStatsHandler serverStatsHandler,
            DSGEventToPlayerRouter baseRouter) {

        this.serverId = serverId;
        this.serverStatsHandler = serverStatsHandler;
        this.baseRouter = baseRouter;
    }

    public void addRoute(DSGEventListener dsgEventListener, String name) {
        baseRouter.addRoute(dsgEventListener, name);
    }

    public DSGEventListener removeRoute(String name) {
        return baseRouter.removeRoute(name);
    }

    public DSGEventListener getRoute(String name) {
        return baseRouter.getRoute(name);
    }

    public void routeEvent(DSGEvent dsgEvent, String name) {
        baseRouter.routeEvent(dsgEvent, name);

        log4j.info("[" + serverId + "] out: " + name + ", " + dsgEvent);

        serverStatsHandler.eventProcessed();
    }
}

