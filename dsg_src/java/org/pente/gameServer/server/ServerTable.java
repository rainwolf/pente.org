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
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBSet;
import org.pente.turnBased.TBStoreException;

import org.pente.kingOfTheHill.*;

public class ServerTable {

    private static Category log4j = Category.getInstance(ServerTable.class.getName());

    private static final PGNGameFormat gameFormat = new PGNGameFormat();
    private static final DateFormat dateFormat = new SimpleDateFormat("MM.dd.yyyy HH.mm.ss");

	private static final int MAX_PLAYERS = 2;
	public static final int NO_ERROR = -1;
	private static final String SYSTEM = "system_player";

	private DSGPlayerData sittingPlayers[] = new DSGPlayerData[MAX_PLAYERS + 1];
	// playing players is set just for the game in case players
	// get kicked off we still know who was playing
	private DSGPlayerData playingPlayers[] = new DSGPlayerData[MAX_PLAYERS + 1];
	private boolean playerClickedPlay[] = new boolean[MAX_PLAYERS + 1];

	private Vector playersInTable = new Vector();
	private Vector playersInMainRoom;
    private List<String> playersInvited = new ArrayList<String>();

    private Map<String, Long> bootTimes = new HashMap<String, Long>();
    
    // keeps track of which ignores the person sending chat has been told about
    // so we only tell them once per table that their chat is being ignored
    private Map<Long, Long> chatIgnoredMsg = new HashMap<Long, Long>();
    
	private int state;
	private int prevState;

	private boolean timed;
	private boolean rated;
	private int	initialMinutes;
	private int incrementalSeconds;
	private GameTimer timers[];


    /** Move times keeps track of the timer times when players have finished
     *  with their turns.  Note that this doesn't always correspond to moves in
     *  a game.  In d-pente, the time is recorded when player 2 sends in the
     *  decision to swap or not in case after that decision the other player
     *  undo's and we need to get back to that time. In connect6 it is after 2
     *  moves.
     */  
  	private List<Time> moveTimes = new ArrayList<Time>();
    private Date gameTime;
    private int tableType;

	private Game game = GridStateFactory.PENTE_GAME;
	private GridState gridState;

	private GameData lastGame;
	
	private boolean undoRequested;
	private boolean cancelRequested;
	private String cancelRequestedBy;
	
	private int tableNum;

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
	private int waitingForPlayerToReturnSeqNbr;
	private static final int WAITING_FOR_PLAYER_TO_RETURN_TIMEOUT = 7;
	private boolean waitingForPlayerToReturnTimeUp;
	private GameTimer waitingForPlayerToReturnTimer;

	/** this represents the player was disconnected
	 *  in the middle of a set after game 1 and before game 2
	 *  the set timeout clock has started running, but if the other player later
	 *  gets disconnected, we want to give that player a full timeout clock as
	 *  well
	 */
	private String disconnectedPlayer;
	/** if both players have been disconnected at different times, don't allow
	 *  any more timer rests
	 */
	private boolean noMoreTimerResets;
	
	private boolean gameStarted;
	
    private Server server;
    private ServerData serverData;
    private long sid;
    private ServerAIController aiController;
	private DSGEventToPlayerRouter dsgEventRouter;
	private DSGEventListener synchronizedTableListener;
    private DSGPlayerStorer dsgPlayerStorer;
	private PingManager pingManager;
    private GameStorer gameFileStorer;
    private GameStorer gameDbStorer;
    private PlayerStorer playerDbStorer;
    private ServerStatsHandler serverStatsHandler;
    private MySQLDSGReturnEmailStorer returnEmailStorer;
    private ActivityLogger activityLogger;
    
    private Resources resources;
    private TourneyMatch tourneyMatch;
    private int tourneyCurrentRound;
    
    private LiveSet set;
    
    private Thread gameOverThread;
	private EndGameRunnable gameOverRunnable;
    
	private String creator;

    private CacheKOTHStorer kothStorer;
	
	public ServerTable(final Server server,
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

        this.server = server;
        this.serverData = server.getServerData();
        sid = serverData.getServerId();
        this.resources = resources;
        this.aiController = aiController;
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

        this.kothStorer = kothStorer;

        this.playersInMainRoom = new Vector();
        for (Iterator it = namesInMainRoom.iterator(); it.hasNext();) {
            DSGPlayerData d = (DSGPlayerData) it.next();
            playersInMainRoom.add(d);
        }
        startGameOverThread();
        resetTable(joinEvent);
	}

	private void startGameOverThread() {
		if (gameOverRunnable == null) {
	        gameOverRunnable = new EndGameRunnable();
		}
		if (gameOverThread == null || !gameOverThread.isAlive()) {
			gameOverRunnable.reset();
			gameOverThread = new Thread(gameOverRunnable);
	        gameOverThread.start();
		}
	}
	
	public void destroy() {
        
        removeAllComputers();

        // destroy old timers
        if (timers != null) {
            for (int i = 0; i < timers.length; i++) {
                if (timers[i] != null) {
                    timers[i].destroy();
                }
            }
        }
		if (waitingForPlayerToReturnTimer != null) {
			waitingForPlayerToReturnTimer.destroy();
		}
        
		if (gameOverThread != null && gameOverRunnable != null) {
			gameOverRunnable.kill();
			gameOverThread.interrupt();
		}
	}

    private String psid() { return "[" + sid + "] "; };
    
    private void resetTable(DSGJoinTableEvent joinEvent) {

        if (gridState != null) {
            gridState.clear();
        }

        playersInvited.clear();
        
        timed = true;
        rated = false;
        tableType = DSGChangeStateTableEvent.TABLE_TYPE_PUBLIC;
        if (serverData.isTournament()) {
            Tourney tourney = server.getTourney();
            initialMinutes = tourney.getInitialTime();
            incrementalSeconds = tourney.getIncrementalTime();
            game = GridStateFactory.getGame(tourney.getGame());
            rated = true;
        }
        else {
            boolean fromPref = false;
            DSGPlayerData creator = getPlayerInMainRoom(joinEvent.getPlayer());
            if (creator != null) {
                try {
                    List prefs = dsgPlayerStorer.loadPlayerPreferences(
                        creator.getPlayerID());
                    if (prefs != null) {
                        for (int i = 0; i < prefs.size(); i++) {
                            DSGPlayerPreference pref = (DSGPlayerPreference) prefs.get(i);
                            if (pref.getName().equals("gameState")) {
                                DSGChangeStateTableEvent e = (DSGChangeStateTableEvent) pref.getValue();
                                initialMinutes = e.getInitialMinutes();
                                incrementalSeconds = e.getIncrementalSeconds();
                                game = GridStateFactory.getGame(e.getGame());
                                rated = e.getRated();
                                timed = e.getTimed();
                                tableType = e.getTableType();
                                fromPref = true;
                                break;
                            }
                        }
                    }
                } catch (Throwable t) {
                    log4j.debug("error resetting table", t);
                }
            }
            if (!fromPref) {
                initialMinutes = 10;
                incrementalSeconds = 5;
                game = GridStateFactory.PENTE_GAME;
            }
        }
        gameTime = null;
        prevState = state;
        state = DSGGameStateTableEvent.NO_GAME_IN_PROGRESS;

		// destroy old timers
		if (timers != null) {
			for (int i = 0; i < timers.length; i++) {
			    if (timers[i] != null) {
			        timers[i].destroy();
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
            final int tempPlayer = i;
            timers[i].addGameTimerListener(new GameTimerListener() {
                public void timeChanged(int minutes, int seconds) {
                    if (minutes <= 0 && seconds <= 0) {
                        synchronizedTableListener.eventOccurred(
                            new DSGTimeUpTableEvent(playingPlayers[tempPlayer].getName(), tableNum));
                    }
                }
            });
        }
        
        broadcastMainRoom(getTableState());
    }

	private void broadcastTable(DSGEvent dsgEvent) {
		
		for (int i = 0; i < playersInTable.size(); i++) {
            String name = ((DSGPlayerData) playersInTable.elementAt(i)).getName();
			dsgEventRouter.routeEvent(dsgEvent, name);
		}
	}

	private void broadcastMainRoom(DSGEvent dsgEvent) {
		for (int i = 0; i < playersInMainRoom.size(); i++) {
            String name = ((DSGPlayerData) playersInMainRoom.elementAt(i)).getName();
			dsgEventRouter.routeEvent(dsgEvent, name);
		}
	}

	public void handleText(String player, String text) {
		
		if (!isPlayerInTable(player)) {
			dsgEventRouter.routeEvent(
				new DSGTextTableErrorEvent(player, tableNum, text, DSGTableErrorEvent.NOT_IN_TABLE),
				player);
		}
		else {
			// instead broadcast, check each player to see if ignoring 1st
			// could try to implement this on client we'll see how slow this is
			DSGEvent event = new DSGTextTableEvent(player, tableNum, text);
			DSGPlayerData actor = getPlayerInMainRoom(player);
			for (int i = 0; i < playersInTable.size(); i++) {
	            DSGPlayerData d = ((DSGPlayerData) playersInTable.elementAt(i));
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
	            			new DSGSystemMessageTableEvent(tableNum,
	            				d.getName() + " is ignoring your chat."),
	            			actor.getName());
	            		chatIgnoredMsg.put(ig.getIgnoreId(), ig.getIgnoreId());
	            	}
	            	log4j.debug("Ignore chat for player " + d.getName());
	            }
			}
		}
	}

	public void handleJoin(String player) {

        DSGPlayerData joinPlayerData = getPlayerInTable(player);
        if (joinPlayerData != null) {
            dsgEventRouter.routeEvent(
                new DSGJoinTableErrorEvent(player, tableNum, DSGJoinTableErrorEvent.ALREADY_IN_TABLE), 
                player);
            return;
        }
        joinPlayerData = getPlayerInMainRoom(player);
        
        // determine if the player was kicked while playing and is now returning
        boolean returningPlayer = false;
        if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
            int seat = getPlayerSeatReturningToGame(player);
            if (seat != NOT_PLAYING) {
                returningPlayer = true;
            }
        }

        // if table is private and
        // player isn't an admin and
        // player wasn't invited and
        // player is not returning to ongoing game
        // don't allow access
        if (joinPlayerData != null && !joinPlayerData.isAdmin() && 
            tableType == DSGChangeStateTableEvent.TABLE_TYPE_PRIVATE &&
            !playersInvited.contains(player) && 
            !returningPlayer &&
            !player.equals(creator)) {
            
            dsgEventRouter.routeEvent(
                new DSGJoinTableErrorEvent(player, tableNum, DSGJoinTableErrorEvent.PRIVATE_TABLE),
                player);
        }
        // player was booted and can't return for 5 minutes
        else if (bootTimes.get(player) != null &&
        	System.currentTimeMillis() < bootTimes.get(player)) {

            dsgEventRouter.routeEvent(
                new DSGJoinTableErrorEvent(player, tableNum, DSGJoinTableErrorEvent.BOOTED),
                player);
        }
		else {
            playersInvited.remove(player);
			playersInTable.addElement(joinPlayerData);

			broadcastMainRoom(new DSGJoinTableEvent(player, tableNum));

			sendTableState(player);
            
            if (state != DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
                sendPlayingPlayers(player);
            }

			boolean restartedGame = false;
			// check if player was playing game and got kicked off
			if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
				int seat = getPlayerSeatReturningToGame(player);
				if (seat != NOT_PLAYING) {

					sit(player, seat);

					// reset requests in case player dropped out
					// after request but before response
					undoRequested = false;
					cancelRequested = false;
					cancelRequestedBy = null;
					waitingForPlayerToReturnTimeUp = false;
					
					if (prevState == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
						changeGameState(DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET,
							"set can now restart", 1);

						long timeLeft = 0;
						if (waitingForPlayerToReturnTimer != null) {
							timeLeft = (waitingForPlayerToReturnTimer.getMinutes() * 60 +
							    waitingForPlayerToReturnTimer.getSeconds()) * 1000;
						}
						dsgEventRouter.routeEvent(
							new DSGStartSetTimerEvent(player, tableNum, timeLeft), player);
						
						//TODO, timers?
						//player still has to click play?
						//what if player DID click play, then got disconnected
						//before other player clicked play (other player still needs to click play then)
					}
					else if (allPlayersSitting()) {

						if (waitingForPlayerToReturnTimer != null) {
							waitingForPlayerToReturnTimer.stop();
							waitingForPlayerToReturnTimer.destroy();
							waitingForPlayerToReturnTimer = null;
						}

						restartedGame = true;

                        if (timed) {
						  timers[gridState.getCurrentPlayer()].go();
                        }
                        
						changeGameState(DSGGameStateTableEvent.GAME_IN_PROGRESS, "game restarted", getGameInSet());
					}
				}
			}
			
			if (!restartedGame) {
				sendGameState(player);
			}


            // if playing d-pente at table, send swap decision 1st, before
            // any moves made (currently this is ok because it's a "silent" swap             
			if ((game == GridStateFactory.DPENTE_GAME || game == GridStateFactory.SPEED_DPENTE_GAME ||
					game == GridStateFactory.DKERYO_GAME || game == GridStateFactory.SPEED_DKERYO_GAME) &&
					gridState != null &&
                ((PenteState) gridState).wasDPenteSwapDecisionMade()) {
                dsgEventRouter.routeEvent(
                    new DSGSwapSeatsTableEvent(null, tableNum,
                    ((PenteState) gridState).didDPenteSwap(), true),
                    player);
            }

			sendMoves(player);

			if (timed && (state == DSGGameStateTableEvent.GAME_IN_PROGRESS || 
				          state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN)) {
				sendTimers(player);
			}

			// send the current owner to the joining player
			dsgEventRouter.routeEvent(
				new DSGOwnerTableEvent(getOwner(), tableNum),
				player);
			
			creator = null;
		}
	}

	private void sendGameState(String toPlayer) {

		String stateMessage = null;
		if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
			stateMessage = "game in progress";
		}
		else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
			stateMessage = "waiting for game 2 of a set to start";
		}
		else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
			String txt = prevState == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET ? "set" : "game";
			stateMessage = txt + " in progress, waiting for player to return";
		}
		
		dsgEventRouter.routeEvent(
			new DSGGameStateTableEvent(toPlayer, tableNum, state, stateMessage,  getGameInSet()),
			toPlayer);
	}

	private void sendMoves(String toPlayer) {
		if (gridState != null) {
            dsgEventRouter.routeEvent(
                new DSGMoveTableEvent(tableNum, gridState.getMoves()),
					toPlayer);
		}
	}

	private void sendTimers(String toPlayer) {
		for (int i = 1; i < timers.length; i++) {
			dsgEventRouter.routeEvent(
				new DSGTimerChangeTableEvent(
					playingPlayers[i].getName(), tableNum, 
					timers[i].getMinutes(),
					timers[i].getSeconds()),
				toPlayer);
		}
	}

	private void stopTimers() {
		for (int i = 1; i < timers.length; i++) {
			timers[i].stop();
		}
	}

	/** Just send out status of table to player
	 */
	public void handleMainRoomJoin(DSGJoinMainRoomEvent mainRoomEvent) {

//        DSGPlayerData joinPlayerData = null;
//        try {
//            joinPlayerData = dsgPlayerStorer.loadPlayer(player);
//        } catch (DSGPlayerStoreException e) {
//            log4j.error(psid() + "Problem loading player " + 
//                        player + 
//                        " on main room join.", e);
//            return;
//        }
		playersInMainRoom.addElement(mainRoomEvent.getDSGPlayerData());
		
		String player = mainRoomEvent.getDSGPlayerData().getName();
		sendPlayerList(player);
		sendTableState(player);
		sendSittingPlayers(player);
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

	private void sendPlayerList(String toPlayer) {
		for (int i = 0; i < playersInTable.size(); i++) {
            DSGPlayerData data = (DSGPlayerData) playersInTable.elementAt(i);
            if (data == null) {
            	continue;
			}
			dsgEventRouter.routeEvent(
				new DSGJoinTableEvent(data.getName(), tableNum),
				toPlayer);
		}
	}
    private DSGChangeStateTableEvent getTableState() {

        DSGChangeStateTableEvent changeStateEvent = new DSGChangeStateTableEvent("system", tableNum);
        changeStateEvent.setGame(game.getId());
        changeStateEvent.setInitialMinutes(initialMinutes);
        changeStateEvent.setIncrementalSeconds(incrementalSeconds);
        changeStateEvent.setTimed(timed);
        changeStateEvent.setRated(rated);
        changeStateEvent.setTableType(tableType);
        
        return changeStateEvent;
    }
	private void sendTableState(String toPlayer) {
		dsgEventRouter.routeEvent(getTableState(), toPlayer);
	}
	private void sendSittingPlayers(String toPlayer) {
		for (int i = 1; i < sittingPlayers.length; i++) {
			if (sittingPlayers[i] != null) {
				dsgEventRouter.routeEvent(
					new DSGSitTableEvent(sittingPlayers[i].getName(), tableNum, i),
					toPlayer);
			}
		}
	}
	

	private static final int NOT_PLAYING = -1;
	private int getPlayerSeatReturningToGame(String player) {

		for (int i = 1; i < playingPlayers.length; i++) {
			if (playingPlayers[i].getName().equals(player)) {
				return prevState == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET 
					? 3 - i : i;
			}
		}
		return NOT_PLAYING;
	}
	private String getPlayerNameReturningToGame() {
		for (int i = 1; i < playingPlayers.length; i++) {
			if (sittingPlayers[i] == null) {
				return playingPlayers[i].getName();
			}
		}
		return null;
	}

	private boolean isPlayerInTable(String player) {
        return getPlayerInTable(player) != null;
	}
    private boolean isPlayerInMainRoom(String player) {
        return getPlayerInMainRoom(player) != null;
    }
	private static final int NOT_SITTING = -1;
	private int getPlayerSeat(String player) {
		for (int i = 1; i < sittingPlayers.length; i++) {
			if (sittingPlayers[i] != null && sittingPlayers[i].getName().equals(player)) {
				return i;
			}
		}
		return NOT_SITTING;
	}
	private int getPlayingPlayerSeat(String player) {
		for (int i = 1; i < playingPlayers.length; i++) {
			if (playingPlayers[i] != null && playingPlayers[i].getName().equals(player)) {
				return i;
			}
		}
		return NOT_SITTING;
	}
	
	private void resetClickedPlays() {
		for (int i = 1; i < playerClickedPlay.length; i++) {
			unClickPlay(i);
		}
	}
	private void unClickPlay(int seat) {
		// send out click play error so client resets its clickedplay
		// flag and displays the correct board message
		if (playerClickedPlay[seat] && sittingPlayers[seat] != null) {
			dsgEventRouter.routeEvent(
				new DSGPlayTableErrorEvent(sittingPlayers[seat].getName(), tableNum, 
					DSGTableErrorEvent.NOT_ALL_PLAYERS_SITTING), 
					sittingPlayers[seat].getName());
		}
		playerClickedPlay[seat] = false;
	}

	public void handleChangeState(DSGChangeStateTableEvent changeStateEvent) {

		int status = NO_ERROR;
        DSGPlayerData actor = getPlayerInTable(changeStateEvent.getPlayer());        
		if (actor == null) {
			status = DSGTableErrorEvent.NOT_IN_TABLE;
		}
		else if (!actor.isAdmin() && !isPlayerOwner(changeStateEvent.getPlayer())) {
			status = DSGTableErrorEvent.NOT_TABLE_OWNER;
		}
        // if only changed table type, don't reset anything else
        else if (changeStateEvent.getTableType() != tableType) {
            tableType = changeStateEvent.getTableType();
            broadcastMainRoom(changeStateEvent);
        }
		else if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
			status = DSGTableErrorEvent.GAME_IN_PROGRESS;
		}
		else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
			status = DSGTableErrorEvent.WAIT_GAME_TWO_OF_SET;
		}
		else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
			status = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
		}
        else if (anyComputersSitting() &&
                 (changeStateEvent.getRated() ||
                  changeStateEvent.getTimed()))
        {
            status = DSGTableErrorEvent.COMPUTER_SITTING;
        }
        else if (serverData.isTournament()) {
            status = DSGTableErrorEvent.TOURNAMENT_GAME;
        }
		else {
            
			int minutes = changeStateEvent.getInitialMinutes();
			int seconds = changeStateEvent.getIncrementalSeconds();
			if (minutes < 0 || minutes > 999) {
				status = DSGTableErrorEvent.UNKNOWN;
			}
			else if (seconds < 0 || seconds > 59) {
				status = DSGTableErrorEvent.UNKNOWN;
			}
			else {
				
				resetClickedPlays();
				
				initialMinutes = minutes;
				incrementalSeconds = seconds;
				timed = changeStateEvent.getTimed();
                rated = changeStateEvent.getRated();
                for (int i = 1; i < timers.length; i++) {
                    timers[i].setStartMinutes(initialMinutes);
                }
                // get game for changeStateEvent.getGame()
                // check if speed game, take into account timed!='N'
                // convert game for changeStateEvent possibly
                // then check if game is different than current
                Game newGame = GridStateFactory.getGame(changeStateEvent.getGame());
                log4j.debug(psid() + "incoming game change is " + newGame.getId());
                boolean speed = timed && Game.isSpeedGame(initialMinutes, incrementalSeconds);
                log4j.debug(psid() + "is it speed game? " + speed);
                if (speed && !newGame.isSpeed()) {
                    newGame = GridStateFactory.getSpeedGame(newGame);
                    log4j.debug(psid() + "ok, converted incoming game change to " + newGame.getId());
                }
                else if (!speed && newGame.isSpeed()) {
                    newGame = GridStateFactory.getNormalGame(newGame);
                    log4j.debug(psid() + "ok, converted incoming game change to " + newGame.getId());
                }

                // player changed game
                if (game.getId() != newGame.getId()) {
                    log4j.debug(psid() + "gameChanged");
                    
                    game = newGame;

                    // if the game is changed, remove all computer players since
                    // they might not know how to play the different game
                    removeAllComputers();
                    if (gridState != null) {
                        gridState.clear();
                    }
                    
                }

                // we might have switched game to/from speed, so send
                // back the correct game. do this even if newGame matches current
                // game because client always sends in Normal game, server
                // switches it to speed-game and must always send that back out
                changeStateEvent.setGame(game.getId());
                
				broadcastMainRoom(changeStateEvent);
				
			}
		}

		if (status != NO_ERROR) {
			// for change errors send back the current state
			// so clients can reset themselves
			DSGChangeStateTableErrorEvent changeError = new DSGChangeStateTableErrorEvent(changeStateEvent.getPlayer(), tableNum, status);
			changeError.setGame(game.getId());
			changeError.setInitialMinutes(initialMinutes);
			changeError.setIncrementalSeconds(incrementalSeconds);
			changeError.setTimed(timed);
			changeError.setRated(rated);
            changeError.setTableType(tableType);
			dsgEventRouter.routeEvent(changeError, changeStateEvent.getPlayer());
		}
		else {

            try {
                DSGPlayerPreference pref = new DSGPlayerPreference("gameState", changeStateEvent);
                dsgPlayerStorer.storePlayerPreference(actor.getPlayerID(), pref);
            } catch (DSGPlayerStoreException dpse) {
                log4j.debug("Error saving game state", dpse);
            }
		}
	}

    private void changeTableState(DSGChangeStateTableEvent changeStateEvent) {

        resetClickedPlays();
        
        initialMinutes = changeStateEvent.getInitialMinutes();
        incrementalSeconds = changeStateEvent.getIncrementalSeconds();
        timed = changeStateEvent.getTimed();
        game = GridStateFactory.getGame(changeStateEvent.getGame());
        rated = changeStateEvent.getRated();

        for (int i = 1; i < timers.length; i++) {
            timers[i].setStartMinutes(initialMinutes);
        }

        broadcastMainRoom(changeStateEvent);
    }

	public void handleSit(String player, int seat) {

		int error = NO_ERROR;
		if (!isPlayerInTable(player)) {
			error = DSGTableErrorEvent.NOT_IN_TABLE;
		}
		else if (getPlayerSeat(player) != NOT_SITTING) {
			error = DSGTableErrorEvent.ALREADY_SITTING;
		}
		else if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
			error = DSGTableErrorEvent.GAME_IN_PROGRESS;
		}
		//not really possible but just in case
		else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
			error = DSGTableErrorEvent.WAIT_GAME_TWO_OF_SET;
		}
		else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
			error = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
		}
		else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
			if (seat < 1 || seat > MAX_PLAYERS) {
				error = DSGTableErrorEvent.UNKNOWN;
			}
			else if (sittingPlayers[seat] != null) {
				error = DSGTableErrorEvent.SEAT_TAKEN;
			}
			else {
				sit(player, seat);
			}
		}
		
		if (error != NO_ERROR) {
			dsgEventRouter.routeEvent(
				new DSGSitTableErrorEvent(player, tableNum, seat, error),
				player);
            
            // if player failing to sit is a computer, then remove it
            // since it won't be able to play and will be a zombie
            DSGPlayerData dsgPlayerData = getPlayerInTable(player);
            if (dsgPlayerData != null && dsgPlayerData.isComputer()) {
                broadcastTable(
                    new DSGTextTableEvent(dsgPlayerData.getName(),
                        tableNum, "Oh my, someone's in my seat, bye!"));
                aiController.removeAIPlayer(player, tableNum);
            }
		}
	}

    private boolean anyComputersSitting() {
        
        for (int i = 0; i < sittingPlayers.length; i++) {
            if (sittingPlayers[i] != null && sittingPlayers[i].isComputer()) {
                return true;
            }
        }
        
        return false;
    }
    private boolean allComputersSitting() {
        
        for (int i = 1; i < sittingPlayers.length; i++) {
            if (sittingPlayers[i] == null || !sittingPlayers[i].isComputer()) {
                return false;
            }
        }
        
        return true;
    }
    private void removeAllComputers() {
        for (Iterator it = playersInTable.iterator(); it.hasNext();) {
            DSGPlayerData data = (DSGPlayerData) it.next();
            if (data.isComputer()) {
                aiController.removeAIPlayer(data.getName(), tableNum);
            }
        }
    }

    private boolean noHumanPlayersInTable() {
        for (Iterator it = playersInTable.iterator(); it.hasNext();) {
            if (((DSGPlayerData) it.next()).isHuman()) {
                return false;
            }
        }
        
        return true;
    }

	private void sit(String player, int seat) {
		sittingPlayers[seat] = getPlayerInTable(player);

		broadcastMainRoom(new DSGSitTableEvent(player, tableNum, seat));

        // if a computer is sitting, make the game unrated and untimed
        if (anyComputersSitting()) {

            DSGChangeStateTableEvent event =
                new DSGChangeStateTableEvent(player, tableNum);
            event.setGame(game.getId());
            event.setIncrementalSeconds(incrementalSeconds);
            event.setInitialMinutes(initialMinutes);
            event.setRated(false);
            event.setTimed(false);
            event.setTableType(tableType); // this didn't change, include for completeness

            changeTableState(event);
        }
	}
	private void stand(String player, int seat) {
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

	public void handleStand(String player) {
		
		int error = NO_ERROR;
		if (!isPlayerInTable(player)) {
			error = DSGTableErrorEvent.NOT_IN_TABLE;
		}
		else {
			int seat = getPlayerSeat(player);
			if (seat == NOT_SITTING) {
				error = DSGTableErrorEvent.NOT_SITTING;
			}
			else if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
				error = DSGTableErrorEvent.GAME_IN_PROGRESS;
			}
			else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
				error = DSGTableErrorEvent.WAIT_GAME_TWO_OF_SET;
			}
			else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
				error = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
			}
			else {
				stand(player, seat);
			}
		}
		
		if (error != NO_ERROR) {
			dsgEventRouter.routeEvent(
				new DSGStandTableErrorEvent(player, tableNum, error),
				player);
		}
	}

    public void handleSwap(DSGSwapSeatsTableEvent swapEvent) {
        int error = NO_ERROR;
        if (!isPlayerInTable(swapEvent.getPlayer())) {
            error = DSGTableErrorEvent.NOT_IN_TABLE;
        }
        else {
            int seat = getPlayerSeat(swapEvent.getPlayer());
            if (seat != 2) {
                error = DSGTableErrorEvent.NOT_SITTING;
            }
            else if (state != DSGGameStateTableEvent.GAME_IN_PROGRESS) {
                error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
            }
            else if ((game != GridStateFactory.DPENTE_GAME &&
                      game != GridStateFactory.SPEED_DPENTE_GAME &&
			game != GridStateFactory.DKERYO_GAME &&
			game != GridStateFactory.SPEED_DKERYO_GAME) || 
                     gridState.getNumMoves() != 4 ||
                     ((PenteState) gridState).wasDPenteSwapDecisionMade()) {
                error = DSGTableErrorEvent.UNKNOWN;
            }
            else {
                
                // cancel any undo requests from player 1 since we already decided
                // to swap or not, it's too late
                undoRequested = false;
                
                // swap the players
                if (swapEvent.wantsToSwap()) {
                    DSGPlayerData tmp = playingPlayers[1];
                    playingPlayers[1] = playingPlayers[2];
                    playingPlayers[2] = tmp;
                    sittingPlayers[1] = sittingPlayers[2];
                    sittingPlayers[2] = tmp;
                }

                // update timers after swap decision
                if (timed) {
                    timers[gridState.getCurrentPlayer()].stop();
                    timers[gridState.getCurrentPlayer()].incrementMillis(
                        (int) pingManager.getPingTime(swapEvent.getPlayer()));
                    
                    // swap player times
                    if (swapEvent.wantsToSwap()) {
                        int s1 = timers[1].getSeconds();
                        int m1 = timers[1].getMinutes();
                        int s2 = timers[2].getSeconds();
                        int m2 = timers[2].getMinutes();
                        timers[1].adjust(m2, s2);
                        timers[2].adjust(m1, s1);
                    }
                }

                ((PenteState) gridState).dPenteSwapDecisionMade(
                    swapEvent.wantsToSwap());
                    
                if (timed) {
                    timers[gridState.getCurrentPlayer()].go();
                }

                broadcastMainRoom(swapEvent);
            }
        }
        if (error != NO_ERROR) {
            log4j.info(psid() + "Swap Event error: " + error);
        }
    }

	private void copySittingPlayersToPlayingPlayers() {
		for (int i = 1; i < playingPlayers.length; i++) {
			playingPlayers[i] = sittingPlayers[i];
            
            broadcastTable(
                new DSGSetPlayingPlayerTableEvent(playingPlayers[i].getName(), tableNum, i));
		}
	}
    
    private void sendPlayingPlayers(String toPlayer) {
        for (int i = 1; i < playingPlayers.length; i++) {
            dsgEventRouter.routeEvent(
                new DSGSetPlayingPlayerTableEvent(playingPlayers[i].getName(), tableNum, i),
                toPlayer);
        }
    }
    
	private boolean allPlayersSitting() {
		for (int i = 1; i < sittingPlayers.length; i++) {
			if (sittingPlayers[i] == null) {
				return false;
			}
		}
		return true;
	}

    /** Determine if all players have agreed to start the game
     *  Assumes that computer opponents always want to play
     */
	private boolean allPlayersClickedPlay() {
		for (int i = 1; i < playerClickedPlay.length; i++) {
            // if the player is a computer, or the player clicked play
			if (!playerClickedPlay[i] && 
                !(sittingPlayers[i] != null && sittingPlayers[i].isComputer())) {
				return false;
			}
		}
		return true;
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
		}
		else if (!allPlayersSitting()) {
			error = DSGTableErrorEvent.NOT_ALL_PLAYERS_SITTING;
		}
		else if (rated && getPlayerInTable(player).isGuest()) {
			error = DSGTableErrorEvent.GUEST_NOT_ALLOWED;

			dsgEventRouter.routeEvent(
				new DSGSystemMessageTableEvent(
                    tableNum, "Guests are not allowed to play rated games, play unrated or create a free user account!"),
                    player);
		}
        // if only computers are playing the game, the owner of the table
        // has to click play to start the game
        else if (allComputersSitting()) {
            
            if (!isPlayerOwner(player)) {
                error = DSGTableErrorEvent.NOT_TABLE_OWNER;
            }
            else if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
                error = DSGTableErrorEvent.GAME_IN_PROGRESS;
            }            
            else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
                error = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
            }
            else {
				// echo play event back to client
				dsgEventRouter.routeEvent(playEvent, player);
				
                startGame();
            }
        }
		else {
			int seat = getPlayerSeat(player);
            
            if (seat == NOT_SITTING) {
				error = DSGTableErrorEvent.NOT_SITTING;
			}
			else if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
				error = DSGTableErrorEvent.GAME_IN_PROGRESS;
			}
			else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
				error = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
			}
			else if (playerClickedPlay[seat]) {
				//just ignore multiple clicks, keeps client simpler
				//error = DSGTableErrorEvent.PLAY_ALREADY_CLICKED;
			}
			else {
				playerClickedPlay[seat] = true;

				if (allPlayersClickedPlay()) {
                    if (serverData.isTournament() && !isValidTourneyMatch()) {
                        error = DSGTableErrorEvent.TOURNAMENT_GAME;
                        broadcastTable(new DSGSystemMessageTableEvent(
                            tableNum,
                            "game cancelled, " + sittingPlayers[1].getName() + 
                            " and " + sittingPlayers[2].getName() + " are not " +
                            "scheduled to play a tournament match, or have " +
                            "already played (switch seats maybe?)"));
                    }
                    else {
						// echo play event back to client
						dsgEventRouter.routeEvent(playEvent, player);
                        startGame();
                    }
                }
				else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {

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
        }
		
        if (error != NO_ERROR) {
            dsgEventRouter.routeEvent(
				new DSGPlayTableErrorEvent(player, tableNum, error),
				player);
		}
	}

	private void startSetTimeOut(String dp) {

		if (noMoreTimerResets) return; // if both players already used up a chance, no more chances

		if (disconnectedPlayer == null) {
			disconnectedPlayer = dp;
		}
		else if (disconnectedPlayer.equals(dp)) {
			return; // if already given a chance, no 2nd chance
		}
		else {
			noMoreTimerResets = true;
		}
		
		long timeLeft = WAITING_FOR_PLAYER_TO_RETURN_TIMEOUT * 1000 * 60;
		startWaitingForPlayerToReturnTimer();
		broadcastTable(new DSGStartSetTimerEvent(null, tableNum, timeLeft));
	}
	
    private boolean isValidTourneyMatch() {

        Tourney tourney = server.getTourney();
        boolean valid = false;

        try {
            long pid1 = sittingPlayers[1].getPlayerID();
            long pid2 = sittingPlayers[2].getPlayerID();
            tourneyMatch = resources.getTourneyStorer().getUnplayedMatch(
                pid1, pid2, tourney.getEventID());
            
            valid = tourneyMatch != null;
            tourneyCurrentRound = tourney.getLastRound().getRound();
            
        } catch (Throwable t) {
            log4j.error("Unable to get tourney match.", t);
        }
        
        return valid;
    }
    
    private void startGame() {

        waitingForPlayerToReturnTimeUp = false;
		if (waitingForPlayerToReturnTimer != null) {
			waitingForPlayerToReturnTimer.stop();
			waitingForPlayerToReturnTimer.destroy();
			waitingForPlayerToReturnTimer = null;
		}
		noMoreTimerResets = false;
		disconnectedPlayer = null;

        resetClickedPlays();
        copySittingPlayersToPlayingPlayers();
        undoRequested = false;
        cancelRequested = false;
        cancelRequestedBy = null;
        gameTime = null;

        // to avoid creating new gridstate we could add another variable
        // to track when we need to create a new state
        gridState = GridStateFactory.createGridState(game.getId());

        // if playing unrated pente or keryo, set tournament rule off
        if (game == GridStateFactory.PENTE_GAME ||
            game == GridStateFactory.SPEED_PENTE_GAME ||
            game == GridStateFactory.KERYO_GAME ||
            game == GridStateFactory.SPEED_KERYO_GAME ||
            game == GridStateFactory.BOAT_PENTE_GAME ||
            game == GridStateFactory.SPEED_BOAT_PENTE_GAME) {
            ((PenteState) gridState).setTournamentRule(rated);
        }

        String startTxt = "game started";
        int gameInSet = 0;
        if (rated && set == null) {
        	set = new LiveSet();
        	set.setP1Pid(playingPlayers[1].getPlayerID());
        	set.setP2Pid(playingPlayers[2].getPlayerID());
        	set.setStatus(LiveSet.STATUS_ACTIVE);
        	try {
        		dsgPlayerStorer.insertLiveSet(set);
        	} catch (DSGPlayerStoreException dpse) {
        		log4j.error(psid() + "Error creating set.", dpse);
        		//what can i really do at this point?
        	}
        	startTxt = "game 1 of set started";
        	gameInSet = 1;
        }
        else if (rated && set != null && state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
        	startTxt = "game 2 of set started";
        	gameInSet = 2;
        }
        
        for (int i = 1; i < timers.length; i++) {
            timers[i].reset();
        }

        changeGameState(DSGGameStateTableEvent.GAME_IN_PROGRESS, startTxt, gameInSet);
        
        activityLogger.startGame(sid, tableNum, playingPlayers[1].getName(),
            playingPlayers[2].getName(), rated);
        
        gameStarted = true;
        
        // handle 1st move
        handleMove(playingPlayers[1].getName(), 180);
    }

	private int getGameInSet() {
		int gameInSet = 0;
        if (rated) {
        	if (set == null || set.getG1Gid() == 0 || 
        		(set.getG2Gid() == 0 && state != DSGGameStateTableEvent.GAME_IN_PROGRESS)) {
        		gameInSet = 1;
        	}
        	else if (set.isComplete()) {
        		gameInSet = 0;
        	}
        	else {
        		gameInSet = 2;
        	}

        }
		return gameInSet;
	}
    
	private boolean isPlayerOwner(String player) {
        return player.equals(getOwner());
	}
	private String getOwner() {
		if (playersInTable.isEmpty()) {
			return null;
		}
		else {
            for (Iterator it = playersInTable.iterator(); it.hasNext();) {
                DSGPlayerData d = (DSGPlayerData) it.next();
                if (d.isHuman()) {
                    return d.getName();
                }
            }
            return null;
		}
	}
	
	public void handleMove(String player, int move) {

		int error = NO_ERROR;
		if (!isPlayerInTable(player)) {
			error = DSGTableErrorEvent.NOT_IN_TABLE;
		}
		else {
			int seat = getPlayerSeat(player);
			if (seat == NOT_SITTING) {
				error = DSGTableErrorEvent.NOT_SITTING;
			}
            else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
                error = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
            }
			else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
				error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
			}
			else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
				error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
			}
			else if (gridState.getCurrentPlayer() != seat) {
				error = DSGTableErrorEvent.NOT_TURN;
			}
			else if (!gridState.isValidMove(move, seat)) {
				error = DSGTableErrorEvent.INVALID_MOVE;
			}
			else {
				
                int oldCurrentPlayer = gridState.getCurrentPlayer();
                gridState.addMove(move);
                int newCurrentPlayer = gridState.getCurrentPlayer();
                
				if (timed) {
					// don't stop/start timers if same players
					if (oldCurrentPlayer != newCurrentPlayer) {
						timers[oldCurrentPlayer].stop();

	                    // in D-pente, after 4th move, p2 has to decide to swap or
	                    // not. if p1 requests undo and accepted, reset p2's clock
	                    // to initial time
	                    if ((game == GridStateFactory.DPENTE_GAME ||
	                         game == GridStateFactory.SPEED_DPENTE_GAME ||
								game == GridStateFactory.DKERYO_GAME || game == GridStateFactory.SPEED_DKERYO_GAME) &&
	                        gridState.getNumMoves() == 4) {
	                        Time newTime2 =
	                            new Time(timers[newCurrentPlayer].getMinutes(),
	                                     timers[newCurrentPlayer].getSeconds());
	                        moveTimes.add(newTime2);
	                    }
	                    
						if (gridState.getNumMoves() != 1) {
	                        timers[oldCurrentPlayer].increment(
	                            incrementalSeconds);
	                        // should also increment millis for d-pente but ignore
	                        // since timers aren't stopped
							timers[oldCurrentPlayer].incrementMillis(
	                            (int) pingManager.getPingTime(player));
						}
	                    Time newTime = 
	                        new Time(timers[oldCurrentPlayer].getMinutes(),
	                                 timers[oldCurrentPlayer].getSeconds());
	
						moveTimes.add(newTime);
					}
				}

				if (undoRequested) {
					broadcastTable(new DSGUndoReplyTableEvent(player, tableNum, false));
					undoRequested = false;
				}
				if (cancelRequested) {
					broadcastTable(new DSGCancelReplyTableEvent(player, tableNum, false));
					cancelRequested = false;
					cancelRequestedBy = null;
				}

				broadcastTable(new DSGMoveTableEvent(player, tableNum, move));
				
				if (gridState.getNumMoves() != 1 && timed &&
					oldCurrentPlayer != newCurrentPlayer) {
					broadcastTable(
						new DSGTimerChangeTableEvent(
							player, tableNum, 
							timers[oldCurrentPlayer].getMinutes(),
							timers[oldCurrentPlayer].getSeconds()));
				}

                activityLogger.updateGameState(sid, tableNum, gridState.getHash(),
                    gridState.getMoves());
				
				if (gridState.isGameOver()) {
                    String winner = null;
                    String loser = null;
                    if (gridState.getWinner() == 0) {
                        winner = playingPlayers[1].getName();
                        loser = playingPlayers[2].getName();
                    }
                    else {
                        winner = playingPlayers[gridState.getWinner()].getName();
                        loser = playingPlayers[3 - gridState.getWinner()].getName();
                    }
					gameOver(gridState.getWinner() == 0, winner, loser, false, false, false);
				}
				else if (timed) {
					if (oldCurrentPlayer != newCurrentPlayer) {
						timers[newCurrentPlayer].go();
					}
					// if playing d-pente, start timer for p1 after 1st move
					// because it is still p1's turn
					else if ((game == GridStateFactory.DPENTE_GAME ||
                              game == GridStateFactory.SPEED_DPENTE_GAME ||
					game == GridStateFactory.DKERYO_GAME || game == GridStateFactory.SPEED_DKERYO_GAME) &&
                             gridState.getNumMoves() == 1) {
						timers[newCurrentPlayer].go();
					}
				}
			}
		}
		
		if (error != NO_ERROR) {
			dsgEventRouter.routeEvent(
				new DSGMoveTableErrorEvent(player, tableNum, move, error),
				player);
		}
	}
	
	public void handleUndoRequest(DSGUndoRequestTableEvent undoRequestEvent) {

		int error = NO_ERROR;
		if (!isPlayerInTable(undoRequestEvent.getPlayer())) {
			error = DSGTableErrorEvent.NOT_IN_TABLE;
		}
		else {
			int seat = getPlayerSeat(undoRequestEvent.getPlayer());
			if (seat == NOT_SITTING) {
				error = DSGTableErrorEvent.NOT_SITTING;
			}
			else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
				error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
			}
			else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
				error = DSGTableErrorEvent.WAIT_GAME_TWO_OF_SET;
			}
			else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
				error = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
			}
			else if (!gridState.canPlayerUndo(seat)) {
				error = DSGTableErrorEvent.CANT_UNDO;
			}
			else if (undoRequested) {
				error = DSGTableErrorEvent.UNDO_ALREADY_REQUESTED;
			}
			else {
				undoRequested = true;
				
				broadcastTable(undoRequestEvent);
			}
		}
		
		if (error != NO_ERROR) {
			dsgEventRouter.routeEvent(
				new DSGUndoRequestTableErrorEvent(undoRequestEvent.getPlayer(), tableNum, error),
				undoRequestEvent.getPlayer());
		}
	}
	
	public void handleUndoReply(DSGUndoReplyTableEvent undoReplyEvent) {

		int error = NO_ERROR;
		if (!isPlayerInTable(undoReplyEvent.getPlayer())) {
			error = DSGTableErrorEvent.NOT_IN_TABLE;
		}
		else {
			int seat = getPlayerSeat(undoReplyEvent.getPlayer());
			if (seat == NOT_SITTING) {
				error = DSGTableErrorEvent.NOT_SITTING;
			}
			else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
				error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
			}
			else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
				error = DSGTableErrorEvent.WAIT_GAME_TWO_OF_SET;
			}
			else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
				error = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
			}
			else if (!undoRequested) {
				error = DSGTableErrorEvent.NO_UNDO_REQUESTED;
			}
			// this will only work for 2 player games...
            // if this player can undo, that means they can't accept
            // an undo so this is an error
			else if (gridState.canPlayerUndo(seat)) {
				error = DSGTableErrorEvent.CANT_UNDO;
			}
			else {
				undoRequested = false;

				if (undoReplyEvent.getAccepted()) {

                    int oldCurrentPlayer = gridState.getCurrentPlayer();
                    gridState.undoMove();
                    int newCurrentPlayer = gridState.getCurrentPlayer();

                    // in D-pente, 1st player makes 1st 4 moves, so if they
                    // undo any of them, don't reset clocks
                    // or connect6
					if (timed && oldCurrentPlayer != newCurrentPlayer) {
                        // remove the last move time
                        moveTimes.remove(moveTimes.size() - 1);

                            
						timers[oldCurrentPlayer].stop();
                    	
                    	// reset the current players clock to what it was when the turn started
                        // so that the player accepting the undo is not penalized
                        Time undoTime = (Time) moveTimes.get(moveTimes.size() - 1);
                        timers[oldCurrentPlayer].adjust(undoTime.getMinutes(), undoTime.getSeconds());
                        broadcastTable(
                            new DSGTimerChangeTableEvent(
                            playingPlayers[oldCurrentPlayer].getName(), 
                            tableNum, 
                            undoTime.getMinutes(), 
                            undoTime.getSeconds()));
            
                        // reset the players turn who just had the undo to take away any incremental
                        // time they gained in the last turn
                        // this doesn't subtract the increase they received from their ping time
                        // i could create another list of ping times at each move but that seems
                        // like overkill at the moment
                        timers[newCurrentPlayer].increment(-incrementalSeconds);
                        broadcastTable(
                            new DSGTimerChangeTableEvent(
                            playingPlayers[newCurrentPlayer].getName(), 
                            tableNum, 
                            timers[newCurrentPlayer].getMinutes(), 
                            timers[newCurrentPlayer].getSeconds()));
                        
                        
						timers[newCurrentPlayer].go();
                    }

                    activityLogger.updateGameState(sid, tableNum, 
                        gridState.getHash(), gridState.getMoves());
				}
				broadcastTable(undoReplyEvent);
			}
		}
		
		if (error != NO_ERROR) {
			dsgEventRouter.routeEvent(
				new DSGUndoReplyTableErrorEvent(undoReplyEvent.getPlayer(), tableNum, undoReplyEvent.getAccepted(), error),
				undoReplyEvent.getPlayer());
		}
	}
	
	public void handleCancelRequest(DSGCancelRequestTableEvent cancelRequestEvent) {

		int error = NO_ERROR;
        DSGPlayerData actor = getPlayerInTable(cancelRequestEvent.getPlayer());
		if (actor == null) {
			error = DSGTableErrorEvent.NOT_IN_TABLE;
		}
        else if (allComputersSitting()) {
            if (!actor.isAdmin() && !isPlayerOwner(cancelRequestEvent.getPlayer())) {
                error = DSGTableErrorEvent.NOT_TABLE_OWNER;
            }
            else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
                error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
            }
            else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
                error = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
            }
            else {
                cancelGame(LiveSet.STATUS_CANCELED);
            }
        }
		else {
            
			int seat = getPlayerSeat(cancelRequestEvent.getPlayer());
			if (seat == NOT_SITTING) {
				error = DSGTableErrorEvent.NOT_SITTING;
			}
			else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
				error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
			}
			else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
				error = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
			}
			else if (cancelRequested) {
				error = DSGTableErrorEvent.CANCEL_ALREADY_REQUESTED;
			}
			else {
				cancelRequested = true;
				cancelRequestedBy = cancelRequestEvent.getPlayer();
				
				broadcastTable(cancelRequestEvent);
			}
		}
		

		if (error != NO_ERROR) {
			dsgEventRouter.routeEvent(
				new DSGCancelRequestTableErrorEvent(cancelRequestEvent.getPlayer(), tableNum, error),
				cancelRequestEvent.getPlayer());
		}
	}
	
	public void handleCancelReply(DSGCancelReplyTableEvent cancelReplyEvent) {

		int error = NO_ERROR;
		if (!isPlayerInTable(cancelReplyEvent.getPlayer())) {
			error = DSGTableErrorEvent.NOT_IN_TABLE;
		}
		else {
			int seat = getPlayerSeat(cancelReplyEvent.getPlayer());
			if (seat == NOT_SITTING) {
				error = DSGTableErrorEvent.NOT_SITTING;
			}
			else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
				error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
			}
            // if player requests cancel, then gets booted, other player
            // can still cancel game
			else if (!cancelRequested) {
				error = DSGTableErrorEvent.NO_CANCEL_REQUESTED;
			}
			// this only works for 2 player games
			// if more players playing all players must agree to cancel
			else if (cancelRequestedBy.equals(cancelReplyEvent.getPlayer())) {
				error = DSGTableErrorEvent.UNKNOWN;
			}
			else {
                // whether or not the cancel is accepted, reset cancelRequested
                // variables so a cancel can be requested again
                cancelRequested = false;
                cancelRequestedBy = null;

				if (cancelReplyEvent.getAccepted()) {
					cancelGame(LiveSet.STATUS_CANCELED);
				}
				else {
					broadcastTable(cancelReplyEvent);
				}
			}
		}

		if (error != NO_ERROR) {
			dsgEventRouter.routeEvent(
				new DSGCancelReplyTableErrorEvent(cancelReplyEvent.getPlayer(), tableNum, error, cancelReplyEvent.getAccepted()),
				cancelReplyEvent.getPlayer());
		}
	}
	
	public void handleExit(String player, boolean forced) {

		if (!isPlayerInTable(player)) {
			dsgEventRouter.routeEvent(
				new DSGExitTableErrorEvent(player, tableNum, DSGTableErrorEvent.NOT_IN_TABLE),
				player);
		}
		else {
			int seat = getPlayerSeat(player);
			if (seat != NOT_SITTING) {
				if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS ||
					state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
					if (forced) {
						
						if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
							changeGameState(DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN, 
							    "player " + player + " has been disconnected, game is paused. ", 0);

							stopTimers();
							startWaitingForPlayerToReturnTimer();
						}
						else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {

							startSetTimeOut(player);

							changeGameState(DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN, 
								"player " + player + " has been disconnected, set is still active.", 0);
						}
						exit(player, false);
					}
					else {
						int error = state == DSGGameStateTableEvent.GAME_IN_PROGRESS ?
							DSGTableErrorEvent.GAME_IN_PROGRESS :
							DSGTableErrorEvent.WAIT_GAME_TWO_OF_SET;
						dsgEventRouter.routeEvent(
							new DSGExitTableErrorEvent(player, tableNum, error),
							player);
					}
				}
				else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
					if (forced) {
						cancelGame(LiveSet.STATUS_CANCEL_DOUBLE_DISCONNECT);
						exit(player, false);
					}
					else {
						dsgEventRouter.routeEvent(
							new DSGExitTableErrorEvent(player, tableNum, DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN),
							player);
					}
				}
				else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
					exit(player, false);
				}
			}
			// else not sitting
			else {
				exit(player, false);
			}
		}
	}
	
	/** Starts a waiting for player to return timer.
	 *  We let each timer run to completion even if
	 *  the player returns before the time up.  The synchronization
	 *  issues become to complex otherwise.
	 */
	private void startWaitingForPlayerToReturnTimer() {

		final int localSeqNbr = ++waitingForPlayerToReturnSeqNbr;
		if (waitingForPlayerToReturnTimer != null) {
			waitingForPlayerToReturnTimer.destroy();
		}
		waitingForPlayerToReturnTimer = new MilliSecondGameTimer("WaitReturn-" + localSeqNbr);
		waitingForPlayerToReturnTimer.setStartMinutes(WAITING_FOR_PLAYER_TO_RETURN_TIMEOUT);
		waitingForPlayerToReturnTimer.reset();
		waitingForPlayerToReturnTimer.addGameTimerListener(new GameTimerListener() {
			public void timeChanged(int minutes, int seconds) {
				if (minutes <= 0 && seconds <= 0) {
                    waitingForPlayerToReturnTimer.removeGameTimerListener(this);
					waitingForPlayerToReturnTimer.stop();
					waitingForPlayerToReturnTimer.destroy();
					synchronizedTableListener.eventOccurred(
						new DSGWaitingPlayerReturnTimeUpTableEvent(SYSTEM, tableNum, localSeqNbr, false));
				}
			}
		});
		waitingForPlayerToReturnTimer.go();
	}
	
	public void handleWaitingPlayerReturnTimeUp(DSGWaitingPlayerReturnTimeUpTableEvent timeUpEvent) {
		
		if (!timeUpEvent.getPlayer().equals(SYSTEM)) {
			// illegal access
		}
		else {

			if (waitingForPlayerToReturnSeqNbr == timeUpEvent.getWaitingForPlayerToReturnSeqNbr()) {
				if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN &&
					prevState == DSGGameStateTableEvent.GAME_IN_PROGRESS) {

					waitingForPlayerToReturnTimeUp = true;

					// only works for 2 player games
					String waitingPlayer = null;
					for (int i = 1; i < sittingPlayers.length; i++) {
						if (sittingPlayers[i] != null) {
							waitingPlayer = sittingPlayers[i].getName();
							break;
						}
					}
					dsgEventRouter.routeEvent(
						new DSGWaitingPlayerReturnTimeUpTableEvent(
						waitingPlayer, tableNum, 0, false),
						waitingPlayer);
				}
				else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN ||
						 state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {

					waitingForPlayerToReturnTimeUp = true;
					String waitingPlayer = null;
					
					// if both players sitting then let player that clicked
					// play decide
					if (allPlayersSitting()) {
						for (int i = 1; i < playerClickedPlay.length; i++) {
							if (playerClickedPlay[i]) {
								waitingPlayer = sittingPlayers[i].getName();
								break;
							}
						}
					}
					// else it doesn't matter who clicked play, let remaining
					// sitting player decide
					else {
						for (int i = 1; i < sittingPlayers.length; i++) {
							if (sittingPlayers[i] != null) {
								waitingPlayer = sittingPlayers[i].getName();
								break;
							}
						}
					}
					
					// if timer was started somehow and neither player has
					// clicked play since returning
					// start the timer again....that shouldn't ever happen but might as well do it
					if (waitingPlayer == null) {
						noMoreTimerResets = false;
						startSetTimeOut(sittingPlayers[1].getName());
						noMoreTimerResets = true;
					}
					else {
						dsgEventRouter.routeEvent(
							new DSGWaitingPlayerReturnTimeUpTableEvent(
							waitingPlayer, tableNum, 0, true),
							waitingPlayer);
					}
				}
			}
		}
	}
	
	/** This method assumes that all validation has already
	 *  been done that it is ok to exit
	 */
	private void exit(String player, boolean booted) {
		boolean owner = isPlayerOwner(player);

		int seat = getPlayerSeat(player);
		if (seat != NOT_SITTING) {
			stand(player, seat);
		}

        removePlayer(player);

		if (owner) {
			String newOwner = getOwner();
			if (newOwner != null) {

                playersInvited.clear(); // clear all invitations from previous owner
                broadcastTable(new DSGOwnerTableEvent(
                    newOwner, tableNum));
			}
		}
		
		broadcastMainRoom(new DSGExitTableEvent(
            player, tableNum, false, booted));

		if (noHumanPlayersInTable()) {
            if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS ||
            	state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
                removeAllComputers();
            }
            else if (tableType == DSGChangeStateTableEvent.TABLE_TYPE_PRIVATE) {
                tableType = DSGChangeStateTableEvent.TABLE_TYPE_PUBLIC;
                broadcastMainRoom(getTableState());
            }
            
            if (set != null) {
            	set.setStatus(LiveSet.STATUS_CANCEL_DOUBLE_DISCONNECT);
            	try {
            		dsgPlayerStorer.updateLiveSet(set);
            	} catch (DSGPlayerStoreException dpse) {
            		dpse.printStackTrace();
            	}
            	set = null;
            }
		}
        
        if (playersInTable.isEmpty()) {
            server.removeTable(tableNum);
        }
	}

    private void removePlayer(String name) {
        for (int i = 0; i < playersInTable.size(); i++) {
            DSGPlayerData data = (DSGPlayerData) playersInTable.elementAt(i);
            if (data.getName().equals(name)) {
                playersInTable.remove(i);
            }
        }
    }
    private DSGPlayerData getPlayerInTable(String name) {
        for (int i = 0; i < playersInTable.size(); i++) {
            DSGPlayerData data = (DSGPlayerData) playersInTable.elementAt(i);
            if (data != null && name.equals(data.getName())) {
                return data;
            }
        }
        
        return null;
    }
    private DSGPlayerData getPlayerInMainRoom(String name) {
        for (int i = 0; i < playersInMainRoom.size(); i++) {
            DSGPlayerData data = (DSGPlayerData) playersInMainRoom.elementAt(i);
            if (data.getName().equals(name)) {
                return data;
            }
        }
        
        return null;
    }

	private void changeGameState(int newState, String reason, int gameInSet) {
		changeGameState(newState, reason, null, gameInSet);
	}
	private void changeGameState(int newState, String reason, String winner, int gameInSet) {
		prevState = state;
		state = newState;
		broadcastTable(new DSGGameStateTableEvent(
			null, tableNum, state, reason, winner, gameInSet));
	}
    
    private void resetTableGameOver() {

        undoRequested = false;
        cancelRequested = false;
        cancelRequestedBy = null;
        waitingForPlayerToReturnTimeUp = false;
        gameTime = new Date();

        for (int i = 1; i < timers.length; i++) {
            timers[i].stop();
        }
        
        activityLogger.gameOver(sid, tableNum, playingPlayers[1].getName(),
            playingPlayers[2].getName());
    }
    
	private void cancelGame(String setStatus) {

		resetTableGameOver();
		gameStarted = false;
		
		if (rated && set != null) {
			set.setStatus(setStatus);
			try {
				dsgPlayerStorer.updateLiveSet(set);
			} catch (DSGPlayerStoreException dpse) {
				log4j.error(psid() + "Error canceling set.", dpse);
			}
			set = null;
		}
		String txt = (rated ? "set" : "game") + " cancelled";

		changeGameState(DSGGameStateTableEvent.NO_GAME_IN_PROGRESS, txt, 0);
	}
	
	private void gameOver(boolean draw, 
		String winnerPlayer, String loserPlayer, boolean resign, boolean timeup,
		boolean forceResign) {
		
		resetTableGameOver();

        int winner = getPlayingPlayerSeat(winnerPlayer);

		int newStatus = DSGGameStateTableEvent.NO_GAME_IN_PROGRESS;
		int gameInSet = 0;
		String setMsg = "";
		String gameStatus = GameData.STATUS_WIN;
		if (resign) {
			gameStatus = GameData.STATUS_RESIGN;
		}
		else if (timeup) {
			gameStatus = GameData.STATUS_TIMEOUT;
		}
		else if (forceResign) {
			gameStatus = GameData.STATUS_FORCE_RESIGN;
		}
		
        if (rated && set != null) {
        	if (set.getG1Gid() == 0) {
        		
        		// if not all sitting and 
        		// resign or force resign means one player disconnected
        		// and other player made a decision during game 1
        		if (!allPlayersSitting() && (resign || forceResign)) {
        			newStatus = DSGGameStateTableEvent.NO_GAME_IN_PROGRESS;
        			set.setStatus(resign ? LiveSet.STATUS_RESIGN : LiveSet.STATUS_FORCED);
        			gameInSet = 0;
        			
        			long winnerPid = playingPlayers[getPlayingPlayerSeat(winnerPlayer)].getPlayerID();
        			int winnerSetPos = set.getP1Pid() == winnerPid ? 1 : 2;
        			set.setWinner(winnerSetPos);
            		set.setCompletionDate(new Date());
            		setMsg = "set over, " + loserPlayer + " resigns";
        		}
        		else {
        			newStatus = DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET;
        			set.setStatus(LiveSet.STATUS_ONE_GAME_COMPLETED);
        			gameInSet = 1;
        		}
        	}
        	else {
        		
        		long g2WinnerPid = playingPlayers[getPlayingPlayerSeat(winnerPlayer)].getPlayerID();

        		int winnerSetPos = set.getP1Pid() == g2WinnerPid ? 1 : 2;
        		
        		long g1WinnerPid = 0;
        		int result = 0;
        		// if first game draw
        		if (set.getG1().getWinner() == 0) {
        			if (draw) {
        				result = 0;
        			}
        			else {
        				result = winnerSetPos;
        			}
        		}
        		else if (set.getG1().getWinner() == 1) {
        			g1WinnerPid = set.getG1().getPlayer1Data().getUserID();
        		}
        		else {
        			g1WinnerPid = set.getG1().getPlayer2Data().getUserID();
        		}
        		// if a player won both games
        		if (g1WinnerPid == g2WinnerPid) {
        			result = winnerSetPos;
        		}
        		else {
        			result = 0;
        		}
        		
        		if (result == 0) {
        			setMsg = "set over, set is a draw"; 
        		}
        		else {
        			setMsg = "set over, " + winnerPlayer + ", wins the set!";
        		}
        		
        		set.setWinner(result);
        		set.setStatus(LiveSet.STATUS_COMPLETED);
        		set.setCompletionDate(new Date());
        		
        		newStatus = DSGGameStateTableEvent.NO_GAME_IN_PROGRESS;
        		gameInSet = 0;
        	}
        }

        String txt = "";
        if (draw) {
        	txt = "game over, game is a draw";
        	if (rated && setMsg != null) {
        		txt += ". " + setMsg;
        	}
        }
        else {
            winner = getPlayerSeat(winnerPlayer);

            if (resign) {
            	txt = "game over, " + loserPlayer + " resigns"; 
            }
            else if (timeup) {
            	txt = "game over, " + loserPlayer + " has run out of time";
            }
            else if (forceResign) {
            	txt = "game over, " + loserPlayer + " was forced to resign";
            }
            else {
            	txt = "game over, " + winnerPlayer + " wins the game";
            }
            
        	if (rated && setMsg != null) {
        		txt += ". " + setMsg;
        	}
        }
		changeGameState(newStatus, txt, winnerPlayer, gameInSet);
		
        updateDatabaseAfterGameOverInSeparateThread(
        	winnerPlayer, loserPlayer, winner, set, gameStatus);

        if (noHumanPlayersInTable()) {
            removeAllComputers();
        }

        if (rated && set != null && set.isComplete()) {
        	set = null;//restart set
        }

		gameStarted = false;
	}
	
	public void handleResign(DSGResignTableEvent resignEvent) {
		
		int error = NO_ERROR;
		if (!isPlayerInTable(resignEvent.getPlayer())) {
			error = DSGTableErrorEvent.NOT_IN_TABLE;
		}
		else {
			int seat = getPlayerSeat(resignEvent.getPlayer());
			if (seat == NOT_SITTING) {
				error = DSGTableErrorEvent.NOT_SITTING;
			}
			//note that this implies players can resign when
			//waiting for another player to return OR
			//while waiting for 2nd game to start
			else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
				error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
			}
			else {
				gameOver(false, playingPlayers[3 - seat].getName(), 
					resignEvent.getPlayer(), true, false, false);
			}
		}
		

		if (error != NO_ERROR) {
			dsgEventRouter.routeEvent(
				new DSGResignTableErrorEvent(resignEvent.getPlayer(), tableNum, error),
				resignEvent.getPlayer());
		}
	}


	public void handleForceCancelResign(DSGForceCancelResignTableEvent forceEvent) {
		
		int error = NO_ERROR;
		if (!isPlayerInTable(forceEvent.getPlayer())) {
			error = DSGTableErrorEvent.NOT_IN_TABLE;
		}
		else {
			int seat = getPlayerSeat(forceEvent.getPlayer());
			if (seat == NOT_SITTING) {
				error = DSGTableErrorEvent.NOT_SITTING;
			}
			else if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
				error = DSGTableErrorEvent.GAME_IN_PROGRESS;
			}
			else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
				error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
			}
			else if (waitingForPlayerToReturnTimeUp == false) {
				error = DSGTableErrorEvent.UNKNOWN;
			}
			else {
				if (forceEvent.forcedCancel()) {
					cancelGame(LiveSet.STATUS_CANCEL_SINGLE_DISCONNECT);
                    
                    if (noHumanPlayersInTable()) {
                        removeAllComputers();
                    }
				}
				else {
					String winner = sittingPlayers[seat].getName();
					String loser = null;
					for (int i = 1; i < playingPlayers.length; i++) {
						if (!playingPlayers[i].getName().equals(winner)) {
							loser = playingPlayers[i].getName();
							break;
						}
					}
					gameOver(false, winner, loser, false, false, true);
				}
			}
		}

		if (error != NO_ERROR) {
			dsgEventRouter.routeEvent(
				new DSGForceCancelResignTableErrorEvent(forceEvent.getPlayer(), tableNum, error, forceEvent.getAction()),
				forceEvent.getPlayer());
		}
	}

    public void handleTimeUp(DSGTimeUpTableEvent timeUpEvent) {
        
        int error = NO_ERROR;
        if (!isPlayerInTable(timeUpEvent.getPlayer())) {
            error = DSGTableErrorEvent.NOT_IN_TABLE;
        }
        else {
            int seat = getPlayerSeat(timeUpEvent.getPlayer());
            if (seat == NOT_SITTING) {
                error = DSGTableErrorEvent.NOT_SITTING;
            }
            else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
                error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
            }
            else {
                if (timers[seat].getMinutes() <= 0 &&
                    timers[seat].getSeconds() <= 0) {
                    
                	gameOver(false, playingPlayers[3 - seat].getName(),
                		timeUpEvent.getPlayer(), false, true, false);
                }
                else {
                    log4j.info(psid() + "Invalid time up call, timer says time remains");
                }
            }
        }
        
        if (error != NO_ERROR) {
            log4j.info(psid() + "Time up event - error " + error + ", stopping timers");
            for (int i = 1; i < timers.length; i++) {
                timers[i].stop();
            }
        }
    }

    public void handleAddAI(DSGAddAITableEvent addEvent) {

        int status = NO_ERROR;
        DSGPlayerData adder = getPlayerInTable(addEvent.getPlayer());
        if (adder == null) {
            status = DSGTableErrorEvent.NOT_IN_TABLE;
        }
        else if (!adder.isAdmin() && !isPlayerOwner(addEvent.getPlayer())) {
            status = DSGTableErrorEvent.NOT_TABLE_OWNER;
        }
        else if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
            status = DSGTableErrorEvent.GAME_IN_PROGRESS;
        }
        else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
            status = DSGTableErrorEvent.WAIT_GAME_TWO_OF_SET;
        }
        else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
            status = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
        }
        // happens if invite dialog is open, game is changed, invite sent
        else if (!addEvent.getAIData().isValidForGame(game.getId())) {
            status = DSGTableErrorEvent.UNKNOWN;
        }
        else {
            playersInvited.add(addEvent.getAIData().getUserIDName());
            aiController.addAIPlayer(addEvent);
        }
        
        if (status != NO_ERROR) {
            log4j.info(psid() + "handleAddAI failed.");
        }
    }

    public void handleBoot(DSGBootTableEvent bootEvent) {
        
        DSGPlayerData booter = getPlayerInTable(bootEvent.getPlayer());
        DSGPlayerData bootee = getPlayerInTable(bootEvent.getPlayerToBoot());
        int status = NO_ERROR;
        if (booter == null || bootee == null) {
            status = DSGTableErrorEvent.NOT_IN_TABLE;
        }
        else if (!booter.isAdmin() && !isPlayerOwner(bootEvent.getPlayer())) {
            status = DSGTableErrorEvent.NOT_TABLE_OWNER;
        }
        else {
            if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS ||
            	state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
                int seat = getPlayerSeat(bootEvent.getPlayerToBoot());
                if (seat != NOT_SITTING) {
                    status = DSGTableErrorEvent.GAME_IN_PROGRESS;
                }
            }
        }
        
        if (status == NO_ERROR) {
            boolean booted = !bootee.isComputer();
            
            exit(bootEvent.getPlayerToBoot(), booted);

            // remove the computer
            if (bootee.isComputer()) {
                // remove ai
                aiController.removeAIPlayer(bootEvent.getPlayerToBoot(), tableNum);
            }
            else {

                // don't allow player in for another 5 minutes
                bootTimes.put(bootEvent.getPlayerToBoot(), System.currentTimeMillis() + 1000 * 60 * 5);
                
                // or let the player know they were booted
                dsgEventRouter.routeEvent(bootEvent, bootEvent.getPlayerToBoot());
            }
        }
        else {
            dsgEventRouter.routeEvent(
                new DSGBootTableErrorEvent(bootEvent.getPlayer(), tableNum,
                bootEvent.getPlayerToBoot(), status),
                bootEvent.getPlayer());
        }
    }
	
    public void handleInvite(DSGInviteTableEvent inviteEvent) {

        int status = NO_ERROR;
        DSGPlayerData actor = getPlayerInTable(inviteEvent.getPlayer());
        if (actor == null) {
            status = DSGTableErrorEvent.NOT_IN_TABLE;
        }
        else if (!actor.isAdmin() && !isPlayerOwner(inviteEvent.getPlayer())) {
            status = DSGTableErrorEvent.NOT_TABLE_OWNER;
        }
        else if (isPlayerInTable(inviteEvent.getPlayerToInvite())) {
            status = DSGTableErrorEvent.ALREADY_IN_TABLE;
        }
        else if (!isPlayerInMainRoom(inviteEvent.getPlayerToInvite())) {
            status = DSGTableErrorEvent.UNKNOWN;
        }
        else {

            // check that player is not computer
            // inviting computers is handled by DSGAddAITableEvent's
            DSGPlayerData invitee = getPlayerInMainRoom(inviteEvent.getPlayerToInvite());
            if (invitee.isComputer()) {
                status = DSGTableErrorEvent.UNKNOWN;
            }
            // check that not ignoring invites from this player
            else {
            	try {
            		DSGIgnoreData ignore = dsgPlayerStorer.getIgnoreData(
                    	invitee.getPlayerID(), actor.getPlayerID());
            		
            		if (ignore != null && ignore.getIgnoreInvite()) {
        				status = DSGTableErrorEvent.UNKNOWN;
        				log4j.debug("ignore invite");

        				dsgEventRouter.routeEvent(
        					new DSGSystemMessageTableEvent(
                                tableNum, inviteEvent.getPlayerToInvite() +
                                " is ignoring your invitations, they will not appear."),
                                inviteEvent.getPlayer());
            		}
            	} catch (DSGPlayerStoreException dpse) {
            		log4j.error("Error checking ignore.", dpse);
            	}
            }
        }
        
        if (status == NO_ERROR) {
            // if player already invited, don't add again, and don't send 
        	// invite to player agains.  if player rejects the invitation then
        	// it will be possible to invite them again
            if (!playersInvited.contains(inviteEvent.getPlayerToInvite())) {
                playersInvited.add(inviteEvent.getPlayerToInvite());
                
                // if booted earlier, allow back in
                bootTimes.remove(inviteEvent.getPlayerToInvite());
                
                // send invite to player
                dsgEventRouter.routeEvent(
                    inviteEvent, inviteEvent.getPlayerToInvite());
            }
        }
        else {
            dsgEventRouter.routeEvent(
                new DSGInviteTableErrorEvent(inviteEvent.getPlayer(), tableNum,
                inviteEvent.getPlayerToInvite(), status),
                inviteEvent.getPlayer());
        }
    }
	
    public void handleInviteResponse(DSGInviteResponseTableEvent inviteResponseEvent) {
        
		int status = NO_ERROR;
        DSGPlayerData actor = getPlayerInTable(inviteResponseEvent.getToPlayer());
		// make sure invitor can receive response, if not in table ignore
        if (actor == null) {
            status = DSGTableErrorEvent.NOT_IN_TABLE;
        }
		// if invitee already in table, ignore
        else if (isPlayerInTable(inviteResponseEvent.getPlayer())) {
            status = DSGTableErrorEvent.ALREADY_IN_TABLE;
        }
        else {
        	// if decline invite, remove player from invite list so that later
        	// player can be invited again
        	if (!inviteResponseEvent.getAccept()) {
        		playersInvited.remove(inviteResponseEvent.getPlayer());
        	}
        	
			dsgEventRouter.routeEvent(inviteResponseEvent, 
				inviteResponseEvent.getToPlayer());
        }

        // store the ignore, if the actor is no longer in the room then ignore
        if (inviteResponseEvent.getIgnore()) {
        	try {
        		// if left table and is guest
        		if (actor == null && inviteResponseEvent.getToPlayer().startsWith("guest")) {
        			actor = getPlayerInMainRoom(inviteResponseEvent.getToPlayer());
        			if (actor == null) {
        				return; //guest has left so no need to ignore
        			}
        		}
        		// if left table
        		if (actor == null) { 
        			actor = dsgPlayerStorer.loadPlayer(inviteResponseEvent.getToPlayer());
        		}
        		DSGPlayerData invitee = getPlayerInMainRoom(inviteResponseEvent.getPlayer());
        		DSGIgnoreData d = dsgPlayerStorer.getIgnoreData(invitee.getPlayerID(),
        			actor.getPlayerID());
        		if (d != null) {
        			d.setIgnoreInvite(true);
        			dsgPlayerStorer.updateIgnore(d);
        		}
        		else {
	        		d = new DSGIgnoreData();
	        		d.setPid(invitee.getPlayerID());
	        		d.setIgnorePid(actor.getPlayerID());
	        		d.setIgnoreInvite(true);
        			d.setGuest(actor.isGuest());
	        		dsgPlayerStorer.insertIgnore(d);
        		}
        		
            	dsgEventRouter.routeEvent(new DSGSystemMessageTableEvent(
                        0, "Invites will be ignored from " + inviteResponseEvent.getToPlayer() + 
                        ".  You can manage your ignore settings by double-clicking the user name " +
                        " in the game room, or by clicking 'My Profile' and" +
                        " then 'Preferences' from http://pente.org/"), 
                        inviteResponseEvent.getPlayer());
            	
        	} catch (DSGPlayerStoreException dpse) {
        		log4j.error("Error server table w/ ignore", dpse);
        	}
        }
    }

	public void handleEmailGame(final DSGEmailGameRequestTableEvent emailGameEvent) {

        int error = NO_ERROR;
		if (!isPlayerInTable(emailGameEvent.getPlayer())) {
			error = DSGTableErrorEvent.NOT_IN_TABLE;
		}
		else if (getPlayerInTable(emailGameEvent.getPlayer()).isGuest()) {
			error = DSGTableErrorEvent.GUEST_NOT_ALLOWED;
		}
		else { 

            if (lastGame == null) {
                error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
                dsgEventRouter.routeEvent(
                    new DSGEmailGameReplyTableEvent(
                        emailGameEvent.getPlayer(),
                        tableNum,
                        "no game to email"),
                    emailGameEvent.getPlayer());
            }
            else {
                // run the code that gets data out of the database
                // and actually sends the email in a separate thread since
                // it is pretty slow
                new Thread(new Runnable() {
                    public void run() {
                        DSGPlayerData dsgPlayerData = null;
                        String emailReply = null;
                        boolean errorOccurred = false;
	                    try {
	                        dsgPlayerData = dsgPlayerStorer.loadPlayer(emailGameEvent.getPlayer());
	                    } catch (DSGPlayerStoreException d) {
	                        log4j.error("Error loading " + emailGameEvent.getPlayer() + "'s email for emailing game.");
	                        emailReply = "error loading email address from database";
                            errorOccurred = true;
	                    }
	 
                        if (errorOccurred) {
                        }
	                    else if (dsgPlayerData == null) {
	                        emailReply = "no email address found for " + emailGameEvent.getPlayer();
	                    }
	                    else if (!dsgPlayerData.getEmailValid()) {
	                        emailReply = "email address " + dsgPlayerData.getEmail() + " is invalid, please update it and try again";
	                    }
						else {
	
			                StringBuffer gameBuffer = new StringBuffer();
			                gameFormat.format(lastGame, gameBuffer);
		
	                        String attachmentTitle = lastGame.getPlayer1Data().getUserIDName() +
	                                                 " vs. " +
	                                                 lastGame.getPlayer2Data().getUserIDName() +
	                                                 " " +
	                                                 dateFormat.format(lastGame.getDate()) +
                                                     ".txt";

                            String fromEmail = System.getProperty("mail.smtp.user");
            
				            try {
				                
				                SendMail2.sendMailSaveInDb(
                                    "Pente.org",
                                    fromEmail,
                                    dsgPlayerData.getPlayerID(),
				                    dsgPlayerData.getName(),
				                    dsgPlayerData.getEmail(),
				                    "Pente.org Game",
				                    gameBuffer.toString(),
				                    true,
				                    attachmentTitle,
                                    returnEmailStorer);
                                emailReply = "game successfully sent to " + dsgPlayerData.getEmail();
				           
				            } catch (Throwable t) {
				                log4j.info("Problem emailing game for " + emailGameEvent.getPlayer(), t);
                                emailReply = "error sending email to " + dsgPlayerData.getEmail() + ", please make sure your address is correct and try again";
				            }
						}
                        
                        dsgEventRouter.routeEvent(
                            new DSGEmailGameReplyTableEvent(
                                emailGameEvent.getPlayer(),
                                tableNum,
                                emailReply),
                            emailGameEvent.getPlayer());
                    }
                }, "handleEmailGame").start();
            }
        }
	}

    private GameEventData getGameEvent(int game) {
        // set the event based on passed in game events and game played
        for (Iterator it = serverData.getGameEvents().iterator(); it.hasNext();) {
            GameEventData d = (GameEventData) it.next();
            if (d.getGame() == game) {
                return d;
            }
        }
        return null;
    }
    
	/** Returns a GameData representation of the current game
	 *  @return GameData The GameData for the current game
	 */
	public GameData getGameData(int winner, String status) {
		if (gridState == null || gridState.getNumMoves() == 0 || !gameStarted) {
			return null;
		}
		else {
			GameData gameData = new DefaultGameData();
			lastGame = gameData;
            // if the game is complete use the date/time the game completed
            if (gameTime != null) {
                gameData.setDate(gameTime);
            }
            // else use the current date/time
            else {
    			gameData.setDate(new Date());
            }
            gameData.setGame(game.getName());
			gameData.setSite("Pente.org");
            
            // set the event based on passed in game events and game played
            gameData.setEvent(getGameEvent(game.getId()).getName());
            if (serverData.isTournament()) {
                gameData.setRound(String.valueOf(tourneyMatch.getRound()));
                gameData.setSection(String.valueOf(tourneyMatch.getSection()));
            }
            
			gameData.setInitialTime(initialMinutes);
			gameData.setIncrementalTime(incrementalSeconds);
			gameData.setRated(rated);
			if (rated && set != null) {
				gameData.setSid(set.getSid());
			}
			gameData.setTimed(timed);
			if (timed) {
				gameData.setMoveTimes(moveTimes);
			}
			
			if (!rated && tableType == DSGChangeStateTableEvent.TABLE_TYPE_PRIVATE) {
				gameData.setPrivateGame(true);
			}
			
			PlayerData p1 = new DefaultPlayerData();
            p1.setType(playingPlayers[1].isComputer() ?
                       PlayerData.COMPUTER :
                       PlayerData.HUMAN);
            if (playingPlayers[1].isGuest()) {
            	p1.setUserIDName("guest");
            }
            else {
				p1.setUserIDName(playingPlayers[1].getName());
	            try {
	                DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(playingPlayers[1].getName());
	                if (dsgPlayerData != null) {
	                    DSGPlayerGameData dsgPlayerGameData = 
	                        dsgPlayerData.getPlayerGameData(game.getId());
	
	                    p1.setRating((int) dsgPlayerGameData.getRating());
	                }
	            } catch (DSGPlayerStoreException d) {
	                log4j.error("Error loading " + playingPlayers[1].getName() + "'s rating for getting game data.");
	            }
			}
			
			//get rating from database and set rating
			gameData.setPlayer1Data(p1);
			
			PlayerData p2 = new DefaultPlayerData();
            p2.setType(playingPlayers[2].isComputer() ?
                       PlayerData.COMPUTER :
                       PlayerData.HUMAN);
            if (playingPlayers[2].isGuest()) {
            	p2.setUserIDName("guest");
            }
            else {
            	p2.setUserIDName(playingPlayers[2].getName());
				//get rating from database and set rating
	            try {                
	                DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(playingPlayers[2].getName());
	                if (dsgPlayerData != null) {
	                    DSGPlayerGameData dsgPlayerGameData = 
	                        dsgPlayerData.getPlayerGameData(game.getId());
	
	                    p2.setRating((int) dsgPlayerGameData.getRating());
	                }
	            } catch (DSGPlayerStoreException d) {
	                log4j.error("Error loading " + playingPlayers[2].getName() + "'s rating for getting game data.");
	            }
            }
			gameData.setPlayer2Data(p2);

            gameData.setWinner(winner);
            
            if (game == GridStateFactory.DPENTE_GAME ||
                game == GridStateFactory.SPEED_DPENTE_GAME ||
					game == GridStateFactory.DKERYO_GAME || game == GridStateFactory.SPEED_DKERYO_GAME) {
                gameData.setSwapped(((PenteState) gridState).didDPenteSwap());
            }

            gameData.setStatus(status);
            
            for (int i = 0; i < gridState.getNumMoves(); i++) {
                gameData.addMove(gridState.getMove(i));
            }

			return gameData;
		}
	}
    
    private void swapSeats() {
        // only swap if both players still sitting
        // (if forced resign, don't swap)
        // (if d-pente and already swapped, don't swap back)
        if (game == GridStateFactory.DPENTE_GAME || game == GridStateFactory.SPEED_DPENTE_GAME
				|| game == GridStateFactory.DKERYO_GAME || game == GridStateFactory.SPEED_DKERYO_GAME) {
            if (((PenteState) gridState).didDPenteSwap()) return; // already swapped seats
        }
        
        if (!anyComputersSitting() && allPlayersSitting()) {
            broadcastMainRoom(new DSGStandTableEvent(sittingPlayers[1].getName(), tableNum));
			broadcastMainRoom(new DSGStandTableEvent(sittingPlayers[2].getName(), tableNum));
            DSGPlayerData tmp = sittingPlayers[1];
            sittingPlayers[1] = sittingPlayers[2];
            sittingPlayers[2] = tmp;
			broadcastMainRoom(new DSGSitTableEvent(sittingPlayers[1].getName(), tableNum, 1));
			broadcastMainRoom(new DSGSitTableEvent(sittingPlayers[2].getName(), tableNum, 2));

            broadcastTable(new DSGSystemMessageTableEvent(
                tableNum,
                "server switched your seats for the next game"));
        }
    }
    
    
    private class EndGameRunnable implements Runnable {

		class Data {
			GameData gameData;
			String winnerPlayer;
			String loserPlayer;
			int game;
			int winner;
			LiveSet set;
			public Data(GameData gameData, String winnerPlayer,
				String loserPlayer, int game, int winner, LiveSet set) {
				this.gameData = gameData;
				this.game = game;
				this.winnerPlayer = winnerPlayer;
				this.loserPlayer = loserPlayer;
				this.winner = winner;
				this.set = set;
			}
		}
		public String getName() {
			return "GS-EndGame";
		}

		private volatile boolean alive = true;
		private SynchronizedQueue queue = new SynchronizedQueue();
		
		public void endGame(GameData gameData, String winnerPlayer,
				String loserPlayer, int game, int winner, LiveSet localSet) {
			log4j.info("EndGameRunnable.endGame(" + tableNum + ", " + winnerPlayer + ", " + loserPlayer + ", " + game + ", " + winner + ")");
			queue.add(new Data(gameData, winnerPlayer, loserPlayer, game, winner, localSet));
		}
		public String toString() {
			return getName() + " queue.size()=" + queue;
		}
		public void kill() {
			alive = false;
		}
		public void reset() {
			alive = true;
		}

		public void run() {

			while (alive) {
				log4j.debug(getName() + " run");
				Data data = null;
	
				try {
					data = (Data) queue.remove();
					
					updateDatabaseAfterGameOver(
	                    data.gameData, data.winnerPlayer, data.loserPlayer, 
	                    data.game, data.winner, data.set);
					
				} catch (InterruptedException e) {
					// no action, if killed thread will see in next loop
					log4j.error(getName() + " Interrupted, ");
				} catch (Throwable t) {
					log4j.error("Unknown error ending game", t);
					alive = false;//stop thread, will cause new end game runnable
					// to be created for the next game
				}
			}
		}
    }
    
    private void updateDatabaseAfterGameOverInSeparateThread(
        final String winnerPlayer, final String loserPlayer, int winner, 
        LiveSet localSet, String status) {
        
        if (serverData.isTournament()) {
            broadcastTable(new DSGSystemMessageTableEvent(
                tableNum,
                "updating tournament standings, please wait"));
        }

        final int localGame = game.getId();
        final GameData gameData = getGameData(winner, status);

        serverStatsHandler.gamePlayed();

        // don't update db in new thread if tournament game because
        // want to ensure tournament standings updated properly
        
        if (serverData.isTournament()) {
            updateDatabaseAfterGameOver(
                gameData, winnerPlayer, loserPlayer, localGame, winner, set);
        }
        else {
            swapSeats();
            
            startGameOverThread();
            gameOverRunnable.endGame(gameData, 
            	winnerPlayer, loserPlayer, localGame, winner, set);
            
            // new thread not needed now since db will not be as slow due
            // to fact that long queries are run against dsg_ro db
//            new Thread(new Runnable() {
//                public void run() {
//                	try { Thread.sleep(5000);} catch (InterruptedException e) {}
//                	System.out.println("done sleeping");
//                    updateDatabaseAfterGameOver(
//                        gameData, winnerPlayer, loserPlayer, localGame, winner);
//                }
//            }, "updateDatabaseAfterGameOver").start();
        }
    }
    
    /** I suppose its possible that if a player finished 2 games at near
     *  the same time, one games stats updates could override the others
     */
    private void updateDatabaseAfterGameOver
        (GameData gameData, String winnerPlayer, String loserPlayer, 
        int game, int localWinner, LiveSet localSet) {

        boolean isComputerGame = false;
        boolean isAllHumanGame = false;
		
        try {
        	// this is ok if ending a set in between games by forcing resign
        	// or resigning i guess
            if (gameData == null) {
                log4j.error(psid() + "Game over but game data is null");
				return;
            }
	        else {
                isComputerGame = 
                    playingPlayers[1].isComputer() || 
                    playingPlayers[2].isComputer();
                isAllHumanGame = !isComputerGame;
                
                if (isAllHumanGame) {
                    try {
                    	gameFileStorer.storeGame(gameData);
                    } catch (Throwable t) {
                        log4j.error(psid() + "Problem saving game in file", t);
                    }
                }
                gameData.setGameID(0);
                
                PlayerData p1 = playerDbStorer.loadPlayer(
                    gameData.getPlayer1Data().getUserIDName(),
                    gameData.getSite());
                PlayerData p2 = playerDbStorer.loadPlayer(
                    gameData.getPlayer2Data().getUserIDName(),
                    gameData.getSite());
                    
                p1.setType(playingPlayers[1].isComputer() ?
                           PlayerData.COMPUTER :
                           PlayerData.HUMAN);
                p1.setRating(gameData.getPlayer1Data().getRating());

                p2.setType(playingPlayers[2].isComputer() ?
                           PlayerData.COMPUTER :
                           PlayerData.HUMAN);
                p2.setRating(gameData.getPlayer2Data().getRating());
                gameData.setPlayer1Data(p1);
                gameData.setPlayer2Data(p2);
				
				// don't store games against ai in db, waste of resources
				if (isAllHumanGame) {
	                gameDbStorer.storeGame(gameData); 
				}
	        }
        } catch (Throwable t) {
            log4j.error(psid() + "Problem saving game in db", t);
        }

        boolean updateRatings = isComputerGame;
        if (gameData.getRated() && localSet != null) {
        	if (localSet.getG1Gid() == 0) {
        		localSet.setG1(gameData);
        	}
        	else {
        		localSet.setG2(gameData);
        		updateRatings = true;
        	}
        	try {
        		dsgPlayerStorer.updateLiveSet(localSet);
        	} catch (DSGPlayerStoreException dpse) {
        		log4j.error(psid() + "Error updating set " + localSet.getSid(), dpse);
        	}
        }

        // rated or game involves computers
        if (updateRatings) {
	        DSGPlayerData winnerPlayerData = null;
	        DSGPlayerData loserPlayerData = null;

            // load the winner and loser player data
	        try {
	            winnerPlayerData = dsgPlayerStorer.loadPlayer(winnerPlayer);
	        } catch (DSGPlayerStoreException e) {
	            log4j.error(psid() + "Problem loading player " + 
	                        winnerPlayer + 
	                        " as winner for game over.", e);
	            return;
	        }
	        try {
	            loserPlayerData = dsgPlayerStorer.loadPlayer(loserPlayer);
	        } catch (DSGPlayerStoreException e) {
	            log4j.error(psid() + "Problem loading player " + 
	                        loserPlayer + 
	                        " as loser for game over.", e);
	            return;
	        }
	
	        if (winnerPlayerData == null) {
	            log4j.error(psid() + "Winner " + winnerPlayer + " not found.");
	            return;
	        }
	        if (loserPlayerData == null) {
	            log4j.error(psid() + "Loser " + loserPlayer + " not found.");
	            return;
	        }

            if (isAllHumanGame) {

                // update the "against computer" copy of the ratings
                // these need to be updated even if the game isn't against a
                // computer to stay current with what your rating "would" be
                // if we included the games against a computer
                // do this BEFORE updating the "viewable" ratings because
                // the initial "against computer" ratings are copied from
                // the "viewable" ratings
                DSGPlayerGameData winnerPlayerGameData = 
                    winnerPlayerData.getPlayerGameData(game, true);
                DSGPlayerGameData loserPlayerGameData = 
                    loserPlayerData.getPlayerGameData(game, true);

				try {
	                GameOverUtilities.updateGameData(
						dsgPlayerStorer,
	                    winnerPlayerData, winnerPlayerGameData,
	                    loserPlayerData, loserPlayerGameData, 
	                    localSet.getWinner() == 0, 64);
				} catch (DSGPlayerStoreException dpse) {
					log4j.error(psid() + "Error updating game data.", dpse);
				}

                // update game statistics for the viewable copy
    	        winnerPlayerGameData = 
    	            winnerPlayerData.getPlayerGameData(game, false);
    	        loserPlayerGameData = 
    	            loserPlayerData.getPlayerGameData(game, false);

                double winnerRatingBefore = winnerPlayerGameData.getRating();
                double loserRatingBefore = loserPlayerGameData.getRating();

				try {
	                GameOverUtilities.updateGameData(
						dsgPlayerStorer,
	                    winnerPlayerData, winnerPlayerGameData,
	                    loserPlayerData, loserPlayerGameData, 
	                    localSet.getWinner() == 0, 64);
				} catch (DSGPlayerStoreException dpse) {
					log4j.error(psid() + "Error updating game data.", dpse);
				}
                    
                double winnerRatingAfter = winnerPlayerGameData.getRating();
                double loserRatingAfter = loserPlayerGameData.getRating();
    
                // don't send ratings change for drawn set
                if (localSet.getWinner() != 0) {
	                // and send out a message with the rating changes
	                String winnerString = winnerPlayerData.getName() + "'s rating has " +
	                    "gone from " + 
	                    Math.round(winnerRatingBefore) + " to " +
	                    Math.round(winnerRatingAfter) + ".";
	    
	                String loserString = loserPlayerData.getName() + "'s rating has " +
	                    "gone from " + 
	                    Math.round(loserRatingBefore) + " to " +
	                    Math.round(loserRatingAfter) + ".";
	    
	                broadcastTable(new DSGSystemMessageTableEvent(
	                    tableNum, winnerString));
	                broadcastTable(new DSGSystemMessageTableEvent(
	                    tableNum, loserString));
    

                    if (serverData.getName() != null && serverData.getName().startsWith("King of the Hill")) {
						Hill hill = kothStorer.getHill(game);
						long oldKingPid = (hill!= null)?hill.getKing():0;
                        long winnerPid = winnerPlayerData.getPlayerID();
                        long loserPid = loserPlayerData.getPlayerID();
                        int stepsBetween = kothStorer.stepsBetween(game, winnerPid, loserPid);
                        if (stepsBetween*stepsBetween < 5) {
                            kothStorer.addPlayer(game, winnerPid);
                            kothStorer.addPlayer(game, loserPid);
                            kothStorer.movePlayersUpDown(game, winnerPid, loserPid);
                            if (hill == null) {
								hill = kothStorer.getHill(game);
							}
							long kingPid = (hill!= null)?hill.getKing():0;
							if (kingPid != oldKingPid && kingPid != 0) {
								try {
									broadcastTable(new DSGSystemMessageTableEvent(
											tableNum,
											"KotH has been updated, all hail King " + dsgPlayerStorer.loadPlayer(kingPid).getName()));
								} catch (DSGPlayerStoreException e) {
									log4j.error("ServerTable: error getting King: " + e);
								}
							} else {
								broadcastTable(new DSGSystemMessageTableEvent(
										tableNum,
										"KotH has been updated"));
							}
                        } else {
                            broadcastTable(new DSGSystemMessageTableEvent(
                                tableNum,
                                "KotH has not been updated, players are too far apart"));
                        }
                    }
                }
				if (serverData.getName() != null && serverData.getName().startsWith("King of the Hill")) {
                    long winnerPid = winnerPlayerData.getPlayerID();
                    long loserPid = loserPlayerData.getPlayerID();
                    kothStorer.updatePlayerLastGameDate(game, winnerPid);
                    kothStorer.updatePlayerLastGameDate(game, loserPid);
                }

                        

            }
            // we're playing against a computer (or 2 computers against each other)
            // in this case we always get the "computer" copy of the stats
            // if the player is human then the against computer copy of the stats
            // is updated and no one knows about it, they are only kept to keep
            // the computer's ratings accurate.  if the player is a computer
            // then we get the "computer" copy because this is the only stats
            // kept for the computer
	        else {
                
                DSGPlayerGameData winnerPlayerGameData =
                    winnerPlayerData.getPlayerGameData(game, true);

                DSGPlayerGameData loserPlayerGameData =
                    loserPlayerData.getPlayerGameData(game, true);

				try {
	                GameOverUtilities.updateGameData(
						dsgPlayerStorer,
	                    winnerPlayerData, winnerPlayerGameData,
	                    loserPlayerData, loserPlayerGameData, 
	                    localWinner == 0, 32);
				} catch (DSGPlayerStoreException dpse) {
					log4j.error(psid() + "Error updating game data.", dpse);
				}
            }
        }

        if (serverData.isTournament()) {

            try {
            	int localWinner2 = localWinner;
            	boolean swapped = false;
            	// for dpente games, don't swap player ids
            	// just record game as being won by correct id
            	if (game == GridStateFactory.DPENTE ||
            		game == GridStateFactory.SPEED_DPENTE ||
						game == GridStateFactory.DKERYO || game == GridStateFactory.SPEED_DKERYO) {
            		
            		if (((PenteState) gridState).didDPenteSwap()) {
            			if (localWinner2 != 0) { //draw
            			    localWinner2 = 3 - localWinner;
                        }
            			swapped = true;
            		}
            	}
            	
                tourneyMatch.setGid(gameData.getGameID());
                tourneyMatch.setResult(localWinner2);
                resources.getTourneyStorer().updateMatch(
                    tourneyMatch);

                broadcastTable(new DSGSystemMessageTableEvent(
                    tableNum,
                    "tournament standings have been updated"));
                
                // switch p id's and try to get match again
                // if successful, swap player seats and send message
                // out, otherwise, send message that tournament match is
                // complete
                
                long newPid1 = playingPlayers[2].getPlayerID();
                long newPid2 = playingPlayers[1].getPlayerID();
                if (swapped) {
                	long temp = newPid1;
                	newPid1 = newPid2;
                	newPid2 = temp;
                }
                TourneyMatch newMatch = resources.getTourneyStorer().getUnplayedMatch(
                    newPid1, newPid2,
                    getGameEvent(game).getEventID());
                if (newMatch == null) {
                    broadcastTable(new DSGSystemMessageTableEvent(
                        tableNum,
                        "this tournament match is complete!"));
   
                    } else if (!swapped) {
                        swapSeats();
                    }


                    // print status of match if playing speed tourney
                Tourney tourney = server.getTourney();
                if (tourney.isSpeed()) {
                    TourneySection s = tourney.getRound(tourneyMatch.getRound()).getSection(
                            tourneyMatch.getSection());
                    if (s instanceof SingleEliminationSection) {
                        SingleEliminationSection ses = (SingleEliminationSection) s;
                        SingleEliminationMatch m = ses.getSingleEliminationMatch(tourneyMatch);
                        broadcastTable(new DSGSystemMessageTableEvent(
                            tableNum,
                            m.getMatchStr()));
                        broadcastMainRoom(new DSGSystemMessageTableEvent(
                            0, "Match update - " + m.getMatchStr()));
                    }
                }
                // create tables and add players to them, hmm, can't create
                // a new table from here, must send event to Server
                // server.routeEventToTable(joinevent p1, NEW_TABLE)
                // server.routeEventToTable(joinevent p2, new table num),need new method in server i guess
                
            } catch (Throwable t) {
                log4j.error("Error updating tourney match for " +
                    gameData.getGameID(), t);
            }
        }
    }
}
