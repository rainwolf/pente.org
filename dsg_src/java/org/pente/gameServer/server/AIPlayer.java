/**
 * AIPlayer.java
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

/** Interface between the server and any third party AI players.
 *  If you wish to create your own AI all you have to do is create a class
 *  that implements this interface.  The class must also have the default
 *  contructor with 0 parameters.  Then add your AI in the ai_config.xml file.
 *  More on that can be found in the developer manual.
 */
public interface AIPlayer {

    /** The server will call this at some point in time before a game is
     *  started.  A new AIPlayer is created for each game.
     */
    public void init();

    /** If your AI supports more than one game with the same instance you
     *  can have it configured with this method.
     *  @param game The game the user wants to play
     *         currently Pente=0
     *                   Gomoku=1
     *                   Keryo-Pente=2
     */
    public void setGame(int game);

    /** If the AI supports different levels of intelligence then the user
     *  can choose which level.
     *  @param level The level to play at
     */
    public void setLevel(int level);

    /** Sets which player the AI is playing as, either 1 or 2
     *  @param seat The seat the AI is sitting at
     */
    public void setSeat(int seat);

    /** If the AI has other options that are unique to it, they can be set
     *  here
     *  @param optionName The name of the option
     *  @param optionValue The value of the option
     */
    public void setOption(String optionName, String optionValue);

    /** Called by the server when a move is made, this is called for moves by
     *  your opponent AND the AI.
     *  @param move The move.  Moves are represented as integers from 0 to 361.
     *              0 represents the A19 coordinate and 361 represents the T1
     *              coordinate.  Coordinates are mapped starting at the upper
     *              left hand corner, moving right across the board and then
     *              down the board.
     *              Hint: map to any given coordinate to an x,y system where
     *              0,0 starts at the bottom right like so.
     *              int xCoordinate = move % 19;
     *              int yCoordinate = 18 - move / 19;
     *              So now the coordinate A1 maps to xCoordinate 0 and 
     *              yCoordinate 0;
     */
    public void addMove(int move);

    /** Called by the server to stop the ai from thinking about the next move
     *  This should make the AI throw and InterruptedException from
     *  getMove() if it is being called currently.
     */
    public void stopThinking();

    /** Called by the server when a player requested the previous move
     *  be undone.  The server will always accept an undo for the ai
     */
    public void undoMove();

    /** Called by the server when its the AI's turn.
     *  This is where your AI should perform most of its work.
     *  It should also periodically make sure that its still running
     *  in case the server has stopped it with a call to stopThinking().
     *  If so throw an interrupted exception and the server will take
     *  care of the rest.
     *  @return int The move, see addMove() for the expected move format.
     */
    public int getMove() throws InterruptedException;

    /** The server will call this at some point after a game is finished.
     *  Note: A new AIPlayer is created for each game so you don't need to
     *  clean up any game state in this method.  Use it only if you have some
     *  other resources (threads, connections) that need to be cleaned up.
     */
    public void destroy();
}
