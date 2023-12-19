/**
 * ServerAIController.java
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

/** Main controller for all AIPlayer controllers.  One instance
 *  for the whole server.  Keeps references to all ServerAIMainRoomControllers
 *  and handles adding/removing them.
 */
public class ServerAIController {

    private Category log4j = Category.getInstance(
            ServerAIController.class.getName());

    private Map<String, ServerAIMainRoomController> aiPlayers;
    private Server server;
    private PasswordHelper passwordHelper;

    public ServerAIController(Server server, PasswordHelper passwordHelper) {
        this.server = server;
        this.passwordHelper = passwordHelper;
        aiPlayers = new HashMap<>();
    }

    public synchronized int addAIPlayer(DSGAddAITableEvent addEvent) {

        AIData aiData = addEvent.getAIData();
        ServerAIMainRoomController controller =
                (ServerAIMainRoomController) aiPlayers.get(aiData.getUserIDName());

        if (controller == null) {
            makeSureAIPlayerExists(aiData.getUserIDName());
            controller = new ServerAIMainRoomController(server);
            aiPlayers.put(aiData.getUserIDName(), controller);
        }

        return controller.addAIPlayer(addEvent);
    }

    public synchronized void removeAIPlayer(String player, int tableNum) {

        ServerAIMainRoomController controller =
                (ServerAIMainRoomController) aiPlayers.get(player);

        if (controller.isInTable(tableNum)) {
            controller.removeAIPlayer(tableNum);

            if (!controller.isInAnyTables()) {
                aiPlayers.remove(controller);
            }
        }
    }

    public void makeSureAIPlayerExists(String name) {

        DSGPlayerStorer dsgPlayerStorer = server.getDSGPlayerStorer();
        RegisterHandler registerHandler = server.getRegisterHandler();
        boolean registered = false;

        try {
            DSGPlayerData data = dsgPlayerStorer.loadPlayer(name);
            registered = data != null;

        } catch (DSGPlayerStoreException e) {
            log4j.error("Failed to load ai player data for registration", e);
        }

        if (!registered) {
            String password = "" + (int) (Math.random() * 1000000);
            int registrationResult = registerHandler.register(
                    name, password, passwordHelper.encrypt(password),
                    name + "@pente.org", false, false, null, null,
                    DSGPlayerData.UNKNOWN, 0, null);

            if (registrationResult == RegisterHandler.SUCCESS) {

                // update the player type to be computer
                try {
                    DSGPlayerData data = dsgPlayerStorer.loadPlayer(name);
                    data.setPlayerType(DSGPlayerData.COMPUTER);
                    dsgPlayerStorer.updatePlayer(data);
                } catch (DSGPlayerStoreException e) {
                    log4j.error("Failed to update player type to computer for " + name);
                }
            } else {
                log4j.error("Failed to register computer " + name + ", returned " +
                        "error code " + registrationResult);
            }
        }
    }
}
