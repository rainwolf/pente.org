/**
 * ArenaServerTable.java
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

import org.pente.game.*;
import org.pente.gameServer.client.GameTimer;
import org.pente.gameServer.client.MilliSecondGameTimer;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.gameServer.core.LiveSet;
import org.pente.gameServer.core.MySQLDSGReturnEmailStorer;
import org.pente.gameServer.event.*;

import java.util.*;

public class ArenaServerTable extends ServerTable {

    protected static final int WAIT_TO_CLOSE_TABLE = 60;
    protected static final int WAIT_TO_PRESS_PLAY = 5;
    protected Timer closeTableTimer, pressPlayTimer;

    protected boolean closingTable = false;

    protected int playAs = 1;

    protected ArenaJoinRequestRegistry joinRequests;
    /**
     * Serializes every publish with destroy(): startGame() publishes from the
     * pressPlayTimer thread, so without it a publish that passed the
     * destroyed check could land after tableRemoved and recreate the table.
     * Lock order: Server.tables -> publishLock -> registry (never reversed).
     */
    private final Object publishLock = new Object();
    /** Set by destroy() under publishLock; a destroyed table never publishes again. */
    protected boolean destroyed = false;

    /** Only for unit tests: an empty table without a server. */
    protected ArenaServerTable() {
    }

    public ArenaServerTable(final Server server,
                            final Resources resources,
                            final int tableNum,
                            final DSGEventToPlayerRouter dsgEventRouter,
                            final DSGEventListener synchronizedTableListener,
                            final DSGPlayerStorer dsgPlayerStorer,
                            final PingManager pingManager,
                            final GameStorer gameFileStorer,
                            final GameStorer gameDbStorer,
                            final PlayerStorer playerDbStorer,
                            final ServerStatsHandler serverStatsHandler,
                            final MySQLDSGReturnEmailStorer returnEmailStorer,
                            final Collection<DSGPlayerData> namesInMainRoom,
                            final ActivityLogger activityLogger,
                            DSGArenaCreateTableEvent joinEvent) {
        this.server = server;
        this.serverData = server.getServerData();
        sid = serverData.getServerId();
        this.resources = resources;
        this.tableNum = tableNum;
        this.dsgEventRouter = dsgEventRouter;
        this.synchronizedTableListener = synchronizedTableListener;
        this.dsgPlayerStorer = dsgPlayerStorer;
        this.pingManager = pingManager;
        this.gameFileStorer = gameFileStorer;
        this.gameDbStorer = gameDbStorer;
        this.playerDbStorer = playerDbStorer;
        this.serverStatsHandler = serverStatsHandler;
        this.returnEmailStorer = returnEmailStorer;
        this.activityLogger = activityLogger;
        this.creator = joinEvent.getPlayer();
        this.joinRequests = ((ArenaServer) server).getJoinRequestRegistry();

        this.playersInMainRoom = new Vector<>();
        playersInMainRoom.addAll(namesInMainRoom);
        startGameOverThread();
        resetTable(joinEvent);
    }


    public void destroy() {
        synchronized (publishLock) {
            destroyed = true;
            if (joinRequests != null) {
                joinRequests.tableRemoved(tableNum);   // R8
            }
        }
        if (closeTableTimer != null) {
            closeTableTimer.cancel();
            closeTableTimer.purge();
            closeTableTimer = null;
        }
        if (pressPlayTimer != null) {
            pressPlayTimer.cancel();
            pressPlayTimer.purge();
            pressPlayTimer = null;
        }

        super.destroy();
    }

    /**
     * Just send out status of table to player
     */
    @Override
    public void handleMainRoomJoin(DSGJoinMainRoomEvent mainRoomEvent) {
        if (closingTable) {
            return;
        }
        super.handleMainRoomJoin(mainRoomEvent);
        // if the player returns while their game was in progress, pull them into this table
        if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
            int seat = getPlayerSeatReturningToGame(mainRoomEvent.getPlayer());
            if (seat != NOT_PLAYING) {
                synchronizedTableListener.eventOccurred((new DSGJoinTableEvent(mainRoomEvent.getPlayer(), tableNum)));
            }
        }
    }

    @Override
    public void handleSit(String player, int seat) {
    }

    @Override
    protected void sit(String player, int seat) {
        sittingPlayers[seat] = getPlayerInTable(player);

        broadcastMainRoom(new DSGSitTableEvent(player, tableNum, seat));

        if (sittingPlayers[1] != null && sittingPlayers[2] != null) {
            startPressPlayTimer();
        }
    }

    protected void startPressPlayTimer() {
        if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
            return;
        }
        if (pressPlayTimer == null) {
            resetClickedPlays();
            pressPlayTimer = new Timer();
            pressPlayTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    pressPlayTimer.cancel();
                    pressPlayTimer.purge();
                    pressPlayTimer = null;
                    startGame();
                }
            }, 1000L * WAIT_TO_PRESS_PLAY);
            broadcastTable(new DSGSystemMessageTableEvent(
                    tableNum,
                    "Game starts in " + WAIT_TO_PRESS_PLAY + " seconds, or when both press play."));
        }
    }

    @Override
    public void handleJoin(String player) {
        // R6: a claimed join only lands if the requester is still available
        if (!joinRequests.admitJoin(tableNum, player, isPlayerInMainRoom(player))) {
            return;
        }
        try {
            super.handleJoin(player);
            if (isPlayerInTable(player)) {
                sitJoinedPlayer(player);
            }
        } finally {
            if (isPlayerInTable(player)) {
                // R7: joining any table ends the player's requests everywhere
                joinRequests.playerJoinedTable(tableNum, player);
            } else {
                // turned away after admitJoin (BOOTED, or super threw and
                // callServerTable swallowed it): never leave a claim stuck
                joinRequests.joinFailed(tableNum, player);
            }
            publishJoinRequestState();
        }
    }

    private void sitJoinedPlayer(String player) {
        if (!rated) {
            if (sittingPlayers[playAs] == null) {
                this.sit(player, playAs);
                return;
            }
        }
        if (this.sittingPlayers[1] == null) {
            this.sit(player, 1);
        } else if (this.sittingPlayers[2] == null) {
            this.sit(player, 2);
        }
    }

    @Override
    public void handleMainRoomExit(String player) {
        // R7: leaving the main room (or disconnecting) ends the player's requests
        joinRequests.requesterLeftMainRoom(player);
        super.handleMainRoomExit(player);
    }

    /** R6b/R10: tell the registry who sits here, who owns it, and whether a game runs. */
    protected void publishJoinRequestState() {
        synchronized (publishLock) {
            if (destroyed) {
                return;
            }
            // one atomic copy (Vector.toArray is synchronized): startGame() also
            // publishes from the pressPlayTimer thread while the pump may change
            // the list, and iterating the Vector itself could throw into startGame()
            List<DSGPlayerData> seated = new ArrayList<>(playersInTable);
            List<String> names = new ArrayList<>();
            String owner = null;
            for (DSGPlayerData d : seated) {
                if (d != null) {
                    names.add(d.getName());
                    if (owner == null && d.isHuman()) {
                        owner = d.getName();   // the ServerTable.getOwner() rule, on the copy
                    }
                }
            }
            joinRequests.publishTable(tableNum, owner, names,
                    state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS, rated);
        }
    }

    @Override
    protected void exit(String player, boolean booted) {
        super.exit(player, booted);
        publishJoinRequestState();   // player count and maybe owner changed (R6b)
    }

    @Override
    protected void changeGameState(int newState, String reason, String winner, int gameInSet) {
        super.changeGameState(newState, reason, winner, gameInSet);
        publishJoinRequestState();   // a table is only open with no game in progress (R6b)
    }

    @Override
    protected void swapSeats() {
        super.swapSeats();
        if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
            startPressPlayTimer();
        }
    }

    @Override
    protected void startGame() {
        if (this.pressPlayTimer != null) {
            this.pressPlayTimer.cancel();
            this.pressPlayTimer.purge();
            this.pressPlayTimer = null;
        }
        super.startGame();
    }

    @Override
    public void handleAddAI(DSGAddAITableEvent addEvent) {
    }

    @Override
    public void handleInvite(DSGInviteTableEvent inviteEvent) {
    }

    @Override
    public void handleInviteResponse(DSGInviteResponseTableEvent inviteResponseEvent) {
    }


//    protected void closeTableNow() {
//        closingTable = true;
//        List<DSGPlayerData> players = new ArrayList<>(playersInTable);
//        for (DSGPlayerData player : players) {
//            if (player != null) {
//                exit(player.getName(), true);
//                try {
//                    Thread.sleep(500);
//                } catch (Exception e) {
//                    log4j.error("Error removing " + player.getName() + " from table", e);
//                }
//            }
//        }
//    }

    @Override
    public void handleArenaRequestJoin(DSGArenaRequestJoinTableEvent dsgEvent) {
        String player = dsgEvent.getPlayer();
        // R2: a boot is this table's state, so it is checked here on the
        // table's own pump, before the registry sees the request. A reopen
        // (R6b) wipes registry memory but leaves bootTimes alone.
        if (isBooted(player)) {
            String owner = getOwner();
            dsgEventRouter.routeEvent(new DSGArenaRequestEndedEvent(player, tableNum,
                    owner == null ? "" : owner, DSGArenaRequestEndedEvent.BOOTED), player);
            joinRequests.sendMyRequests(player);   // R11 answer
            return;
        }
        joinRequests.request(tableNum, player, isPlayerInMainRoom(player));
    }

    /** The ServerTable.handleJoin boot rule (ServerTable.java:447-453), applied at request time. */
    private boolean isBooted(String player) {
        Long until = bootTimes.get(player);
        return until != null && System.currentTimeMillis() < until;
    }

    @Override
    public void handleArenaWithdrawJoin(DSGArenaWithdrawJoinRequestEvent dsgEvent) {
        joinRequests.withdraw(tableNum, dsgEvent.getPlayer());
    }

    @Override
    public void handleArenaRejectJoin(DSGArenaRejectTableJoinEvent dsgEvent) {
        joinRequests.decline(tableNum, dsgEvent.getPlayer(), dsgEvent.getPlayerToReject());
    }

    @Override
    public void handleArenaAcceptJoin(DSGArenaAcceptTableJoinEvent dsgEvent) {
        final String player = dsgEvent.getPlayerToAccept();
        // R5: only claims; the join is queued on this table's pump and re-checked when it lands (R6)
        joinRequests.accept(tableNum, dsgEvent.getPlayer(), player,
                () -> synchronizedTableListener.eventOccurred(new DSGJoinTableEvent(player, tableNum)));
    }


    protected void resetTable(DSGArenaCreateTableEvent joinEvent) {
        if (gridState != null) {
            gridState.clear();
        }

        playersInvited.clear();

        tableType = DSGChangeStateTableEvent.TABLE_TYPE_PUBLIC;

        initialMinutes = joinEvent.getInitialMinutes();
        incrementalSeconds = joinEvent.getIncrementalSeconds();
        game = GridStateFactory.getGame(joinEvent.getGame());
        // ToDo: allow untimed games?
        timed = joinEvent.isTimed();
        playAs = joinEvent.getPlayAs();

        boolean speed = timed && Game.isSpeedGame(initialMinutes, incrementalSeconds);
        if (speed && !game.isSpeed()) {
            game = GridStateFactory.getSpeedGame(game);
        } else if (!speed && game.isSpeed()) {
            game = GridStateFactory.getNormalGame(game);
        }

        rated = joinEvent.isRated();
        if (joinEvent.getPlayer().toLowerCase().startsWith("guest")) {
            rated = false;
        }
        tableType = DSGChangeStateTableEvent.TABLE_TYPE_PUBLIC;

        gameTime = null;
        prevState = state;
        state = DSGGameStateTableEvent.NO_GAME_IN_PROGRESS;

        // destroy old timers
        if (timers != null) {
            for (GameTimer timer : timers) {
                if (timer != null) {
                    timer.destroy();
                }
            }
        }
        if (waitingForPlayerToReturnTimer != null) {
            waitingForPlayerToReturnTimer.destroy();
        }
        noMoreTimerResets = false;
        disconnectedPlayer = null;

        // make new timers
        timers = new GameTimer[MAX_PLAYERS + 1];
        for (int i = 1; i < timers.length; i++) {
            timers[i] = new MilliSecondGameTimer("Table " + tableNum + " player " + i);
            timers[i].setStartMinutes(initialMinutes);
            if (initialMinutes == 0) {
                timers[i].setStartSeconds(incrementalSeconds);
            } else {
                timers[i].setStartSeconds(0);
            }
            final int tempPlayer = i;
            timers[i].addGameTimerListener((minutes, seconds) -> {
                if (minutes <= 0 && seconds <= 0) {
                    synchronizedTableListener.eventOccurred(
                            new DSGTimeUpTableEvent(playingPlayers[tempPlayer].getName(), tableNum));
                }
            });
        }

        broadcastMainRoom(getTableState());
        // send the current owner to the joining player
        dsgEventRouter.routeEvent(
                new DSGOwnerTableEvent(getOwner(), tableNum),
                joinEvent.getPlayer());
    }


    public void handleStand(String player) {

        int error = NO_ERROR;
        if (!isPlayerInTable(player)) {
            error = DSGTableErrorEvent.NOT_IN_TABLE;
        } else {
            int seat = getPlayerSeat(player);
            if (seat == NOT_SITTING) {
                error = DSGTableErrorEvent.NOT_SITTING;
            } else if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
                error = DSGTableErrorEvent.GAME_IN_PROGRESS;
            } else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
                error = DSGTableErrorEvent.WAIT_GAME_TWO_OF_SET;
            } else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
                error = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
            } else {
                stand(player, seat);
            }
        }

        if (error != NO_ERROR) {
            dsgEventRouter.routeEvent(
                    new DSGStandTableErrorEvent(player, tableNum, error),
                    player);
        } else {
            exit(player, false);
        }
    }

    @Override
    protected void updateDatabaseAfterGameOver
            (GameData gameData, GameData fileGameData, String winnerPlayer, String loserPlayer,
             int game, int localWinner, LiveSet localSet) {

        super.updateDatabaseAfterGameOver(gameData, fileGameData, winnerPlayer, loserPlayer, game, localWinner, localSet);
        if (rated && set != null && !set.isComplete()) {
            startPressPlayTimer();
        }
    }

    public void handleClickPlay(DSGPlayTableEvent playEvent) {
        String player = playEvent.getPlayer();
        int error = NO_ERROR;
        if (!isPlayerInTable(player)) {
            error = DSGTableErrorEvent.NOT_IN_TABLE;
        }
        // if one player is disconnected after game 1 and other player clicks
        // play
        else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET &&
                !allPlayersSitting()) {

            int seat = getPlayerSeat(player);
            playerClickedPlay[seat] = true;

            // echo play event back to client
            dsgEventRouter.routeEvent(playEvent, player);
        } else if (!allPlayersSitting()) {
            error = DSGTableErrorEvent.NOT_ALL_PLAYERS_SITTING;
        } else if (rated && getPlayerInTable(player).isGuest()) {
            error = DSGTableErrorEvent.GUEST_NOT_ALLOWED;

            dsgEventRouter.routeEvent(
                    new DSGSystemMessageTableEvent(
                            tableNum, "Guests are not allowed to play rated games, play unrated or create a free user account!"),
                    player);
        }
        int seat = getPlayerSeat(player);

        if (seat == NOT_SITTING) {
            error = DSGTableErrorEvent.NOT_SITTING;
        } else if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
            error = DSGTableErrorEvent.GAME_IN_PROGRESS;
        } else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
            error = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
        } else if (playerClickedPlay[seat]) {
            //just ignore multiple clicks, keeps client simpler
            //error = DSGTableErrorEvent.PLAY_ALREADY_CLICKED;
        } else {
            playerClickedPlay[seat] = true;

            if (allPlayersClickedPlay()) {
                // echo play event back to client
                dsgEventRouter.routeEvent(playEvent, player);
                startGame();
            } else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {

                // echo play event back to client
                dsgEventRouter.routeEvent(playEvent, player);

                String dp = null;
                for (int i = 1; i < sittingPlayers.length; i++) {
                    if (!sittingPlayers[i].getName().equals(player)) {
                        dp = sittingPlayers[i].getName();
                        break;
                    }
                }
                startSetTimeOut(dp);
            }
        }

        if (error != NO_ERROR) {
            dsgEventRouter.routeEvent(
                    new DSGPlayTableErrorEvent(player, tableNum, error),
                    player);
        }
    }

}
