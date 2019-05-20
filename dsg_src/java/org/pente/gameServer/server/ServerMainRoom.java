/** ServerMainRoom.java
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

import org.pente.gameServer.core.*;
import org.pente.gameServer.event.*;
import org.pente.gameServer.tourney.*;

public class ServerMainRoom {

    protected static Category log4j =
        Category.getInstance(ServerMainRoom.class.getName());

    protected Server server;
    protected Resources resources;
    protected DSGEventToPlayerRouter dsgEventRouter;
    protected CacheDSGPlayerStorer dsgPlayerStorer;

    protected Map<String, DSGPlayerData> playersInMainRoom = new HashMap<String, DSGPlayerData>(30);

    // keeps track of which ignores the person sending chat has been told about
    // so we only tell them once per table that their chat is being ignored
    protected Map<Long, Long> chatIgnoredMsg = new HashMap<Long, Long>();
    
	public ServerMainRoom(
        Server server,
        Resources resources,
        DSGEventToPlayerRouter dsgEventRouter) throws Throwable {
        
        this.server = server;
        this.resources = resources;
		this.dsgEventRouter = dsgEventRouter;
        this.dsgPlayerStorer = (CacheDSGPlayerStorer) resources.getDsgPlayerStorer();
	}

    public Collection getPlayersInMainRoom() {
        return playersInMainRoom.values();
    }

    public boolean isPlayerInMainRoom(String player) {
		return playersInMainRoom.containsKey(player);
	}

    
    
	public void broadcast(DSGEvent dsgEvent) {

		for (Iterator i = playersInMainRoom.values().iterator(); i.hasNext();) {
			DSGPlayerData d = (DSGPlayerData) i.next();
            dsgEventRouter.routeEvent(dsgEvent, d.getName());
		}
	}

	public void handleText(String player, String text) {
		
		if (!isPlayerInMainRoom(player)) {
			dsgEventRouter.routeEvent(
				new DSGTextMainRoomErrorEvent(player, text, DSGMainRoomErrorEvent.NOT_IN_MAIN_ROOM),
				player);
		}
		else {
			// instead broadcast, check each player to see if ignoring 1st
			// could try to implement this on client we'll see how slow this is
			DSGEvent event = new DSGTextMainRoomEvent(player, text);
			DSGPlayerData actor = (DSGPlayerData) playersInMainRoom.get(player);

			for (Iterator i = playersInMainRoom.values().iterator(); i.hasNext();) {
				DSGPlayerData d = (DSGPlayerData) i.next();
	            DSGIgnoreData ig = null;
	            try {
	            	ig = dsgPlayerStorer.getIgnoreData(d.getPlayerID(), actor.getPlayerID());
	            } catch (DSGPlayerStoreException dpse) {
	            	log4j.error("Error getting ignore data, assume no ignore", dpse);
	            }

	            if (ig == null || !ig.getIgnoreChat()) {
	            	dsgEventRouter.routeEvent(event, d.getName());
	            }
	            else {

	            	// lookup ig.id in map
	            	// if not found, send msg to player that chat is ignored
	            	if (ig != null && chatIgnoredMsg.get(ig.getIgnoreId()) == null) {
	            		dsgEventRouter.routeEvent(
	            			new DSGSystemMessageTableEvent(0,
	            				d.getName() + " is ignoring your chat."),
	            			actor.getName());
	            		chatIgnoredMsg.put(ig.getIgnoreId(), ig.getIgnoreId());
	            	}
	            	log4j.debug("Ignore chat for player " + d.getName());
	            }
			}
		}
	}

	public void handleJoin(DSGJoinMainRoomEvent joinEvent) {
		
		// this shouldn't happen because ServerPlayer checks before logging in a player
		if (isPlayerInMainRoom(joinEvent.getPlayer())) {
			dsgEventRouter.routeEvent(
				new DSGJoinMainRoomErrorEvent(joinEvent.getPlayer(),
                    DSGMainRoomErrorEvent.ALREADY_IN_MAIN_ROOM),
				joinEvent.getPlayer());
		}
		else {
            //try {
                //DSGPlayerData joinPlayerData =
                //    dsgPlayerStorer.loadPlayer(joinEvent.getPlayer());

                playersInMainRoom.put(joinEvent.getPlayer(), joinEvent.getDSGPlayerData());

                sendPlayerList(joinEvent.getPlayer());
                //joinEvent.setDSGPlayerData(joinEvent.getDSGPlayerData());
                broadcast(joinEvent);

                // send join to all tables AFTER processing locally
                // this ensures dsgplayerdata is sent to client BEFORE
                // any tables send out join table events
                server.routeEventToAllTables(joinEvent);

//            } catch (DSGPlayerStoreException e) {
//                log4j.error("Problem loading player " + 
//                            joinEvent.getPlayer() + 
//                            " on main room join.", e);
//                dsgEventRouter.routeEvent(
//                    new DSGJoinMainRoomErrorEvent(joinEvent.getPlayer(),
//                        DSGMainRoomErrorEvent.UNKNOWN),
//                    joinEvent.getPlayer());
//            }
		}

	}
					
	protected void sendPlayerList(String toPlayer) {
		for (Iterator i = playersInMainRoom.values().iterator(); i.hasNext();) {
			DSGPlayerData d = (DSGPlayerData) i.next();
			if (!toPlayer.equals(d.getName())) {
				dsgEventRouter.routeEvent(
					new DSGJoinMainRoomEvent(d.getName(), d),
					toPlayer);
			}
		}
	}
	
	public void handleExit(DSGExitMainRoomEvent exitEvent) {
		
		if (isPlayerInMainRoom(exitEvent.getPlayer())) {

			playersInMainRoom.remove(exitEvent.getPlayer());

            server.routeEventToAllTables(exitEvent);

			broadcast(new DSGExitMainRoomEvent(exitEvent.getPlayer(), exitEvent.wasBooted()));
		}
	}
    
    public void handleUpdatePlayerData(DSGUpdatePlayerDataEvent updateEvent) {
        DSGPlayerData d = updateEvent.getDSGPlayerData();
        log4j.debug("handleUpdatePlayerData()");
        if (isPlayerInMainRoom(d.getName())) {
            // if player has deleted themselves, remove player from table
            if (!d.isActive()) {
                log4j.debug("Remove player " + d.getName() + " from server, deleted themselves");
                //there is no way to BOOT a player from server yet
                //so leave player logged in, shouldn't be a big deal
                //handleExit(new DSGExitMainRoomEvent(
                //    updateEvent.getDSGPlayerData().getName()));
            } else {
                log4j.debug("Send out updated player data for " + d.getName());

                playersInMainRoom.put(d.getName(), d);
                broadcast(updateEvent);
            }
        }
    }
    public void handleIgnoreEvent(DSGIgnoreEvent event) {

    	for (String s : playersInMainRoom.keySet()) {
    		DSGPlayerData d = playersInMainRoom.get(s);
    		if (d.getPlayerID() == event.getPid()) {
    			
    	    	// load the new ignore data for the player
    	    	try {
    	    		List<DSGIgnoreData> data = dsgPlayerStorer.getIgnoreData(event.getPid());
    	    		if (!data.isEmpty()) {
    	    			event.setPlayers((DSGIgnoreData[]) data.toArray(new DSGIgnoreData[data.size()]));
    	    		}
    	    	} catch (DSGPlayerStoreException dpse) {
    	    		log4j.error("Error sending updated ignore data", dpse);
    	    	}
				dsgEventRouter.routeEvent(event, s);
				
				break;
    		}
    	}
    }
    
    public void handleSystemMessage(DSGSystemMessageTableEvent messageEvent) {
        broadcast(messageEvent);
    }
    
    public void handleBoot(String player, final String toBoot, final int minutes) {
    	DSGPlayerData d = (DSGPlayerData) playersInMainRoom.get(player);
    	if (d == null || !d.isAdmin()) {
    		//ignore invalid boots for now
    		return;
    	}
    	DSGPlayerData b = (DSGPlayerData) playersInMainRoom.get(toBoot);
    	if (b == null) {
    		// ignore boots for non logged in players
    		return;
    	}
    	
    	// send exit event to everyone w/ boot=true
    	// and exit from all tables
    	handleExit(new DSGExitMainRoomEvent(toBoot, true));
    	
    	// handleexit doesn't send to player being removed, so send it also
    	dsgEventRouter.routeEvent(new DSGExitMainRoomEvent(toBoot, 
    		true), toBoot);

    	// close out socket connection for the player in server
    	// remove player from routing
    	server.bootPlayer(toBoot, minutes);
    }

    public void handleTourneyEvent(TourneyEvent event) {
        Tourney tourney = server.getTourney();
        if (tourney == null || event.getEid() != tourney.getEventID()) return;
        if (!tourney.isSpeed()) return;
        
        if (event.getType() == TourneyEvent.NEW_ROUND) {
            // if a round is complete, print out results
            if (tourney.getNumRounds() > 1) {
                int lastRound = tourney.getNumRounds() - 1;
                broadcast(new DSGSystemMessageTableEvent(0,
                    "Round " + lastRound + " complete"));
                TourneyRound completedRound = tourney.getRound(lastRound);
                for (Iterator it = completedRound.getMatchStrings().iterator(); it.hasNext();) {
                    String s = (String) it.next();
                    broadcast(new DSGSystemMessageTableEvent(0, s)); 
                }
            }
            broadcast(new DSGSystemMessageTableEvent(0,
                    "Round " + tourney.getNumRounds() + " matchups"));
            for (Iterator it = tourney.getLastRound().getMatchStrings().iterator();
                 it.hasNext();) {
                String s = (String) it.next();
                broadcast(new DSGSystemMessageTableEvent(0, s)); 
            }
        }
        else if (event.getType() == TourneyEvent.COMPLETE) {
            
            int lastRound = tourney.getNumRounds();
            broadcast(new DSGSystemMessageTableEvent(0,
                "Round " + lastRound + " complete"));
            TourneyRound completedRound = tourney.getRound(lastRound);
            for (Iterator it = completedRound.getMatchStrings().iterator(); it.hasNext();) {
                String s = (String) it.next();
                broadcast(new DSGSystemMessageTableEvent(0, s)); 
            }
            
            // this won't work if there IS NO winner...double-forfeit
            String winner = ((TourneyPlayerData) tourney.getLastRound().getSection(1).getWinners().get(0)).getName();
            if (winner != null) {
                // refresh player data to show crown
                try {
                    dsgPlayerStorer.refreshPlayer(winner);
                    DSGPlayerData w =
                        dsgPlayerStorer.loadPlayer(winner);
                    playersInMainRoom.put(winner, w);

                    dsgPlayerStorer.notifyListeners(w);
                    
                } catch (DSGPlayerStoreException dpse) {
                    log4j.error("Error refreshing winner.", dpse);
                }

                broadcast(new DSGSystemMessageTableEvent(0,
                    "Tournament complete, " + winner + " wins!"));
            }
        }
        else if (event.getType() == TourneyEvent.PLAYER_REGISTER) {
            try {
                long pid = ((Long) event.getData()).longValue();
                DSGPlayerData p = resources.getDsgPlayerStorer().loadPlayer(pid);
                broadcast(new DSGSystemMessageTableEvent(0,
                    p.getName() + " has registered for the tournament."));
                
            } catch (DSGPlayerStoreException dpse) {
                log4j.error(dpse);
            }
        }
        else if (event.getType() == TourneyEvent.PLAYER_DROP) {
            try {
                long pid = ((Long) event.getData()).longValue();
                DSGPlayerData p = resources.getDsgPlayerStorer().loadPlayer(pid);
                broadcast(new DSGSystemMessageTableEvent(0,
                    p.getName() + " has been dropped from the tournament."));
                
            } catch (DSGPlayerStoreException dpse) {
                log4j.error(dpse);
            }
        }
    }
}