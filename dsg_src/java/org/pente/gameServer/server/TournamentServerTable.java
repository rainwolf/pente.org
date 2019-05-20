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

    protected static Category log4j = Category.getInstance(ServerTable.class.getName());

    protected static final PGNGameFormat gameFormat = new PGNGameFormat();
    protected static final DateFormat dateFormat = new SimpleDateFormat("MM.dd.yyyy HH.mm.ss");

	protected static final int MAX_PLAYERS = 2;
	public static final int NO_ERROR = -1;
	protected static final String SYSTEM = "system_player";

	protected DSGPlayerData sittingPlayers[] = new DSGPlayerData[MAX_PLAYERS + 1];
	// playing players is set just for the game in case players
	// get kicked off we still know who was playing
	protected DSGPlayerData playingPlayers[] = new DSGPlayerData[MAX_PLAYERS + 1];
	protected boolean playerClickedPlay[] = new boolean[MAX_PLAYERS + 1];

	protected Vector playersInTable = new Vector();
	protected Vector playersInMainRoom;
    protected List<String> playersInvited = new ArrayList<String>();

    protected Map<String, Long> bootTimes = new HashMap<String, Long>();
    
    // keeps track of which ignores the person sending chat has been told about
    // so we only tell them once per table that their chat is being ignored
    protected Map<Long, Long> chatIgnoredMsg = new HashMap<Long, Long>();
    
	protected int state;
	protected int prevState;

	protected boolean timed;
	protected boolean rated;
	protected int	initialMinutes;
	protected int incrementalSeconds;
	protected GameTimer timers[];


    /** Move times keeps track of the timer times when players have finished
     *  with their turns.  Note that this doesn't always correspond to moves in
     *  a game.  In d-pente, the time is recorded when player 2 sends in the
     *  decision to swap or not in case after that decision the other player
     *  undo's and we need to get back to that time. In connect6 it is after 2
     *  moves.
     */  
  	protected List<Time> moveTimes = new ArrayList<Time>();
    protected Date gameTime;
    protected int tableType;

	protected Game game = GridStateFactory.PENTE_GAME;
	protected GridState gridState;

	protected GameData lastGame;
	
	protected boolean undoRequested;
	protected boolean cancelRequested;
	protected String cancelRequestedBy;
	
	protected int tableNum;

	/** This sequence number is needed for a very rare case that could occur.
	 *  1. player 1 exits in middle of game, waiting timer is activated
	 *  2. player 1 returns to table
	 *  3. player 1 exits table again right away
	 *  4. 1st waiting timer runs out and generates time up message
	 *     (this could happen as long as 2. and 3. hadn't processed
	 *      through the SynchronizedServerTable yet, so all 3 events
	 *      would be sitting in the queue.)
	 *  5. now the state of the table after 2. and 3. would be
	 *     waiting for a player to return and a new waiting timer
	 *     would be created. when 4. is processed it would see
	 *     that the state of the system was waiting and would
	 *     then send out the force resign / cancel WRONG!
	 *  6. by adding a sequence number the table can check
	 *     that the current sequence number matches the sequence
	 *     number given by the time up timer.
	 */
	protected int waitingForPlayerToReturnSeqNbr;
	protected static final int WAITING_FOR_PLAYER_TO_RETURN_TIMEOUT = 1;
	protected boolean waitingForPlayerToReturnTimeUp;
	protected GameTimer waitingForPlayerToReturnTimer;

	/** this represents the player was disconnected
	 *  in the middle of a set after game 1 and before game 2
	 *  the set timeout clock has started running, but if the other player later
	 *  gets disconnected, we want to give that player a full timeout clock as
	 *  well
	 */
	protected String disconnectedPlayer;
	/** if both players have been disconnected at different times, don't allow
	 *  any more timer rests
	 */
	protected boolean noMoreTimerResets;
	
	protected boolean gameStarted;
	
    protected Server server;
    protected ServerData serverData;
    protected long sid;
    protected ServerAIController aiController;
	protected DSGEventToPlayerRouter dsgEventRouter;
	protected DSGEventListener synchronizedTableListener;
    protected DSGPlayerStorer dsgPlayerStorer;
	protected PingManager pingManager;
    protected GameStorer gameFileStorer;
    protected GameStorer gameDbStorer;
    protected PlayerStorer playerDbStorer;
    protected ServerStatsHandler serverStatsHandler;
    protected MySQLDSGReturnEmailStorer returnEmailStorer;
    protected ActivityLogger activityLogger;
    
    protected Resources resources;
    protected TourneyMatch tourneyMatch;
    protected int tourneyCurrentRound;
    
    protected LiveSet set;
    
    protected Thread gameOverThread;
	protected EndGameRunnable gameOverRunnable;
    
	protected String creator;

    protected CacheKOTHStorer kothStorer;
    
    
    
    // new TournamentServerTable stuff
    protected static final int WAIT_TO_CLOSE_TABLE = 30;
    protected static final int WAIT_TO_PRESS_PLAY = 30;
    protected Timer closeTableTimer, pressPlayTimer;
    protected boolean tableCanClose = false;
    public void setTourneyMatch(TourneyMatch tourneyMatch) { this.tourneyMatch = tourneyMatch; }



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
        
        if (!tourneyMatch.hasBeenPlayed()) {
            determineAndUpdateForfeit();
        } else {
            TourneyMatch newMatch = null;
            try {
                newMatch = resources.getTourneyStorer().getUnplayedMatch(
                        tourneyMatch.getPlayer2().getPlayerID(), tourneyMatch.getPlayer1().getPlayerID(),
                        getGameEvent(game.getId()).getEventID());
                tourneyMatch = newMatch;
                if (tourneyMatch != null) {
                    determineAndUpdateForfeit();
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        super.destroy();
	}

	/** Just send out status of table to player
	 */
	@Override
	public void handleMainRoomJoin(DSGJoinMainRoomEvent mainRoomEvent) {
//        if (playersInTable != null && playersInTable.size() == 0) {
//            server.removeTable(tableNum);
//            return;
//        }
//        
//		playersInMainRoom.addElement(mainRoomEvent.getDSGPlayerData());
//		
//		String player = mainRoomEvent.getDSGPlayerData().getName();
//		sendPlayerList(player);
//		sendTableState(player);
//		sendSittingPlayers(player);
		super.handleMainRoomJoin(mainRoomEvent);
		if (tourneyMatch == null || tourneyMatch.hasBeenPlayed()) {
		    return;
        }
		if (tourneyMatch.getPlayer1().getPlayerID() == mainRoomEvent.getDSGPlayerData().getPlayerID() || 
                tourneyMatch.getPlayer2().getPlayerID() == mainRoomEvent.getDSGPlayerData().getPlayerID()) {
            handleJoin(mainRoomEvent.getPlayer());
        }
	}
	
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
	}


	@Override
	public void handleSit(String player, int seat) { }

	protected void sit(String player, int seat) {
		sittingPlayers[seat] = getPlayerInTable(player);

		broadcastMainRoom(new DSGSitTableEvent(player, tableNum, seat));

        if (sittingPlayers[1] != null && sittingPlayers[2] != null) {
            startPressPlayTimer();
        }
	}
	
	protected void startPressPlayTimer() {
        if (pressPlayTimer == null) {
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
	    int result = TourneyMatch.RESULT_UNFINISHED;
	    if ((sittingPlayers[1] != null && sittingPlayers[2] != null)) {
	        if (!this.playerClickedPlay[1] && !this.playerClickedPlay[2]) {
	            result = TourneyMatch.RESULT_DBL_FORFEIT;
            } else if (this.playerClickedPlay[1] && !this.playerClickedPlay[2]) {
	            result = TourneyMatch.RESULT_P1_WINS;
            } else if (this.playerClickedPlay[2] && !this.playerClickedPlay[1]) {
                result = TourneyMatch.RESULT_P2_WINS;
            }
        } else if ((sittingPlayers[1] == null && sittingPlayers[2] == null)) {
            result = TourneyMatch.RESULT_DBL_FORFEIT;
        } else if (sittingPlayers[1] == null) {
            result = TourneyMatch.RESULT_P2_WINS;
        } else if (sittingPlayers[2] == null) {
            result = TourneyMatch.RESULT_P1_WINS;
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
	public void handleStand(String player) { }

	@Override
    public void handleJoin(String player) {
	    super.handleJoin(player);
	    if (isPlayerInTable(player)) {
	        int i = 0;
	        if (tourneyMatch != null && tourneyMatch.getPlayer1().getName().equals(player)) {
	            i = 1;
            } else if (tourneyMatch != null && tourneyMatch.getPlayer1().getName().equals(player)) {
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
        }
	    super.startGame();
    }

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
        broadcastTable(new DSGSystemMessageTableEvent(
                tableNum,
                "this table will be automatically closed in "+WAIT_TO_CLOSE_TABLE+" seconds."));
        closeTableTimer = new Timer();
        closeTableTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                for (Object playerObject: playersInTable) {
                    DSGPlayerData player = (DSGPlayerData) playerObject;
                    if (player != null) {
                        exit(player.getName(), true);
                    }
                }
            }
        }, 1000L*WAIT_TO_CLOSE_TABLE);
    }
}
