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
    protected Timer closeTableTimer;
    

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

	protected void startGameOverThread() {
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
        super.destroy();
        
        
//        removeAllComputers();
//
//        // destroy old timers
//        if (timers != null) {
//            for (int i = 0; i < timers.length; i++) {
//                if (timers[i] != null) {
//                    timers[i].destroy();
//                }
//            }
//        }
//		if (waitingForPlayerToReturnTimer != null) {
//			waitingForPlayerToReturnTimer.destroy();
//		}
//        
//		if (gameOverThread != null && gameOverRunnable != null) {
//			gameOverRunnable.kill();
//			gameOverThread.interrupt();
//		}
	}

    protected String psid() { return "[" + sid + "] "; };
    

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


	/** Just send out status of table to player
	 */
	public void handleMainRoomJoin(DSGJoinMainRoomEvent mainRoomEvent) {

        if (playersInTable != null && playersInTable.size() == 0) {
            server.removeTable(tableNum);
            return;
        }
        
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

	protected void sit(String player, int seat) {
		sittingPlayers[seat] = getPlayerInTable(player);

		broadcastMainRoom(new DSGSitTableEvent(player, tableNum, seat));

        // if a computer is sitting, make the game unrated and untimed
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

    protected boolean isValidTourneyMatch() {

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
    
  
	protected boolean isPlayerOwner(String player) {
        return player.equals(getOwner());
	}
	protected String getOwner() {
		if (playersInTable.isEmpty()) {
			return null;
		}
		else {
//		    for (int i = playersInTable.size()-1; i>-1; i--) {
//                DSGPlayerData d = (DSGPlayerData) playersInTable.elementAt(i);
//                if (d == null) {
//                    playersInTable.remove(i);
//                } else if (d.isHuman()) {
//                    return d.getName();
//                }
//            }
//            return null;
            for (int i = 0; i<playersInTable.size(); i++) {
                DSGPlayerData d = (DSGPlayerData) playersInTable.elementAt(i);
                if (d != null && d.isHuman()) {
                    return d.getName();
                }
            }
            return null;
		}
	}

	@Override
    protected void startGame() {
	    super.startGame();
    }

    @Override
	public void handleCancelRequest(DSGCancelRequestTableEvent cancelRequestEvent) { }

    @Override
	public void handleCancelReply(DSGCancelReplyTableEvent cancelReplyEvent) { }
	
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
	
	
	/** This method assumes that all validation has already
	 *  been done that it is ok to exit
	 */
	protected void exit(String player, boolean booted) {
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
        
		for (int i = playersInTable.size()-1; i>-1; i--) {
		    if (playersInTable.elementAt(i) == null) {
		        playersInTable.remove(i);
            }
        }
        if (playersInTable.isEmpty()) {
            server.removeTable(tableNum);
        }
	}

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

    
    
    protected void updateDatabaseAfterGameOverInSeparateThread(
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
    
}
