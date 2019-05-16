/** Server.java
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
import java.net.*;

import org.apache.log4j.*;

import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.*;
import org.pente.gameServer.tourney.*;

import org.pente.kingOfTheHill.*;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import javax.websocket.Session;


/** A simple class to contain the necessary components that make up the server
 */
public class TournamentServer extends Server {

    public TournamentServer(Resources resources,
                  ServerData serverData) throws Throwable {

        super(resources, serverData);
//        this.resources = resources;
//        this.dsgPlayerStorer = resources.getDsgPlayerStorer();
//		this.serverStatsHandler = resources.getServerStatsHandler();
//        this.returnEmailStorer = resources.getReturnEmailStorer();
//        this.gameStorer = resources.getGameStorer();
//        this.fileGameStorer = resources.getFileGameStorer();
//        this.playerStorer = resources.getPlayerStorer();
//        this.passwordHelper = resources.getPasswordHelper();
//        this.activityLogger = resources.getActivityLogger();
//        this.serverData = serverData;
//        this.name = serverData.getName();
//        this.port = serverData.getPort();
//        this.kothStorer = resources.getKOTHStorer();
//        this.followerStorer = resources.getFollowerStorer();
//
//        System.setProperty("javax.net.ssl.keyStore", "/var/lib/tomcat8/webapps/MyDSKeyStore.jks");
//        System.setProperty("javax.net.ssl.keyStorePassword", "nuria8a13b");
//        try {
//            ServerSocketFactory ssf = SSLServerSocketFactory.getDefault();
//            gameServerSocket = ssf.createServerSocket(port);
//        } catch (Throwable t) {
//            log4j.error("Server [" + name + "], could not listen on port: " + 
//                port + ".", t);
//            return;
//        }
//        
//        // can these be shared also?
//        loginHandler = new DSGPlayerStorerLoginHandler(dsgPlayerStorer);
//        registerHandler = new DSGPlayerStorerRegisterHandler(dsgPlayerStorer);
//
//        // initialize per server stuff
//        DSGEventToPlayerRouter baseRouter = new SimpleDSGEventToPlayerRouter();
//        dsgEventToPlayerRouter = 
//            new LoggingDSGEventToPlayerRouter(
//                serverData.getServerId(), serverStatsHandler, baseRouter);
//        pingManager = new DSGEventPingManager(dsgEventToPlayerRouter); // don't log ping events
//
//
//        tables = new ArrayList(5);
//        tables.add(new Object());//dummy 1st table
//        
//        mainRoom = new SynchronizedServerMainRoom(
//            this, resources, dsgEventToPlayerRouter);
//        dsgPlayerStorer.addPlayerDataChangeListener(mainRoom);
//        dsgPlayerStorer.addIgnoreDataChangeListener(mainRoom);
//        
//        aiController = new ServerAIController(this, passwordHelper);
//        initAiData(resources.getAiConfigFile());
//
//
//        gameThread = new Thread(new Runnable() {
//            public void run() {
//                while (running) {
//                    try {
//                        Socket socket = gameServerSocket.accept();
//                        
//                        // don't wait for more messages to come in before sending
//                        // because server sends many short messages
//                        socket.setTcpNoDelay(true); 
//                        // timeout after 30 seconds
//                        // this should be ok because we send pings every 15 seconds
//                        //socket.setSoTimeout(30 * 1000); 
//                                             
//                        addPlayerSocket(socket);
//
//                    } catch (Throwable t) {
//                        if (running) {
//                            log4j.error("Error accepting game socket " +
//                                "connection [" + name + "].", t);
//                        }
//                    }
//                }
//                
//                // close socket
//                try {
//                    gameServerSocket.close();
//                    destroy();
//                    log4j.info("Game server socket [" + name + "] closed.");
//                } catch (Throwable t) {
//                    log4j.error("Error closing game server socket [" + name + "].", t);
//                }
//            }
//        }, "DSG Server [" + name + "]");
//        gameThread.start();


    }

    public void bootPlayer(String name, int minutes) {
    	final SocketDSGEventHandler s = removePlayerListener(name, true);
    	loginHandler.bootPlayer(name, minutes);
    	
    	if (s != null) { //ai shoudln't happen
	    	// just in case client doesn't close the socket
	    	// do it here
			new Thread(new Runnable() {
				public void run() {
	
					// sleep for 3 seconds to give booted player a chance to
					// receive message that they were booted before closing socket
					try {
						Thread.sleep(3000);
					} catch (InterruptedException ie) {}
					
			    	s.destroy();
				}
			}).run();
    	}
    }
    
    public int createNewTable(DSGJoinTableEvent joinEvent) throws Throwable {

        int newTableNum = -1;
        Collection mainRoomPlayers = mainRoom.getPlayersInMainRoom();
        synchronized (tables) {

            for (int i = 1; i < tables.size(); i++) {
                SynchronizedServerTable t = (SynchronizedServerTable) tables.get(i);
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
                aiController, newTableNum, dsgEventToPlayerRouter, dsgPlayerStorer,
                pingManager, fileGameStorer, gameStorer, playerStorer,
                serverStatsHandler, returnEmailStorer, mainRoomPlayers,
                activityLogger, joinEvent, kothStorer);

            if (newTableNum == tables.size()) {
                tables.add(newT);
            }
            else {
                tables.set(newTableNum, newT);
            }
        }

        return newTableNum;
    }
    public void removeTable(int tableNum) {
        synchronized (tables) {
            SynchronizedServerTable t = (SynchronizedServerTable) tables.get(tableNum);
            if (t != null) {
                t.destroy();
                tables.set(tableNum, null);
            }
        }
    }
    
    
    public void destroy() {

        try {
            running = false;
            if (gameThread != null) {
                gameThread.interrupt();
            }
            if (gameServerSocket != null) {
                gameServerSocket.close();
            }
            
        } catch (Throwable t) {
            log4j.error("Error stopping server threads [" + name + ".", t);
        }
        
        synchronized (tables) {
            for (int i = 1; i < tables.size(); i++) {
                SynchronizedServerTable t = (SynchronizedServerTable) tables.get(i);
                if (t != null) {
                    t.destroy();
                }
            }
        }
        if (dsgPlayerStorer != null) {
            dsgPlayerStorer.removePlayerDataChangeListener(mainRoom);
        }
        if (mainRoom != null) {
            mainRoom.destroy();
        }
        if (pingManager != null) {
	        pingManager.destroy();
        }
    }
    public ServerData getServerData() {
        return serverData;
    }
    public void setServerData(ServerData serverData) {
        this.serverData = serverData;
    }
    public Tourney getTourney() {
        Tourney tourney = null;
        try {
            if (serverData.isTournament()) {
                GameEventData first = (GameEventData) serverData.getGameEvents().get(0);
                tourney = resources.getTourneyStorer().getTourney(first.getEventID());
            }
        } catch (Throwable t) {
            log4j.error("Error getting tourney.", t);
        }

        return tourney;
    }
}