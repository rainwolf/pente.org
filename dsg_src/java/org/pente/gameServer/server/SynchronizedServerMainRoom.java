/** SynchronizedServerMainRoom.java
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
import org.pente.gameServer.tourney.*;
import org.pente.gameServer.core.*;

public class SynchronizedServerMainRoom
    implements DSGEventListener, PlayerDataChangeListener, TourneyListener,
    IgnoreDataChangeListener {

    private static Category log4j = Category.getInstance(
        SynchronizedServerMainRoom.class.getName());

    private long sid;
	private ServerMainRoom 		serverMainRoom;
	private SynchronizedQueue 	synchronizedQueue;
    private Resources           resources;

	private Thread				queueThread;
	private volatile boolean	running;

	public SynchronizedServerMainRoom(
        Server server,
        Resources resources,
        DSGEventToPlayerRouter dsgEventRouter) throws Throwable {

        this.resources = resources;
        
        sid = server.getServerData().getServerId();
        if (server.getServerData().isTournament()) {
            serverMainRoom = new TournamentServerMainRoom(server, resources, dsgEventRouter);
        } else {
            serverMainRoom = new ServerMainRoom(server, resources, dsgEventRouter);
        }

        resources.getTourneyStorer().addTourneyListener(this);
        
		synchronizedQueue = new SynchronizedQueue();

		Runnable queueRunnable = new Runnable() {
			public void run() {
				while (running) {	
					try {
						callServerMainRoom((DSGEvent) synchronizedQueue.remove());
					} catch (InterruptedException e) {
					}
				}
			}
		};

		running = true;
		queueThread = new Thread(queueRunnable, "SynchronizedServerMainRoom");
		queueThread.start();
	}

	public void eventOccurred(DSGEvent dsgEvent) {
		synchronizedQueue.add(dsgEvent);
	}
    public void playerChanged(DSGPlayerData dsgPlayerData) {
        synchronizedQueue.add(new DSGUpdatePlayerDataEvent(dsgPlayerData));
    }

    public void tourneyEventOccurred(TourneyEvent event) {
        synchronizedQueue.add(event);
    }
    public void ignoreDataChanged(long pid) {
    	synchronizedQueue.add(new DSGIgnoreEvent(pid, null));
    }

	public void destroy() {
		running = false;
		if (queueThread != null) {
			queueThread.interrupt();
		}
        if (resources.getTourneyStorer() != null) {
            resources.getTourneyStorer().removeTourneyListener(this);
        }
	}

    /** synchronized to control access to players in main room */
    public synchronized Collection getPlayersInMainRoom() {
        return serverMainRoom.getPlayersInMainRoom();
    }

    private String psid() { return "[" + sid + "] "; };
    
    /** synchronized to control access to getPlayersInMainRoom */
	private synchronized void callServerMainRoom(DSGEvent dsgEvent) {
		
		log4j.info(psid() + "in: " + dsgEvent);
		
		try {

			if (dsgEvent instanceof DSGMainRoomEvent) {
				DSGMainRoomEvent mainRoomEvent = (DSGMainRoomEvent) dsgEvent;
				
				if (mainRoomEvent instanceof DSGJoinMainRoomEvent) {
					DSGJoinMainRoomEvent joinEvent = (DSGJoinMainRoomEvent) dsgEvent;
					serverMainRoom.handleJoin(joinEvent);
				}
				else if (mainRoomEvent instanceof DSGExitMainRoomEvent) {
                    DSGExitMainRoomEvent exitEvent = (DSGExitMainRoomEvent) dsgEvent;
					serverMainRoom.handleExit(exitEvent);
				}
				else if (mainRoomEvent instanceof DSGTextMainRoomEvent) {
					DSGTextMainRoomEvent textEvent = (DSGTextMainRoomEvent) mainRoomEvent;
					serverMainRoom.handleText(textEvent.getPlayer(), textEvent.getText());
				}
				else if (mainRoomEvent instanceof DSGBootMainRoomEvent) {
					DSGBootMainRoomEvent bootEvent = (DSGBootMainRoomEvent) mainRoomEvent;
					serverMainRoom.handleBoot(bootEvent.getPlayer(), 
						bootEvent.getPlayerToBoot(), bootEvent.getBootMinutes());
				}
				else {
					log4j.info(psid() + "Illegal type of DSGEvent: " + dsgEvent.getClass().getName());
				}
			}
            else if (dsgEvent instanceof DSGUpdatePlayerDataEvent) {
                serverMainRoom.handleUpdatePlayerData(
                    (DSGUpdatePlayerDataEvent) dsgEvent);
            }
            else if (dsgEvent instanceof DSGIgnoreEvent) {
            	serverMainRoom.handleIgnoreEvent((DSGIgnoreEvent) dsgEvent);
            }
            else if (dsgEvent instanceof DSGSystemMessageTableEvent) {
                serverMainRoom.handleSystemMessage(
                    (DSGSystemMessageTableEvent) dsgEvent);
            }
            else if (dsgEvent instanceof TourneyEvent) {
                serverMainRoom.handleTourneyEvent((TourneyEvent) dsgEvent);
            }
			else {
				log4j.info(psid() + "Illegal type of DSGEvent: " + dsgEvent.getClass().getName());
			}
			
		} catch (Throwable t) {
			log4j.error(psid() + "callServerMainRoom()", t);
		}
	}
}