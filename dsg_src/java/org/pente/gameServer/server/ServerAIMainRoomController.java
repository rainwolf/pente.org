/** ServerAIMainRoomController.java
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

import org.apache.log4j.*;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.event.*;
import org.pente.gameServer.core.AIData;

/** AI Controller to handle an AI player in the main room.
 *  Interfaces between the ServerTable and ServerAITableController(s).
 *  There will be one instance per AIPlayer.
 */
public class ServerAIMainRoomController implements DSGEventListener {

    private Category log4j = Category.getInstance(
        ServerAIMainRoomController.class.getName());

    private AIData aiData;
    //private ServerAITableController tables[];
    private Hashtable tables;
    private Server server;

    public ServerAIMainRoomController(Server server) {
        this.server = server;
        
        // = new ServerAITableController[PenteServerContextListener.MAX_TABLES + 1];
        tables = new Hashtable();
    }
    
    private ServerAITableController getTable(int tableNum) {
        
        ServerAITableController controller = 
            (ServerAITableController) tables.get(new Integer(tableNum));
        if (controller == null) {
            controller = new ServerAITableController(server);
            tables.put(new Integer(tableNum), controller);
        }
        
        return controller;
    }
    public void removeTable(int tableNum) {
        tables.remove(new Integer(tableNum));
    }
    
    public int addAIPlayer(DSGAddAITableEvent addEvent) {
        
        aiData = addEvent.getAIData();
        
        int error = ServerTable.NO_ERROR;

        try {
	        if (tables.get(new Integer(addEvent.getTable())) != null) {
	            return DSGTableErrorEvent.ALREADY_IN_TABLE;
	        }
	        else {
	
	            boolean inMainRoom = isInAnyTables();
	            
	            ServerAITableController controller = getTable(addEvent.getTable());
	            controller.addAIPlayer(addEvent);
	            
	            if (!inMainRoom) {
	                server.addPlayerListener(this, aiData.getUserIDName(), false);
	
	                DSGPlayerData aipd = server.getDSGPlayerStorer().loadPlayer(aiData.getUserIDName());
	                	
	                // join main room
	                DSGEvent joinEvent = new DSGJoinMainRoomEvent(
	                    aiData.getUserIDName(), aipd);
	
	                server.routeEventToMainRoom(joinEvent);
	            }
	            else {
	                controller.joinTable();
	            }
	        }
    	} catch (Exception e) {
    		e.printStackTrace();
    		error = DSGTableErrorEvent.UNKNOWN;
    	}
        
        return error;
    }
    
    public void removeAIPlayer(int tableNum) {
        
        if (isInTable(tableNum)) {

            ServerAITableController controller = getTable(tableNum);
            controller.removeAIPlayer();
            removeTable(tableNum);
            
            if (!isInAnyTables()) {
                
                server.removePlayerListener(aiData.getUserIDName(), false);
                
                // exit main room
                DSGEvent exitEvent = new DSGExitMainRoomEvent(
                    aiData.getUserIDName(), false);
                server.routeEventToMainRoom(exitEvent);
            }
        }
    }
    
    public boolean isInAnyTables() {
        return tables.size() > 0;
    }
    
    public boolean isInTable(int tableNum) {
        return tables.containsKey(new Integer(tableNum));
    }
    
    public void eventOccurred(final DSGEvent dsgEvent) {
        
        if (dsgEvent instanceof DSGPingEvent) {
            // send ping back or ignore?
            return;
        }
        else if (dsgEvent instanceof DSGTableEvent) {
            int tableNum = ((DSGTableEvent) dsgEvent).getTable();
            if (isInTable(tableNum)) {
                ServerAITableController controller = getTable(tableNum);
                controller.eventOccurred(dsgEvent);
            }
        }
        // if we get a join main room event, send the event to all tables since
        // we don't know which one needs to still join
        else if (dsgEvent instanceof DSGJoinMainRoomEvent) {
            DSGJoinMainRoomEvent joinEvent = (DSGJoinMainRoomEvent) dsgEvent;
            if (joinEvent.getPlayer().equals(aiData.getUserIDName())) {
                // hack, run in seperate thread to allow clients to receive
                // ai join main room event before ai join table # event
                // this allows client to get DSGPlayerData before player
                // joins a table causing a NullPointerException
                new Thread(new Runnable() {
                    public void run() { 
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                        }
                        for (Enumeration e = tables.elements(); e.hasMoreElements();) {
                            ServerAITableController controller = (ServerAITableController)
                                e.nextElement();
                            controller.eventOccurred(dsgEvent);       
                        }
                    }
                }).start();
            }                 
        }
    }
}
