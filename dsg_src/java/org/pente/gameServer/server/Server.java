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
public class Server {

    protected static Category log4j = 
        Category.getInstance(Server.class.getName());

    protected Resources resources;
    protected volatile boolean running = true;

    protected ServerData serverData;
    protected String name;
    protected int port;
    protected ServerSocket gameServerSocket;
    protected Thread gameThread;
    
    protected DSGPlayerStorerLoginHandler loginHandler;
    protected RegisterHandler registerHandler;
    protected CacheDSGPlayerStorer dsgPlayerStorer;
    protected GameStorer fileGameStorer;
    protected GameStorer gameStorer;
    protected PlayerStorer playerStorer;

    protected DSGEventToPlayerRouter dsgEventToPlayerRouter;

    protected MySQLDSGReturnEmailStorer returnEmailStorer;

    protected List tables;
    
    protected SynchronizedServerMainRoom mainRoom;

    protected PingManager pingManager;
    protected ServerStatsHandler serverStatsHandler;

    protected ServerAIController aiController;
    protected Collection aiDataCollection;
    
    protected PasswordHelper passwordHelper;
    protected ActivityLogger activityLogger;

    protected GameEventData gameEvent;

    protected CacheKOTHStorer kothStorer;
    
    protected DSGFollowerStorer followerStorer;
    
//    protected ServerContainer serverContainer;
//
//    public void setServerContainer(ServerContainer serverContainer) {
//        this.serverContainer = serverContainer;
//    }

    public Server(Resources resources,
                  ServerData serverData) throws Throwable {

        this.resources = resources;
        this.dsgPlayerStorer = resources.getDsgPlayerStorer();
		this.serverStatsHandler = resources.getServerStatsHandler();
        this.returnEmailStorer = resources.getReturnEmailStorer();
        this.gameStorer = resources.getGameStorer();
        this.fileGameStorer = resources.getFileGameStorer();
        this.playerStorer = resources.getPlayerStorer();
        this.passwordHelper = resources.getPasswordHelper();
        this.activityLogger = resources.getActivityLogger();
        this.serverData = serverData;
        this.name = serverData.getName();
        this.port = serverData.getPort();
        this.kothStorer = resources.getKOTHStorer();
        this.followerStorer = resources.getFollowerStorer();

        System.setProperty("javax.net.ssl.keyStore", "/var/lib/tomcat9/webapps/MyDSKeyStore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "***REMOVED***");
        try {
            ServerSocketFactory ssf = SSLServerSocketFactory.getDefault();
            gameServerSocket = ssf.createServerSocket(port);
        } catch (Throwable t) {
            log4j.error("Server [" + name + "], could not listen on port: " + 
                port + ".", t);
            return;
        }
        
        // can these be shared also?
        loginHandler = new DSGPlayerStorerLoginHandler(dsgPlayerStorer);
        registerHandler = new DSGPlayerStorerRegisterHandler(dsgPlayerStorer);

        // initialize per server stuff
        DSGEventToPlayerRouter baseRouter = new SimpleDSGEventToPlayerRouter();
        dsgEventToPlayerRouter = 
            new LoggingDSGEventToPlayerRouter(
                serverData.getServerId(), serverStatsHandler, baseRouter);
        pingManager = new DSGEventPingManager(dsgEventToPlayerRouter); // don't log ping events


        tables = new ArrayList(5);
        tables.add(new Object());//dummy 1st table
        
        mainRoom = new SynchronizedServerMainRoom(
            this, resources, dsgEventToPlayerRouter);
        dsgPlayerStorer.addPlayerDataChangeListener(mainRoom);
        dsgPlayerStorer.addIgnoreDataChangeListener(mainRoom);
        
        aiController = new ServerAIController(this, passwordHelper);
        initAiData(resources.getAiConfigFile());


        gameThread = new Thread(new Runnable() {
            public void run() {
                while (running) {
                    try {
                        Socket socket = gameServerSocket.accept();
                        
                        // don't wait for more messages to come in before sending
                        // because server sends many short messages
                        socket.setTcpNoDelay(true); 
                        // timeout after 30 seconds
                        // this should be ok because we send pings every 15 seconds
                        //socket.setSoTimeout(30 * 1000); 
                                             
                        addPlayerSocket(socket);

                    } catch (Throwable t) {
                        if (running) {
                            log4j.error("Error accepting game socket " +
                                "connection [" + name + "].", t);
                        }
                    }
                }
                
                // close socket
                try {
                    gameServerSocket.close();
                    destroy();
                    log4j.info("Game server socket [" + name + "] closed.");
                } catch (Throwable t) {
                    log4j.error("Error closing game server socket [" + name + "].", t);
                }
            }
        }, "DSG Server [" + name + "]");
        gameThread.start();


    }

    public boolean allowAccess(String player) {
    	return !serverData.isPrivateServer() || serverData.getPlayers().contains(player);
    }
    
    public void initAiData(String aiConfigFile) {

        /** tournament tables don't allow ai games */
        if (serverData.isTournament()) {
            aiDataCollection = new ArrayList();
        }
        else {
            AIConfigurator configurator = new XMLAIConfigurator();
            try {
                aiDataCollection = configurator.getAIData(aiConfigFile);
            } catch (Throwable t) {
                log4j.error("Problem initializing ai data, starting with no AI players.", t);
                aiDataCollection = new ArrayList();
            }
        }
    }

//    For WebSockets
    public WebSocketDSGEventHandler addPlayerWebSocketSession(Session session) {
        WebSocketDSGEventHandler socketDSGEventHandler = 
                new WebSocketDSGEventHandler(session);
        log4j.info("Websocket Connection from " + socketDSGEventHandler.getHostAddress());
        ServerPlayer serverPlayer =
                new ServerPlayer(this,
                        dsgEventToPlayerRouter,
                        socketDSGEventHandler,
                        dsgPlayerStorer,
                        loginHandler,
                        pingManager,
                        serverStatsHandler,
                        aiDataCollection,
                        passwordHelper,
                        activityLogger);
        socketDSGEventHandler.addListener(serverPlayer);
        return socketDSGEventHandler;
    }
    
//    For TCP sockets
    public void addPlayerSocket(Socket socket) {

        ServerSocketDSGEventHandler socketDSGEventHandler = 
            new ServerSocketDSGEventHandler(socket);
        log4j.info("Connection from " + socketDSGEventHandler.getHostAddress());
        
        ServerPlayer serverPlayer = 
            new ServerPlayer(this,
                             dsgEventToPlayerRouter,
                             socketDSGEventHandler,
                             dsgPlayerStorer,
                             loginHandler,
                             pingManager,
                             serverStatsHandler,
                             aiDataCollection,
                             passwordHelper,
                             activityLogger);
        socketDSGEventHandler.addListener(serverPlayer);
    }
    
    public void addPlayerListener(DSGEventListener listener, String name, 
        boolean isHuman) {
        
        // add route to player for events from the server
        dsgEventToPlayerRouter.addRoute(listener, name);
        
        if (isHuman) {

            // add the player to the ping manager
            pingManager.addPlayer(name);

            // increment logins for server stats
            serverStatsHandler.playerJoined();
        }
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
    public SocketDSGEventHandler removePlayerListener(String name, boolean isHuman) {
        DSGEventListener l = dsgEventToPlayerRouter.removeRoute(name);
        
        SocketDSGEventHandler s = null;
        if (l instanceof SocketDSGEventHandler) {
        	s = (SocketDSGEventHandler) l;
        }
        	

        if (isHuman) {
            pingManager.removePlayer(name);
            serverStatsHandler.playerExited();
        }
        
        return s;
    }
    
    public void routeEventToMainRoom(DSGEvent event) {
        mainRoom.eventOccurred(event);
    }
    public void routeEventToTable(DSGEvent event, int tableNum) {
        if (event instanceof DSGJoinTableEvent &&
            tableNum == DSGJoinTableEvent.CREATE_NEW_TABLE) {
            try {
                tableNum = createNewTable((DSGJoinTableEvent) event);
                ((DSGJoinTableEvent) event).setTable(tableNum);
            } catch (Throwable t) {
                log4j.error("Problem creating new table.", t);
            }
        }
        synchronized (tables) {
            if (tableNum < 1 || tableNum >= tables.size() || tables.get(tableNum) == null) {
                log4j.error("Invalid table: " + tableNum + " for event " + event);
                return;
            }
            ((SynchronizedServerTable) tables.get(tableNum)).eventOccurred(event);
        }
    }
    public void routeEventToAllTables(DSGEvent event) {
        synchronized (tables) {
            for (int i = 1; i < tables.size(); i++) {
                SynchronizedServerTable t = (SynchronizedServerTable) tables.get(i);
                if (t != null) {
                    t.eventOccurred(event);
                }
            }
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
    
    
    public DSGPlayerStorer getDSGPlayerStorer() {
        return dsgPlayerStorer;
    }
    public RegisterHandler getRegisterHandler() {
        return registerHandler;
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