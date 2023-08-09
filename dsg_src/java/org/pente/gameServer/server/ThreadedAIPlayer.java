/**
 * ThreadedAIPlayer.java
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

import java.util.*;

import org.pente.gameServer.event.*;

/** Don't use this class for AIPlayer implementations.
 *  This class is to be used by code that wraps an AIPlayer
 *  implementation to run in a separate thread and communicate
 *  through a callback interface.
 *
 *  The point of having a threaded AI player is that AI's
 *  will certainly take a long time to think and generate a move.
 *  If the AI didn't run in a separate thread it would tie up
 *  the server room.
 *
 *  If a player requests an undo, the AIPlayer is interrupted
 *  and should throw an InterruptedException from getMove().  This threaded
 *  AI player will guarantee that undoMove(), addMove() and getMove()
 *  will never be called at the same time.  However stopThinking() WILL
 *  be called while getMove() is called in another thread.
 */
class ThreadedAIPlayer implements AIPlayer, Runnable {

    private AIPlayer aiPlayer;
    private ThreadedAIPlayerCallback callback;
    private List events;

    private Thread thread;
    private boolean alive;

    private int seat;
    private int moveNum;

    private static final long MOVE_SLEEP_TIME = 5000;

    public ThreadedAIPlayer(
            AIPlayer aiPlayer,
            ThreadedAIPlayerCallback callback) {

        this.aiPlayer = aiPlayer;
        this.callback = callback;

        events = new ArrayList();

        alive = true;
        thread = new Thread(this, "AIPlayerThread");
        thread.start();
    }

    public void init() {
        aiPlayer.init();
    }

    public void setGame(int game) {
        aiPlayer.setGame(game);
    }

    public void setLevel(int level) {
        aiPlayer.setLevel(level);
    }

    public void setSeat(int seat) {
        this.seat = seat;
        aiPlayer.setSeat(seat);
    }

    public void setOption(String optionName, String optionValue) {
        aiPlayer.setOption(optionName, optionValue);
    }

    private boolean isMyTurn() {
        return (moveNum % 2 + 1) == seat;
    }

    public synchronized void addMove(int move) {

        events.add(new DSGMoveTableEvent("", 0, move));
        notifyAll();
    }

    public synchronized void undoMove() {

        events.add(new DSGUndoRequestTableEvent("", 0));
        aiPlayer.stopThinking();
        notifyAll();
    }

    public int getMove() {
        throw new UnsupportedOperationException(
                "getMove() is not supported, instead your AIPlayerCallback " +
                        "will have its receiveMove(int) method called");
    }

    public void stopThinking() {
        aiPlayer.stopThinking();
    }

    public synchronized void destroy() {

        alive = false;
        thread.interrupt();
        aiPlayer.stopThinking();
    }

    public void run() {

        while (alive) {

            // block here until events come in that need to be processed
            DSGEvent event = null;
            synchronized (this) {
                while (events.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // kill the thread
                        if (!alive) {
                            return;
                        }
                    }
                }

                event = (DSGEvent) events.remove(0);
            }

            if (event instanceof DSGMoveTableEvent) {
                DSGMoveTableEvent moveEvent = (DSGMoveTableEvent) event;

                moveNum++;
                aiPlayer.addMove(moveEvent.getMove());

                if (isMyTurn()) {
                    try {

                        if (moveNum > 1) {
                            //sleep to allow opponent to request undo
                            Thread.sleep(MOVE_SLEEP_TIME);
                        }

                        int move = aiPlayer.getMove();
                        callback.receiveMove(move);

                    } catch (InterruptedException e) {
                    }
                }
            } else if (event instanceof DSGUndoRequestTableEvent) {
                // don't undo the move if its not my turn!
                // timing issues could make it happen that a move
                // gets sent out just as an undo request comes in
                if (isMyTurn()) {
                    moveNum--;
                    aiPlayer.undoMove();
                    callback.receiveUndoReply(true);
                } else {
                    callback.receiveUndoReply(false);
                }
            }
        }
    }
}