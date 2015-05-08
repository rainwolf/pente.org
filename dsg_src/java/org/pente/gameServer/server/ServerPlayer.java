/** ServerPlayer.java
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

import org.pente.gameServer.event.*;
import org.pente.gameServer.core.*;

public class ServerPlayer implements DSGEventListener {

    private Server server;
    private long sid;
    
	//DSGEventListener for stats events
	private DSGEventToPlayerRouter dsgEventToPlayerRouter;
	private ServerSocketDSGEventHandler client;
	
	private DSGPlayerStorer dsgPlayerStorer;
	private LoginHandler loginHandler;
	private PingManager pingManager;
    private ServerStatsHandler serverStatsHandler;
	
	private boolean loggedIn;
	private String player;
    private DSGPlayerData playerData;

    private Collection aiDataCollection;
    private PasswordHelper passwordHelper;
    private ActivityLogger activityLogger;
    private String remoteAddr;

    private static volatile long guestId = 1;

    private static Category log4j = 
        Category.getInstance(ServerPlayer.class.getName());

	public ServerPlayer(Server server,
						DSGEventToPlayerRouter dsgEventToPlayerRouter,
						ServerSocketDSGEventHandler client,
						DSGPlayerStorer dsgPlayerStorer,
						LoginHandler loginHandler,
						PingManager pingManager,
                        ServerStatsHandler serverStatsHandler,
                        Collection aiDataCollection,
                        PasswordHelper passwordHelper,
                        ActivityLogger activityLogger) {

        this.server = server;
        sid = server.getServerData().getServerId();
		this.dsgEventToPlayerRouter = dsgEventToPlayerRouter;
		this.client = client;
		this.dsgPlayerStorer = dsgPlayerStorer;
		this.loginHandler = loginHandler;
		this.pingManager = pingManager;
        this.serverStatsHandler = serverStatsHandler;
        this.aiDataCollection = aiDataCollection;
        this.passwordHelper = passwordHelper;
        this.activityLogger = activityLogger;
	} 

    private String psid() { return "[" + sid + "] "; };
    
    public void eventOccurred(DSGEvent dsgEvent) {

        serverStatsHandler.eventProcessed();

    	if (!loggedIn) {
    		if (!(dsgEvent instanceof DSGLoginEvent)) {
    			//disconnect somehow?
    			//client.eventOccurred(disconnect event)
                log4j.info(psid() + "not logged in event: " + dsgEvent);
    		}
    		else {
    			DSGLoginEvent loginEvent = (DSGLoginEvent) dsgEvent;

    			log4j.info(psid() + "in: " + loginEvent);
                log4j.info(psid() + "from: " + client.getHostAddress());

                if (loginEvent.isGuest() && loginEvent.getPlayer() == null) {
                	loginEvent.setPlayer("guest" + guestId++);
                }
                
                
				// if a route exists already to a player with the name being used
				// to log in then the player is already logged in
                if (dsgEventToPlayerRouter.getRoute(loginEvent.getPlayer()) != null) {
					client.eventOccurred(
						new DSGJoinMainRoomErrorEvent(
							player, DSGMainRoomErrorEvent.ALREADY_IN_MAIN_ROOM));
				}
                // try either password that's sent, or try encrypting it
				else if (loginEvent.isGuest() ||
						 loginHandler.login(loginEvent.getPlayer(), loginEvent.getPassword()) ||
                         loginHandler.login(loginEvent.getPlayer(), passwordHelper.encrypt(loginEvent.getPassword()))) {

                    // for server status checking
                    if (loginEvent.getPlayer().equals("stat")) {
                        client.setPlayerName(loginEvent.getPlayer());
                        client.eventOccurred(loginEvent);
                        return;
                    }
                    if (!server.allowAccess(loginEvent.getPlayer())) {

        				client.eventOccurred(new DSGLoginErrorEvent(
                            loginEvent.getPlayer(), "", 
                            DSGLoginErrorEvent.PRIVATE_ROOM));
        				return;
                    }
                    
    				loggedIn = true;

    				// set player's name locally for checking future requests
    				player = loginEvent.getPlayer();
                    client.setPlayerName(player);
    				
                    remoteAddr = client.getHostAddress();
                    // don't currently ban from playing, just logging here
                    activityLogger.joinPlayer(
                        new ActivityData(player, remoteAddr, sid));
                    
                    server.addPlayerListener(client, player, true);

                    // load player data, player prefs from db or cache
                    List prefs = null;
                    List<DSGIgnoreData> ignoreData = null;
                    if (loginEvent.isGuest()) {
                    	playerData = new SimpleDSGPlayerData();
                    	playerData.setGuest(true);
                    	playerData.setLogins(1);
                    	playerData.setName(loginEvent.getPlayer());
                    	playerData.setPlayerID(5000 + guestId);//for ignores
                    	playerData.setPlayerType(DSGPlayerData.HUMAN);
                    	playerData.setStatus(DSGPlayerData.ACTIVE);
                    	playerData.setAdmin(false);
                    	playerData.setRegisterDate(new Date());
                    	playerData.setLastLoginDate(new Date());
                    	playerData.setLastUpdateDate(new Date());
                    	loginEvent.setMe(playerData);
                    	
                    	//empty
                    	prefs = new ArrayList();
                    	ignoreData = new ArrayList<DSGIgnoreData>();
                    }
                    else {
                    	try {
	                        playerData = dsgPlayerStorer.loadPlayer(player);
	                        prefs = dsgPlayerStorer.loadPlayerPreferences(
	                            playerData.getPlayerID());
	                        ignoreData = dsgPlayerStorer.getIgnoreData(playerData.getPlayerID());
	                        loginEvent.setMe(playerData);
	                    } catch (DSGPlayerStoreException e) {
	                        // should really do something else here since continuing
	                        // would be a bad thing
	                        log4j.error(psid() + "Problem loading player " + 
	                                    player + 
	                                    " in server player join.", e);
	                    }
                    }

                    // send preferences before logging in
                    for (Iterator it = prefs.iterator(); it.hasNext();) {
                        DSGPlayerPreference p = (DSGPlayerPreference) it.next();
                        client.eventOccurred(new DSGPreferenceEvent(p));
                    }
                    if (!ignoreData.isEmpty()) {
                    	DSGIgnoreData ignorePlayers[] = ignoreData.toArray(new DSGIgnoreData[ignoreData.size()]);
                    	client.eventOccurred(new DSGIgnoreEvent(0, ignorePlayers));
                    }
                    // send back info about server
                    loginEvent.setServerData(server.getServerData());
                    
    				// send back confirmation by echoing the login event
    				client.eventOccurred(loginEvent);
    				
    				// join the main room
                    eventOccurred(new DSGJoinMainRoomEvent(
                        player, playerData));

                    // send the list of possible computer opponents to the client
                    for (Iterator it = aiDataCollection.iterator(); it.hasNext();) {
                        log4j.debug(psid() + "sending ai data");
                        client.eventOccurred(new DSGAddAITableEvent(
                            player, 1, (AIData) it.next()));
                    }
    			}
    			else {
    				client.eventOccurred(new DSGLoginErrorEvent(
                        loginEvent.getPlayer(), "", 
                        DSGLoginErrorEvent.INVALID_LOGIN));
    			}
    		}
    	}
    	else {
    		if (dsgEvent instanceof DSGPingEvent) {
    			pingManager.receivePingEvent((DSGPingEvent) dsgEvent);
    		}
    		else if (dsgEvent instanceof DSGMainRoomEvent) {

				DSGMainRoomEvent mainRoomEvent = (DSGMainRoomEvent) dsgEvent;
				if (mainRoomEvent.getPlayer() == null) {
					mainRoomEvent.setPlayer(player);
				}

    			if (!player.equals(mainRoomEvent.getPlayer())) {
    				log4j.error(psid() + "Illegal access: " + player + " attempting access as " + mainRoomEvent.getPlayer());
    				//disconnect?
    			}
    			else {
	    			server.routeEventToMainRoom(dsgEvent);

	    			if (mainRoomEvent instanceof DSGExitMainRoomEvent) {
                        server.removePlayerListener(mainRoomEvent.getPlayer(), true);

                        // don't currently ban from playing, just logging here
                        activityLogger.exitPlayer(
                            new ActivityData(player, remoteAddr, sid));
                    }
    			}
    		}
    		else if (dsgEvent instanceof DSGTableEvent) {
    			
    			DSGTableEvent tableEvent = (DSGTableEvent) dsgEvent;
    			if (tableEvent.getPlayer() == null) {
    				tableEvent.setPlayer(player);
    			}
    			
    			int table = ((DSGTableEvent) dsgEvent).getTable();

				String eventPlayer = ((DSGTableEvent) dsgEvent).getPlayer();
				if (!player.equals(eventPlayer)) {
					log4j.error(psid() + "Illegal access: " + player + " attempting access as " + eventPlayer);
    				//disconnect?
				}
				else {					
                    server.routeEventToTable(dsgEvent, table);
				}
    		}
            else if (dsgEvent instanceof DSGServerStatsEvent) {

                log4j.info(psid() + "in: " + dsgEvent);
                dsgEventToPlayerRouter.routeEvent(
                    serverStatsHandler.handleServerStatsRequest(player),
                    player);
            }
            else if (dsgEvent instanceof DSGClientErrorEvent) {
                DSGClientErrorEvent errorEvent = (DSGClientErrorEvent)
                    dsgEvent;
                log4j.error(psid() + "in: " + dsgEvent, errorEvent.getThrowable());
            }
            else if (!playerData.isGuest() && dsgEvent instanceof DSGPreferenceEvent) {
                log4j.info(psid() + "in: " + dsgEvent);
                
                // store preference in this thread, probably not a big impact
                // on performance
                try {
                    DSGPlayerPreference pref = ((DSGPreferenceEvent) dsgEvent).
                        getPref();
                    dsgPlayerStorer.storePlayerPreference(
                        playerData.getPlayerID(), pref);

                } catch (DSGPlayerStoreException e) {
                    log4j.error(psid() + "Error storing preference", e);
                }
            }
            else if (!playerData.isGuest() && dsgEvent instanceof DSGIgnoreEvent) {
            	log4j.info(psid() + "in: " + dsgEvent);
                
                // store ignore in this thread, probably not a big impact
                // on performance
                try {
                	DSGIgnoreData in = ((DSGIgnoreEvent) dsgEvent).getPlayers()[0];
                	DSGIgnoreData exist = dsgPlayerStorer.getIgnoreData(
            			playerData.getPlayerID(), in.getIgnorePid());
            		if (exist != null) {
            			exist.setIgnoreChat(in.getIgnoreChat());
            			exist.setIgnoreInvite(in.getIgnoreInvite());
            			if (exist.getIgnoreChat() || exist.getIgnoreInvite()) {
            				dsgPlayerStorer.updateIgnore(exist);
            			}
            			else {
            				dsgPlayerStorer.deleteIgnore(exist);
            			}
            		}
            		else {
            			DSGIgnoreData ignore = new DSGIgnoreData();
            			ignore.setIgnoreInvite(in.getIgnoreInvite());
            			ignore.setIgnoreChat(in.getIgnoreChat());
            			ignore.setIgnorePid(in.getIgnorePid());
            			ignore.setPid(playerData.getPlayerID());
    	        		dsgPlayerStorer.insertIgnore(ignore);
            		}

                } catch (DSGPlayerStoreException e) {
                    log4j.error(psid() + "Error storing preference", e);
                }
            }
    	}
    }
}