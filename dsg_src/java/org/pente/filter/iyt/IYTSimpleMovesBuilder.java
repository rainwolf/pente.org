/** IYTSimpleMovesBuilder.java
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

package org.pente.filter.iyt;

import java.util.*;

import org.pente.filter.*;
import org.pente.filter.http.*;
import org.pente.game.*;

/** Builds the move list for a game by running a HttpFilterController with a
 *  IYTMovesFilter for each move.  This process has much room for improvement.
 *  I plan on adding a better class for this in a later version.  3 different
 *  improvements are: use a new moves filter that can filter out 2 moves at a
 *  time (one for white, one for black), or use a builder that can send/receive
 *  all data from iyt in a single connection using HTTP 1.1 persistent connections,
 *  or provide a multithreaded version, get all moves at the same time in different
 *  threads (This would good for users with fast internet connections).
 *  @see IYTMoveFilter
 *  @see IYTGameBuilder
 *  @since 0.1
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class IYTSimpleMovesBuilder implements IYTMovesBuilder, FilterListener {

    /** The current move filter */
    private IYTMoveFilter 	    currentFilter;
    /** The current filter controller */
    private FilterController	currentFilterController;

    /** The game data to store moves in */
    private GameData 			gameData;

    /** The number of moves to get */
    private int                 numMoves;

    /** Flag to tell if done or not */
    private boolean				done;

    /** Flag to tell if the building process was successful or not */
    private boolean				success;

    /** The exception that was thrown if the !success */
    private Exception			ex;

    /** The http parameters to use for iyt */
    private Hashtable			params;

    /** The cookies to use to connect to iyt */
    private Hashtable           cookies;


    /** Creates a new IYTSimpleMovesBuilder
     *  @param params The http params to use for iyt
     *  @param cookies The cookies to use to connect to iyt
     *  @param gameData The game data to store moves in
     *  @param numMoves The number of moves in the game
     */
    public IYTSimpleMovesBuilder(Hashtable params, Hashtable cookies, GameData gameData, int numMoves) {
        this.params = params;
        this.cookies = cookies;
        this.gameData = gameData;
        this.numMoves = numMoves;
    }

    /** Builds the game.  Blocks until the game is built or fails to build.
     *  @return GameData The game data with the moves
     *  @exception Exception If the moves couldn't be build
     */
    public synchronized GameData buildMoves() throws Exception {

        // if we don't have all the moves yet
        if (gameData.getNumMoves() < numMoves) {

            // get the next move
            getNextMove();

            while (!done) {
                // wait for all threads to complete and then return game
                try {
                    wait();
                } catch(InterruptedException ex) {
                }
            }

            if (success) {
                return gameData;
            }
            else {
                throw ex;
            }
        }
        // else return the gameData
        else {
            return gameData;
        }
    }

    /** Gets the next move */
    private void getNextMove() {

        // increment the move parameter
        params.put(IYTConstants.MOVE_PARAMETER, Integer.toString(gameData.getNumMoves() + 1));
        params.put(IYTConstants.OLD_MOVE_PARAMETER, Integer.toString(gameData.getNumMoves() + 1));

        currentFilter = new IYTMoveFilter();
        HttpFilterController httpFilterController = new HttpFilterController("GET",
                                                                             IYTConstants.HOST,
                                                                             IYTConstants.GAME_REQUEST,
                                                                             params,
                                                                             cookies,
                                                                             currentFilter);
        currentFilterController = new RetryFilterController(httpFilterController, RetryFilterController.INFINITE_RETRIES, 60);
        currentFilterController.addListener(this);

        // run the request for the next move in a new thread
        Thread t = new Thread(currentFilterController);
        t.start();
    }


    public void lineFiltered(String line) {
    }

    /** Called by move filter when complete, check if all moves have been
     *  retrieved yet.  If so notify the caller of buildGame() that the build
     *  is complete.  If the game hasn't been built yet and there was a problem
     *  set the exception and notify the caller.  If there was no problem, get
     *  the next move.
     *  @param success Whether or not the filtering was successful
     *  @param ex The exception that occurred if !success
     */
    public void filteringComplete(boolean success, Exception ex) {

        if (success) {

            currentFilterController.removeListener(this);

            // for some reason, no move was found
            if (!currentFilter.wasMoveFiltered()) {

                synchronized(this) {

                    // if game not completely downloaded
                    if (gameData.getNumMoves() < numMoves) {
                        this.ex = new Exception("Exception getting move " + (gameData.getNumMoves() + 1));
                        this.success = false;
                    }
                    // else we didn't except to see a move
                    // actually should remove all this since we now know
                    // the number of expected moves before we start processing
                    else {
                        this.success = true;
                    }

                    done = true;
                    notify();
                }
            }
            else {
                // record the move in the game data
                gameData.addMove(currentFilter.getMove());

                // if we're done, notify
                if (gameData.getNumMoves() == numMoves) {
                    synchronized(this) {
                        this.success = true;
                        done = true;
                        notify();
                    }
                }
                // else get the next move
                else {
                    getNextMove();
                }
            }
        }
        // not successful, some exception caught
        else {
            synchronized(this) {
                this.success = false;
                done = true;
                this.ex = ex;
                notify();
            }
        }
    }
}