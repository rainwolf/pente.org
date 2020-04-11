/** ServerTable.java
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
import java.text.*;

import org.apache.log4j.*;

import org.pente.game.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.*;
import org.pente.gameServer.tourney.*;

import org.pente.kingOfTheHill.*;

public class TournamentServerTable extends ServerTable {

    // new TournamentServerTable stuff
    protected static final int WAIT_TO_CLOSE_TABLE = 60;
    protected static final int WAIT_TO_PRESS_PLAY = 30;
    protected Timer closeTableTimer, pressPlayTimer;
    public void setTourneyMatch(TourneyMatch tourneyMatch) { this.tourneyMatch = tourneyMatch; }
    protected Long forfeitPid;
    protected boolean closingTable = false;


    public TournamentServerTable(final Server server,
                       final Resources resources,
                       final ServerAIController aiController,
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
                       final Collection namesInMainRoom,
                       final ActivityLogger activityLogger,
                       DSGJoinTableEvent joinEvent, 
                       final CacheKOTHStorer kothStorer) throws Throwable {

	    super(server, resources, aiController, tableNum, dsgEventRouter, synchronizedTableListener, dsgPlayerStorer, pingManager, gameFileStorer, gameDbStorer, playerDbStorer, serverStatsHandler, returnEmailStorer, namesInMainRoom, activityLogger, joinEvent, kothStorer);

//        this.server = server;
//        this.serverData = server.getServerData();
//        sid = serverData.getServerId();
//        this.resources = resources;
//        this.aiController = aiController;
//		this.tableNum = tableNum;
//		this.dsgEventRouter = dsgEventRouter;
//		this.synchronizedTableListener = synchronizedTableListener;
//        this.dsgPlayerStorer = dsgPlayerStorer;
//		this.pingManager = pingManager;
//        this.gameFileStorer = gameFileStorer;
//        this.gameDbStorer = gameDbStorer;
//        this.playerDbStorer = playerDbStorer;
//        this.serverStatsHandler = serverStatsHandler;
//        this.returnEmailStorer = returnEmailStorer;
//        this.activityLogger = activityLogger;
//        this.creator = joinEvent.getPlayer();
//
//        this.kothStorer = kothStorer;
//
//        this.playersInMainRoom = new Vector();
//        for (Iterator it = namesInMainRoom.iterator(); it.hasNext();) {
//            DSGPlayerData d = (DSGPlayerData) it.next();
//            playersInMainRoom.add(d);
//        }
//        startGameOverThread();
//        resetTable(joinEvent);
	}

	
	public void destroy() {
        
        if (closeTableTimer != null) {
            closeTableTimer.cancel();
        }
        if (pressPlayTimer != null) {
            pressPlayTimer.cancel();
        }
        
        super.destroy();
	}

	/** Just send out status of table to player
	 */
	@Override
	public void handleMainRoomJoin(DSGJoinMainRoomEvent mainRoomEvent) {
	    if (closingTable) {
	        return;
        }
		super.handleMainRoomJoin(mainRoomEvent);
		if (tourneyMatch == null || tourneyMatch.hasBeenPlayed()) {
		    return;
        }
		if (tourneyMatch.getPlayer1().getPlayerID() == mainRoomEvent.getDSGPlayerData().getPlayerID() || 
                tourneyMatch.getPlayer2().getPlayerID() == mainRoomEvent.getDSGPlayerData().getPlayerID()) {
            handleJoin(mainRoomEvent.getPlayer());
        }
	}
	
	@Override
	public void handleMainRoomExit(String player) {
        for (Iterator it = playersInMainRoom.iterator(); it.hasNext();) {
            DSGPlayerData data = (DSGPlayerData) it.next();
            if (data.getName().equals(player)) {
                it.remove();
                break;
            }
        }

		if (isPlayerInTable(player)) {
            handleExit(player, true);
		}
		
		if (sittingPlayers[1] == null && sittingPlayers[2] == null) {
		    if (pressPlayTimer != null) {
		        pressPlayTimer.cancel();
		        pressPlayTimer = null;
            }
		    closeTableNow();
        }
	}


	@Override
	public void handleSit(String player, int seat) { }

	@Override
	protected void sit(String player, int seat) {
		sittingPlayers[seat] = getPlayerInTable(player);

		broadcastMainRoom(new DSGSitTableEvent(player, tableNum, seat));

        if (sittingPlayers[1] != null && sittingPlayers[2] != null) {
            startPressPlayTimer();
        }
	}
	
	protected void startPressPlayTimer() {
        if (pressPlayTimer == null) {
            resetClickedPlays();
            pressPlayTimer = new Timer();
            pressPlayTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    determineAndUpdateForfeit();
                }
            }, 1000L*WAIT_TO_PRESS_PLAY);
            broadcastTable(new DSGSystemMessageTableEvent(
                    tableNum,
                    "You have "+WAIT_TO_PRESS_PLAY+" seconds to press play, otherwise you forfeit this match."));
        }
    }
    
    protected void determineAndUpdateForfeit() {
	    if (tourneyMatch == null) {
	        return;
        }
	    if (waitingForPlayerToReturnTimer != null) {
            waitingForPlayerToReturnTimer.stop();
            waitingForPlayerToReturnTimer.destroy();
            waitingForPlayerToReturnTimer = null;
        }

	    int result = TourneyMatch.RESULT_DBL_FORFEIT;
	    if (forfeitPid == null) {
            if ((sittingPlayers[1] != null && sittingPlayers[2] != null)) {
                if (this.playerClickedPlay[1] && !this.playerClickedPlay[2]) {
                    forfeitPid = sittingPlayers[2].getPlayerID();
                } else if (this.playerClickedPlay[2] && !this.playerClickedPlay[1]) {
                    forfeitPid = sittingPlayers[1].getPlayerID();
                }
            } else if (sittingPlayers[1] == null && sittingPlayers[2] != null) {
                forfeitPid = sittingPlayers[2].getPlayerID();
            } else if (sittingPlayers[2] == null && sittingPlayers[1] != null) {
                forfeitPid = sittingPlayers[1].getPlayerID();
            }
        }
	    if (forfeitPid != null) {
            if (forfeitPid == tourneyMatch.getPlayer1().getPlayerID()) {
                result = TourneyMatch.RESULT_P2_WINS;
                broadcastTable(new DSGSystemMessageTableEvent(
                        tableNum,
                        "Game over, forfeit by "+tourneyMatch.getPlayer1().getName()+"."));
            } else if (forfeitPid == tourneyMatch.getPlayer2().getPlayerID()) {
                result = TourneyMatch.RESULT_P1_WINS;
                broadcastTable(new DSGSystemMessageTableEvent(
                        tableNum,
                        "Game over, forfeit by "+tourneyMatch.getPlayer2().getName()+"."));
            }
        } else {
            broadcastTable(new DSGSystemMessageTableEvent(
                    tableNum,
                    "Game over, double forfeit."));
        }
        
        tourneyMatch.setForfeit(true);
	    tourneyMatch.setResult(result);
        try {
            resources.getTourneyStorer().updateMatch(tourneyMatch);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        TourneyMatch newMatch = null;
        try {
            newMatch = resources.getTourneyStorer().getUnplayedMatch(
                    tourneyMatch.getPlayer2().getPlayerID(), tourneyMatch.getPlayer1().getPlayerID(),
                    getGameEvent(game.getId()).getEventID());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        tourneyMatch = newMatch;
        if (tourneyMatch != null) {
            determineAndUpdateForfeit();
        } else {
            closeTable();
        }
    }
    
//    protected void swapSeatsAndPlays() {
//	    swapSeats();
//	    boolean tmp = playerClickedPlay[1];
//	    playerClickedPlay[1] = playerClickedPlay[2];
//	    playerClickedPlay[2] = tmp;
//    }
    
    @Override
	protected void stand(String player, int seat) {
		sittingPlayers[seat] = null;

		// if waiting for 2nd game and player disconnected
		// then don't want to reset the other player's click
		if (prevState == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET &&
			state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
			playerClickedPlay[seat] = false;
		}
		else {
			resetClickedPlays();
		}

		broadcastMainRoom(new DSGStandTableEvent(player, tableNum));
	}

	@Override
    public void handleJoin(String player) {
	    super.handleJoin(player);
	    if (isPlayerInTable(player)) {
	        int i = 0;
	        if (tourneyMatch != null && tourneyMatch.getPlayer1().getName().equals(player)) {
	            i = 1;
            } else if (tourneyMatch != null && tourneyMatch.getPlayer2().getName().equals(player)) {
	            i = 2;
            }
            if (i > 0) {
                if (this.sittingPlayers[i] == null) {
                    sit(player, i);
                } else if (!this.sittingPlayers[i].getName().equals(player)) {
                    stand(this.sittingPlayers[i].getName(), i);
                    sit(player, i);
                }
            }
        }
    }
    
    @Override
    protected void startGame() {
	    if (this.pressPlayTimer != null) {
	        this.pressPlayTimer.cancel();
	        this.pressPlayTimer = null;
        }
	    super.startGame();
    }

    @Override
    public void handleStand(String player) { }
    @Override
	public void handleCancelRequest(DSGCancelRequestTableEvent cancelRequestEvent) { }
    @Override
	public void handleCancelReply(DSGCancelReplyTableEvent cancelReplyEvent) { }
    @Override
	protected void cancelGame(String setStatus) { }
    @Override
    public void handleAddAI(DSGAddAITableEvent addEvent) { }

    @Override
    public void handleBoot(DSGBootTableEvent bootEvent) {
        DSGPlayerData booter = getPlayerInTable(bootEvent.getPlayer());
        DSGPlayerData bootee = getPlayerInTable(bootEvent.getPlayerToBoot());
        if (booter != null && bootee != null) {
            int seat = getPlayerSeat(bootEvent.getPlayerToBoot());
            if (seat == NOT_SITTING) {
                super.handleBoot(bootEvent);
            }
        }
    }

    @Override
    public void handleInvite(DSGInviteTableEvent inviteEvent) { }
    @Override
    public void handleInviteResponse(DSGInviteResponseTableEvent inviteResponseEvent) { }

    
    
    /** I suppose its possible that if a player finished 2 games at near
     *  the same time, one games stats updates could override the others
     */
    @Override
    protected void updateDatabaseAfterGameOver
    (GameData gameData, String winnerPlayer, String loserPlayer,
     int game, int localWinner, LiveSet localSet) {

        try {
            super.updateDatabaseAfterGameOver(gameData, winnerPlayer, loserPlayer, game, localWinner, localSet);
            long newPid1 = sittingPlayers[1].getPlayerID();
            long newPid2 = sittingPlayers[2].getPlayerID();
            TourneyMatch newMatch = resources.getTourneyStorer().getUnplayedMatch(
                    newPid1, newPid2,
                    getGameEvent(game).getEventID());
            if (newMatch == null) {
                closeTable();
            } else {
                tourneyMatch = newMatch;
                startPressPlayTimer();
            }

        } catch (Throwable t) {
            log4j.error("Error updating tourney match for " +
                    gameData.getGameID(), t);
        }
    }
    
    protected void closeTable() {
        closingTable = true;
        broadcastTable(new DSGSystemMessageTableEvent(
                tableNum,
                "this table will be automatically closed in "+WAIT_TO_CLOSE_TABLE+" seconds.\nYou will not be matched as long as you are seated, use this for a short break if needed."));
        closeTableTimer = new Timer();
        closeTableTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                closeTableNow();
            }
        }, 1000L*WAIT_TO_CLOSE_TABLE);
    }
    
    protected void closeTableNow() {
        closingTable = true;
        List<DSGPlayerData> players = new ArrayList<>(playersInTable);
        for (Object playerObject: players) {
            DSGPlayerData player = (DSGPlayerData) playerObject;
            if (player != null) {
                exit(player.getName(), true);
                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                    log4j.error("Error removing " + player.getName() + " from table", e);
                }
            }
        }
    }
}
