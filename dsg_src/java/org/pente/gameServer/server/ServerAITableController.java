/** ServerAITableController.java
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

import org.pente.gameServer.core.AIData;
import org.pente.gameServer.event.*;

/** AI Controller to handle an AI player in a table.
 *  Interfaces between the ServerTable and an AIPlayer instance.
 *  Note, a new AIPlayer instance is created for each game but
 *  this controller sticks around for multiple games.
 */
public class ServerAITableController implements 
    DSGEventListener,
    ThreadedAIPlayerCallback {
 
    private Server server;
    private AIData aiData;
    private int tableNum;
    private boolean joinedTable;

    private int state;
    private static final int NO_MOVE = -1;
    private int waitingMove = NO_MOVE;

    private AIPlayer aiPlayer;

    public ServerAITableController(Server server) {
        this.server = server;
        
        state = DSGGameStateTableEvent.NO_GAME_IN_PROGRESS;
    }
    
    public synchronized int addAIPlayer(DSGAddAITableEvent addEvent) {

        if (!addEvent.getAIData().isValid()) {
            return DSGTableErrorEvent.UNKNOWN;
        }

        aiData = addEvent.getAIData();
        tableNum = addEvent.getTable();

        return ServerTable.NO_ERROR;
    }
    
    public synchronized void removeAIPlayer() {
        
        if (aiPlayer != null) {
            aiPlayer.destroy();
        }
        
        // exit table event
        server.routeEventToTable(new DSGExitTableEvent(
            aiData.getUserIDName(), tableNum, true, false), tableNum);
    }

    public void joinTable() {
        joinedTable = true;
        server.routeEventToTable(new DSGJoinTableEvent(
            aiData.getUserIDName(), tableNum), tableNum);
        server.routeEventToTable(new DSGSitTableEvent(
            aiData.getUserIDName(), tableNum, aiData.getSeat()), tableNum);
        server.routeEventToTable(new DSGTextTableEvent(aiData.getUserIDName(),
            tableNum, "* " + aiData.getUserIDName() + " was created by Mark " +
            "Mammel - http://mysite.verizon.net/msmammek/marksfiv.html"),
            tableNum);
    }

    public synchronized void eventOccurred(DSGEvent dsgEvent) {

        if (dsgEvent instanceof DSGJoinMainRoomEvent) {
            if (!joinedTable) {
                joinTable();
                joinedTable = true;
            }
        }
        else if (dsgEvent instanceof DSGMoveTableEvent) {
            
            if (state != DSGGameStateTableEvent.GAME_IN_PROGRESS) {
                return;
            }
            
            if (aiPlayer != null) {
                aiPlayer.addMove(((DSGMoveTableEvent) dsgEvent).getMove());
            }
        }
        else if (dsgEvent instanceof DSGMoveTableErrorEvent) {
            // if move rejected because of game going into waiting state
            // then store move in waitingMove variable
            if (((DSGMoveTableErrorEvent) dsgEvent).getError() ==
                DSGMoveTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
                waitingMove = ((DSGMoveTableErrorEvent) dsgEvent).getMove();
            }
        }
        // always accept cancel game requests
        else if (dsgEvent instanceof DSGCancelRequestTableEvent) {
            server.routeEventToTable(new DSGCancelReplyTableEvent(
                aiData.getUserIDName(), tableNum, true), tableNum);
        }
        else if (dsgEvent instanceof DSGUndoRequestTableEvent) {
            if (aiPlayer != null) {
                aiPlayer.undoMove();
            }
        }
        // if player exits and doesn't return in x minutes, always cancel
        else if (dsgEvent instanceof DSGWaitingPlayerReturnTimeUpTableEvent) {

            server.routeEventToTable(
                new DSGForceCancelResignTableEvent(
                    aiData.getUserIDName(), tableNum, 
                    DSGForceCancelResignTableEvent.CANCEL),
                tableNum);
        }
        else if (dsgEvent instanceof DSGGameStateTableEvent) {
                
            int newState = ((DSGGameStateTableEvent) dsgEvent).getState();
            if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS &&
                newState == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
                
                waitingMove = NO_MOVE;
                
                // create and initialize the ai player at the start of
                // every game
                aiPlayer = AIPlayerFactory.getAIPlayerThreaded(aiData, this);
            }
            else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN &&
                     newState == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
                
                // if the ai tried to make a move while the opponent left
                // the table and now the opponent is back, then send the move
                if (waitingMove != NO_MOVE) {

                    server.routeEventToTable(new DSGMoveTableEvent(
                        aiData.getUserIDName(), tableNum, waitingMove),
                        tableNum);

                    waitingMove = NO_MOVE;
                }
            }

            state = newState;
            
            // if we are stopping the game
            if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
                
                // destroy the ai player at the end of every game
                if (aiPlayer != null) {
                    aiPlayer.destroy();
                    aiPlayer = null;
                }
            }
        }
    }
    
    // AIPlayerThreadedCallback
    public synchronized void receiveMove(int move) {

        server.routeEventToTable(new DSGMoveTableEvent(
            aiData.getUserIDName(), tableNum, move),
            tableNum);
    }
    public synchronized void receiveUndoReply(boolean reply) {

        if (reply) {
            server.routeEventToTable(new DSGUndoReplyTableEvent(
                aiData.getUserIDName(), tableNum, true), tableNum);
        }
    }
    // end AIPlayerThreadedCallback
}
