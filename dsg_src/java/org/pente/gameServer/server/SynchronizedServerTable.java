/**
 * SynchronizedServerTable.java
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

import org.apache.log4j.Category;
import org.pente.game.GameStorer;
import org.pente.game.PlayerStorer;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.*;
import org.pente.kingOfTheHill.CacheKOTHStorer;

import java.util.Collection;


public class SynchronizedServerTable implements DSGEventListener {

    private static Category log4j =
            Category.getInstance(SynchronizedServerTable.class.getName());

    private long sid;

    // for ArenaServer
    public SynchronizedServerTable(ArenaServer server, Resources resources, int newTableNum, DSGEventToPlayerRouter dsgEventToPlayerRouter, CacheDSGPlayerStorer dsgPlayerStorer, PingManager pingManager, GameStorer fileGameStorer, GameStorer gameStorer, PlayerStorer playerStorer, ServerStatsHandler serverStatsHandler, MySQLDSGReturnEmailStorer returnEmailStorer, Collection<DSGPlayerData> mainRoomPlayers, ActivityLogger activityLogger, DSGArenaCreateTableEvent joinEvent) throws Throwable {
        sid = server.getServerData().getServerId();
        serverTable = new ArenaServerTable(
                server, resources, newTableNum, dsgEventToPlayerRouter, this, dsgPlayerStorer,
                pingManager, fileGameStorer, gameStorer, playerStorer,
                serverStatsHandler, returnEmailStorer, mainRoomPlayers,
                activityLogger, joinEvent);
        synchronizedQueue = new SynchronizedQueue();

        Runnable queueRunnable = () -> {
            while (running) {
                try {
                    callServerTable((DSGEvent) synchronizedQueue.remove());
                } catch (InterruptedException e) {
                }
            }
        };

        running = true;
        queueThread = new Thread(queueRunnable, "SynchronizedServerTable " + newTableNum);
        queueThread.start();
    }

    public ServerTable getServerTable() {
        return serverTable;
    }

    private ServerTable serverTable;
    private SynchronizedQueue synchronizedQueue;

    private Thread queueThread;
    private volatile boolean running;

    public SynchronizedServerTable() {
    }

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
            Collection<DSGPlayerData> playersInMainRoom,
            ActivityLogger activityLogger,
            DSGJoinTableEvent joinEvent,
            final CacheKOTHStorer kothStorer) throws Throwable {

        sid = server.getServerData().getServerId();
        if (server.getServerData().isTournament()) {
            serverTable = new TournamentServerTable(
                    server, resources, aiController, table, dsgEventRouter, this, dsgPlayerStorer,
                    pingManager, gameFileStorer, gameDbStorer, playerDbStorer,
                    serverStatsHandler, returnEmailStorer, playersInMainRoom,
                    activityLogger, joinEvent, kothStorer);
        } else {
            serverTable = new ServerTable(
                    server, resources, aiController, table, dsgEventRouter, this, dsgPlayerStorer,
                    pingManager, gameFileStorer, gameDbStorer, playerDbStorer,
                    serverStatsHandler, returnEmailStorer, playersInMainRoom,
                    activityLogger, joinEvent, kothStorer);
        }

        synchronizedQueue = new SynchronizedQueue();

        Runnable queueRunnable = () -> {
            while (running) {
                try {
                    callServerTable((DSGEvent) synchronizedQueue.remove());
                } catch (InterruptedException e) {
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

    private String psid() {
        return "[" + sid + "] ";
    }

    ;

    private void callServerTable(DSGEvent dsgEvent) {

        log4j.info(psid() + "in: " + dsgEvent);

        try {

            if (dsgEvent instanceof DSGMainRoomEvent mainRoomEvent) {

                if (mainRoomEvent instanceof DSGJoinMainRoomEvent) {
                    serverTable.handleMainRoomJoin((DSGJoinMainRoomEvent) mainRoomEvent);
                } else if (mainRoomEvent instanceof DSGExitMainRoomEvent) {
                    serverTable.handleMainRoomExit(mainRoomEvent.getPlayer());
                }
            } else if (dsgEvent instanceof DSGTableEvent e) {

                switch (e) {
                    case DSGJoinTableEvent dsgJoinTableEvent -> {
                        serverTable.handleJoin(dsgJoinTableEvent.getPlayer());
                    }
                    case DSGSitTableEvent dsgSitTableEvent -> {
                        serverTable.handleSit(dsgSitTableEvent.getPlayer(), dsgSitTableEvent.getSeat());
                    }
                    case DSGStandTableEvent dsgStandTableEvent -> {
                        serverTable.handleStand(dsgStandTableEvent.getPlayer());
                    }
                    case DSGTextTableEvent dsgTextTableEvent -> {
                        serverTable.handleText(dsgTextTableEvent.getPlayer(), dsgTextTableEvent.getText());
                    }
                    case DSGExitTableEvent dsgExitTableEvent -> {
                        serverTable.handleExit(dsgExitTableEvent.getPlayer(), dsgExitTableEvent.getForced());
                    }
                    case DSGChangeStateTableEvent dsgChangeStateTableEvent ->
                            serverTable.handleChangeState(dsgChangeStateTableEvent);
                    case DSGPlayTableEvent dsgPlayTableEvent -> serverTable.handleClickPlay(dsgPlayTableEvent);
                    case DSGMoveTableEvent dsgMoveTableEvent ->
                            serverTable.handleMove(dsgMoveTableEvent.getPlayer(), dsgMoveTableEvent.getMove());
                    case DSGTimeUpTableEvent dsgTimeUpTableEvent -> serverTable.handleTimeUp(dsgTimeUpTableEvent);
                    case DSGUndoRequestTableEvent dsgUndoRequestTableEvent ->
                            serverTable.handleUndoRequest(dsgUndoRequestTableEvent);
                    case DSGUndoReplyTableEvent dsgUndoReplyTableEvent ->
                            serverTable.handleUndoReply(dsgUndoReplyTableEvent);
                    case DSGResignTableEvent dsgResignTableEvent -> serverTable.handleResign(dsgResignTableEvent);
                    case DSGCancelRequestTableEvent dsgCancelRequestTableEvent ->
                            serverTable.handleCancelRequest(dsgCancelRequestTableEvent);
                    case DSGCancelReplyTableEvent dsgCancelReplyTableEvent ->
                            serverTable.handleCancelReply(dsgCancelReplyTableEvent);
                    case DSGWaitingPlayerReturnTimeUpTableEvent dsgWaitingPlayerReturnTimeUpTableEvent ->
                            serverTable.handleWaitingPlayerReturnTimeUp(dsgWaitingPlayerReturnTimeUpTableEvent);
                    case DSGForceCancelResignTableEvent dsgForceCancelResignTableEvent ->
                            serverTable.handleForceCancelResign(dsgForceCancelResignTableEvent);
                    case DSGEmailGameRequestTableEvent dsgEmailGameRequestTableEvent ->
                            serverTable.handleEmailGame(dsgEmailGameRequestTableEvent);
                    case DSGAddAITableEvent dsgAddAITableEvent -> serverTable.handleAddAI(dsgAddAITableEvent);
                    case DSGBootTableEvent dsgBootTableEvent -> serverTable.handleBoot(dsgBootTableEvent);
                    case DSGInviteTableEvent dsgInviteTableEvent -> serverTable.handleInvite(dsgInviteTableEvent);
                    case DSGInviteResponseTableEvent dsgInviteResponseTableEvent ->
                            serverTable.handleInviteResponse(dsgInviteResponseTableEvent);
                    case DSGSwapSeatsTableEvent dsgSwapSeatsTableEvent ->
                            serverTable.handleSwap(dsgSwapSeatsTableEvent);
                    case DSGRejectGoStateEvent dsgRejectGoStateEvent ->
                            serverTable.handleRejectGoState(dsgRejectGoStateEvent);
                    case DSGSwap2PassTableEvent dsgSwap2PassTableEvent ->
                            serverTable.handleSwap2Pass(dsgSwap2PassTableEvent);
                    case DSGArenaRequestJoinTableEvent dsgArenaRequestJoinTableEvent ->
                            serverTable.handleArenaRequestJoin(dsgArenaRequestJoinTableEvent);
                    case DSGArenaRejectTableJoinEvent dsgArenaRejectTableJoinEvent ->
                            serverTable.handleArenaRejectJoin(dsgArenaRejectTableJoinEvent);
                    case DSGArenaAcceptTableJoinEvent dsgArenaAcceptTableJoinEvent ->
                            serverTable.handleArenaAcceptJoin(dsgArenaAcceptTableJoinEvent);
                    default -> {
                    }
                }
            } else {
                log4j.info(psid() + "Illegal type of DSGEvent: " + dsgEvent.getClass().getName());
            }

        } catch (Throwable t) {
            log4j.error(psid() + "callServerTable()", t);
        }
    }
}

