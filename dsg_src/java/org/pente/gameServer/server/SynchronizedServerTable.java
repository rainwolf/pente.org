/** SynchronizedServerTable.java
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

import org.pente.game.*;
import org.pente.gameServer.event.*;
import org.pente.gameServer.core.*;

import org.pente.kingOfTheHill.*;


public class SynchronizedServerTable implements DSGEventListener {

    private static Category log4j =
        Category.getInstance(SynchronizedServerTable.class.getName());
	
    private long sid;
	private ServerTable 		serverTable;
	private SynchronizedQueue 	synchronizedQueue;

	private Thread				queueThread;
	private volatile boolean	running;

	public SynchronizedServerTable(
        Server server,
        Resources resources,
        ServerAIController aiController, 
		int table,
		DSGEventToPlayerRouter dsgEventRouter,
        DSGPlayerStorer dsgPlayerStorer,
		PingManager pingManager,
        GameStorer gameFileStorer,
        GameStorer gameDbStorer,
        PlayerStorer playerDbStorer,
        ServerStatsHandler serverStatsHandler,
        MySQLDSGReturnEmailStorer returnEmailStorer,
        Collection playersInMainRoom,
        ActivityLogger activityLogger,
        DSGJoinTableEvent joinEvent,
        final CacheKOTHStorer kothStorer) throws Throwable {

        sid = server.getServerData().getServerId();
		serverTable = new ServerTable(
            server, resources, aiController, table, dsgEventRouter, this, dsgPlayerStorer,
            pingManager, gameFileStorer, gameDbStorer, playerDbStorer,
            serverStatsHandler, returnEmailStorer, playersInMainRoom,
            activityLogger, joinEvent, kothStorer);
		
		synchronizedQueue = new SynchronizedQueue();
		
		Runnable queueRunnable = new Runnable() {
			public void run() {
				while (running) {	
					try {
						callServerTable((DSGEvent) synchronizedQueue.remove());
					} catch (InterruptedException e) {
					}
				}
			}
		};

		running = true;
		queueThread = new Thread(queueRunnable, "SynchronizedServerTable " + table);
		queueThread.start();
	}

	public void eventOccurred(DSGEvent dsgEvent) {
		synchronizedQueue.add(dsgEvent);
	}

	public void destroy() {
		running = false;
		if (queueThread != null) {
			queueThread.interrupt();
			queueThread = null;
		}
		serverTable.destroy();
	}

    private String psid() { return "[" + sid + "] "; };
    
	private void callServerTable(DSGEvent dsgEvent) {

		log4j.info(psid() + "in: " + dsgEvent);

		try {

			if (dsgEvent instanceof DSGMainRoomEvent) {
				DSGMainRoomEvent mainRoomEvent = (DSGMainRoomEvent) dsgEvent;
				
				if (mainRoomEvent instanceof DSGJoinMainRoomEvent) {
					serverTable.handleMainRoomJoin((DSGJoinMainRoomEvent) mainRoomEvent);
				}
				else if (mainRoomEvent instanceof DSGExitMainRoomEvent) {
					serverTable.handleMainRoomExit(mainRoomEvent.getPlayer());
				}
			}
			else if (dsgEvent instanceof DSGTableEvent) {

				DSGTableEvent e = (DSGTableEvent) dsgEvent;
				
				if (dsgEvent instanceof DSGJoinTableEvent) {
					DSGJoinTableEvent j = (DSGJoinTableEvent) e;
					serverTable.handleJoin(j.getPlayer());
				}
				else if (dsgEvent instanceof DSGSitTableEvent) {
					DSGSitTableEvent s = (DSGSitTableEvent) e;
					serverTable.handleSit(s.getPlayer(), s.getSeat());
				}
				else if (dsgEvent instanceof DSGStandTableEvent) {
					DSGStandTableEvent s = (DSGStandTableEvent) e;
					serverTable.handleStand(s.getPlayer());
				}
				else if (dsgEvent instanceof DSGTextTableEvent) {
					DSGTextTableEvent t = (DSGTextTableEvent) e;
					serverTable.handleText(t.getPlayer(), t.getText());
				}
				else if (dsgEvent instanceof DSGExitTableEvent) {
					DSGExitTableEvent x = (DSGExitTableEvent) e;
					serverTable.handleExit(x.getPlayer(), x.getForced());
				}
				else if (dsgEvent instanceof DSGChangeStateTableEvent) {
					serverTable.handleChangeState((DSGChangeStateTableEvent) dsgEvent);
				}
				else if (dsgEvent instanceof DSGPlayTableEvent) {
					serverTable.handleClickPlay((DSGPlayTableEvent) dsgEvent);
				}
				else if (dsgEvent instanceof DSGMoveTableEvent) {
					DSGMoveTableEvent moveEvent = (DSGMoveTableEvent) dsgEvent;
					serverTable.handleMove(moveEvent.getPlayer(), moveEvent.getMove());
				}
                else if (dsgEvent instanceof DSGTimeUpTableEvent) {
                    serverTable.handleTimeUp((DSGTimeUpTableEvent) dsgEvent);   
                }
				else if (dsgEvent instanceof DSGUndoRequestTableEvent) {
					serverTable.handleUndoRequest((DSGUndoRequestTableEvent) dsgEvent);
				}
				else if (dsgEvent instanceof DSGUndoReplyTableEvent) {
					serverTable.handleUndoReply((DSGUndoReplyTableEvent) dsgEvent);
				}
				else if (dsgEvent instanceof DSGResignTableEvent) {
					serverTable.handleResign((DSGResignTableEvent) dsgEvent);
				}
				else if (dsgEvent instanceof DSGCancelRequestTableEvent) {
					serverTable.handleCancelRequest((DSGCancelRequestTableEvent) dsgEvent);
				}
				else if (dsgEvent instanceof DSGCancelReplyTableEvent) {
					serverTable.handleCancelReply((DSGCancelReplyTableEvent) dsgEvent);
				}
				else if (dsgEvent instanceof DSGWaitingPlayerReturnTimeUpTableEvent) {
					serverTable.handleWaitingPlayerReturnTimeUp((DSGWaitingPlayerReturnTimeUpTableEvent) dsgEvent);
				}
				else if (dsgEvent instanceof DSGForceCancelResignTableEvent) {
					serverTable.handleForceCancelResign((DSGForceCancelResignTableEvent) dsgEvent);
				}
                else if (dsgEvent instanceof DSGEmailGameRequestTableEvent) {
                    serverTable.handleEmailGame((DSGEmailGameRequestTableEvent) dsgEvent);
                }
                else if (dsgEvent instanceof DSGAddAITableEvent) {
                    serverTable.handleAddAI((DSGAddAITableEvent) dsgEvent);
                }
                else if (dsgEvent instanceof DSGBootTableEvent) {
                    serverTable.handleBoot((DSGBootTableEvent) dsgEvent);
                }
                else if (dsgEvent instanceof DSGInviteTableEvent) {
                    serverTable.handleInvite((DSGInviteTableEvent) dsgEvent);
                }
                else if (dsgEvent instanceof DSGInviteResponseTableEvent) {
                    serverTable.handleInviteResponse((DSGInviteResponseTableEvent) dsgEvent);
                }
                else if (dsgEvent instanceof DSGSwapSeatsTableEvent) {
                    serverTable.handleSwap((DSGSwapSeatsTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGRejectGoStateEvent) {
                        serverTable.handleRejectGoState((DSGRejectGoStateEvent) dsgEvent);
                }
			}
			else {
				log4j.info(psid() + "Illegal type of DSGEvent: " + dsgEvent.getClass().getName());
			}
			
		} catch (Throwable t) {
			log4j.error(psid() + "callServerTable()", t);
		}
	}
}

