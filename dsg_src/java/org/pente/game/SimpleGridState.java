/** SimpleGridState.java
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

package org.pente.game;

import java.util.*;


/** This is a simple implementation of GridState to represent the state of the board
 *  in a grid based game.
 *
 *  @author dweebo (dweebo@www.pente.org)
 */
public class SimpleGridState implements GridState {

    /** The width of the board */
    private int     boardSizeX;

    /** The height of the board */
    private int     boardSizeY;

    /** The data for the board, currently stored as 1 int per position */
    private int[][] board;

    /** The list of moves made on the board in order */
    private Vector<Integer>  moves;
    public void setMoves(Vector<Integer> moves) { this.moves = new Vector<>(moves); }
    public Vector<Integer> getMovesVector() { return moves; }
    
    private boolean allowNonBoardMoves;
    public boolean allowNonBoardMoves() { return allowNonBoardMoves; }
    public void setAllowNonBoardMoves(boolean allowNonBoardMoves) { this.allowNonBoardMoves = allowNonBoardMoves; }

    private boolean allowOccupiedMoves;
    public boolean isAllowOccupiedMoves() { return allowOccupiedMoves; }
    public void setAllowOccupiedMoves(boolean allowOccupiedMoves) { this.allowOccupiedMoves = allowOccupiedMoves; }

    
    /** Create a new grid state with a specified size
     *  @param boardSize The size of the board
     */
    public SimpleGridState(int boardSizeX, int boardSizeY) {
        this.boardSizeX = boardSizeX;
        this.boardSizeY = boardSizeY;

        board = new int[boardSizeX][boardSizeY];
        moves = new Vector();
    }

    /** Create a new grid state with a specified size and add the moves
     *  contained in the passed in MoveData to the board.
     *  @param data MoveData containing a list of moves
     *  @param boardSize The size of the board
     */
    public SimpleGridState(MoveData data, int boardSizeX, int boardSizeY) {
        this(boardSizeX, boardSizeY);

        for (int i = 0; i < data.getNumMoves(); i++) {
            addMove(data.getMove(i));
        }
    }
    
    public GridState getInstance(MoveData moveData) {
        
        return new SimpleGridState(moveData, boardSizeX, boardSizeY);
    }

    public int getGridSizeX() {
        return boardSizeX;
    }
    public int getGridSizeY() {
        return boardSizeY;
    }

    /** Determine if a move is valid in this grid state
     *  @param move An integer representation of a move
     *  @param player The player making the move
     */
    public boolean isValidMove(int move, int player) {

        if (player != getCurrentPlayer()) {
            return false;
        }

        // make sure move is in bounds
        
        if (outOfBounds(move)) {
            if (!allowNonBoardMoves) {
                return false;
            }
        } else if (getPosition(move) != 0 && !allowOccupiedMoves) {
            return false;
        }

        return true;
    }

    /** Allow players to undo at any time since this isn't
     *  tied to a specific game, unless no moves have been made.
     *  @param player The player requesting an undo
     *  @return boolean True if the player can request an undo
     */
    public boolean canPlayerUndo(int player) {
        return getNumMoves() > 0;
    }

    /** Undo the last move */
    public void undoMove() {

        if (moves.size() > 0) {
            int move = (Integer) moves.elementAt(moves.size() - 1);
            moves.removeElementAt(moves.size() - 1);
            if (!outOfBounds(move)) {
                setPosition(move, 0);
            }
        }
    }

    /** Not implemented */
    public boolean isGameOver() {
        return false;
    }
    /** Not implemented */
    public int getWinner() {
        return 0;
    }

    /** Clears the grid state */
    public void clear() {
        moves.removeAllElements();

        for (int i = 0; i < boardSizeX; i++) {
            for (int j = 0; j < boardSizeY; j++) {
                setPosition(i, j, 0);
            }
        }
    }

    /** Add a move for this board
     *  @param move An integer representation of a move
     */
    public void addMove(int move) {
        if (!outOfBounds(move)) {
            int currentPlayer = getCurrentColor();
            int pos = getPosition(move);
            if (pos != 0 && !allowOccupiedMoves) {
                throw new IllegalArgumentException("position already filled " + move);
            }
            moves.addElement(move);
            if (pos == 0) {
                setPosition(move, currentPlayer);
            }
        } else if (allowNonBoardMoves) {
            moves.addElement(move);
        }
    }

    /** Get which color it is
     *  @return int The current player (1, 2, etc.)
     */
    public int getCurrentColor() {
        return getNumMoves() % 2 + 1;
    }

    public int getColor(int moveNum) {
    	return moveNum % 2 + 1;
    }
    /** Get whose turn it is
     *  @return int The current player (1, 2, etc.)
     */
    public int getCurrentPlayer() {
        return getCurrentColor();
    }

    /** Get the number of moves for this board
     *  @return The number of moves
     */
    public int getNumMoves() {
        return moves.size();
    }

    /** Get a move for this board
     *  @param num The sequence number of the move
     */
    public int getMove(int moveNum) {
        return ((Integer) moves.elementAt(moveNum)).intValue();
    }

    public int[] getMoves() {
        int m[] = new int[getNumMoves()];
        for (int i = 0; i < m.length; i++) {
            m[i] = getMove(i);
        }
        return m;
    }
    
    /** Get info about a position
     *  @param position The position on the board
     *  @return int The value associated with this position
     */
    public int getPosition(int position) {
        checkOutOfBounds(position);
        Coord move = convertMove(position);
        return getPosition(move.x, move.y);
    }

    /** Get info about a position
     *  @param x The horizontal position from left to right
     *  @param y The vertical position from top to bottom
     *  @return int The value associated with this position
     */
    public int getPosition(int x, int y) {
        checkOutOfBounds(x, y);
        return board[x][y];
    }

    public void checkOutOfBounds(int move) {
        Coord p = convertMove(move);
        checkOutOfBounds(p.x, p.y);
    }
    public void checkOutOfBounds(int x, int y) {
        if (x < 0 || x >= boardSizeX ||
                y < 0 || y >= boardSizeY) {
            throw new IllegalArgumentException("Out of bounds: " + x + ", " + y + " max = " + (boardSizeX - 1) + ", " + (boardSizeY - 1));
        }
    }

    /** Set a position
     *  @param position The position on the board
     *  @param value The value to put at this position
     */
    public void setPosition(int position, int value) {
        if (!outOfBounds(position)) {
            Coord move = convertMove(position);
            setPosition(move.x, move.y, value);
        }
    }

    /** Set a position
     *  @param x The horizontal position from left to right
     *  @param y The vertical position from top to bottom
     *  @param value The value to put at this position
     */
    public void setPosition(int x, int y, int value) {
        if (!outOfBounds(x, y)) {
            board[x][y] = value;
        }
    }

    /** Get the whole board
     *  @return int[][] 2 dimensional array with an integer for each position
     */
    public int[][] getBoard() {
        return board;
    }

    /** Converts coordinates to a single variable
     *  @param x The horizontal position from left to right
     *  @param y The vertical position from top to bottom
     *  @return int The move
     */
    public int convertMove(int x, int y) {
        return x + y * boardSizeX;
    }

    /** Converts a single variable move to its x,y coordinates
     *  @param move The move to convert
     *  @return Point A point variable with x and y set
     */
    public Coord convertMove(int move) {
        return new Coord(move % boardSizeX, move / boardSizeX);
    }

    public boolean outOfBounds(int move) {
    	Coord p = convertMove(move);
        return outOfBounds(p.x, p.y);
    }
    public boolean outOfBounds(int x, int y) {
        if (x < 0 || x >= boardSizeX ||
            y < 0 || y >= boardSizeY) {
            return true;
        }
        return false;
    }

    /** Determine if the pieces on the board are the same
     *  @param gridState The grid state to compare agains
     *  @return boolean True if the position is the same
     */
    public boolean positionEquals(GridState gridState) {

        if (boardSizeX != gridState.getGridSizeX() ||
            boardSizeY != gridState.getGridSizeY()) {
            return false;
        }

        for (int i = 0; i < boardSizeX; i++) {
            for (int j = 0; j <	boardSizeY; j++) {
                if (getPosition(i, j) != gridState.getPosition(i, j)) {
                    return false;
                }
            }
        }

        return true;
    }

    // hash stuff
    private long hashes[][] = new long[362][9];
    private int rots[] = new int[362];

    private int possibleRotations[] = new int[8];
    private int numPossibleRotations = 0;


    public long[][] getHashes() {
    	return hashes;
    }
    public long getHash() {

        if (getNumMoves() == 0) {
            return 0;
        }
        
        return hashes[getNumMoves() - 1][8];
    }

    public long getHash(int index) {
        if (index >= getNumMoves()) {
            return 0;
        }
        
        return hashes[index][8];
    }
    
    public int getRotation() {
        if (getNumMoves() == 0) {
            return 0;
        }
        
        return rots[getNumMoves() - 1];
    }
    public int getRotation(int index) {
        if (index >= getNumMoves()) {
            return 0;
        }
        
        return rots[index];
    }
    public int[] getRotations() {
    	return rots;	
    }

    static final int rotx[] = new int[] { 1, 1,  1,  1, -1, -1, -1, -1 };
    static final int roty[] = new int[] { 1, 1, -1, -1, -1, -1,  1,  1 };
    static final int rotf[] = new int[] { 0, 1,  0,  1,  0,  1,  0,  1 };

    public int rotateMove(int move, int rotationIndex) {
        if (outOfBounds(move)) {
            return move;
        }
        int xoff = - (boardSizeX/2);
        int yoff = - (boardSizeY/2);

        Coord p = convertMove(move);
        int x = p.x;
        int y = p.y;
        int x1 = (x + xoff) * rotx[rotationIndex];
        int y1 = (y + yoff) * roty[rotationIndex];
        if (rotf[rotationIndex] != 0) {
            int t = x1;
            x1 = y1;
            y1 = t;
        }
        return convertMove(x1 - xoff, y1 - yoff);
    }

    public int rotateFirstMove(int move, int rotation) {
        int xoff = - (boardSizeX/2);
        int yoff = - (boardSizeY/2);

        Coord p = convertMove(move);
        int x = p.x;
        int y = p.y;

        int x1 = (x + xoff) * rotx[rotation] - xoff;
        int y1 = (y + yoff) * roty[rotation] - yoff;
        if (rotf[rotation] != 0) {
            int t = x1;
            x1 = y1;
            y1 = t;
        }

        return convertMove(x1, y1);
    }

    public int getFirstMoveRotation(int move) {
        int xoff = - (boardSizeX/2);
        int yoff = - (boardSizeY/2);

        Coord p = convertMove(move);
        int x = p.x;
        int y = p.y;

        int x1 = (x + xoff);
        int y1 = (y + yoff);

        x = -boardSizeX;
        y = -boardSizeY;
        int rotation = 0;

        for (int k = 0; k < 8; k++) {
            int x2 = x1;
            int y2 = y1;
            x2 = x2 * rotx[k];
            y2 = y2 * roty[k];
            if (rotf[k] != 0) {
                int t = x2;
                x2 = y2;
                y2 = t;
            }

            if (x2 > x || x2 == x && y2 > y) {
                x = x2;
                y = y2;
                rotation = k;
            }
        }

        return rotation;
    }

    public int rotateMoveToLocalRotation(int move, int newRotation) {
        int xoff = - (boardSizeX/2);
        int yoff = - (boardSizeY/2);

        Coord p = convertMove(move);
        int x = p.x;
        int y = p.y;

        int x1 = (x + xoff) * rotx[newRotation];
        int y1 = (y + yoff) * roty[newRotation];
        if (rotf[newRotation] != 0) {
            int t = x1;
            x1 = y1;
            y1 = t;
        }

        if (numPossibleRotations > 0) {
            x = -1;
            y = -1;
        } else {
            x = x1 - xoff;
            y = y1 - yoff;
        }
        
        for (int k = 0; k < numPossibleRotations; k++) {
            //rotate from std to test
            newRotation = possibleRotations[k];
            int x2 = x1;
            int y2 = y1;
            if (rotf[newRotation] != 0) {
                int t = x2;
                x2 = y2;
                y2 = t;
            }
            x2 = x2 * rotx[newRotation] - xoff;
            y2 = y2 * roty[newRotation] - yoff;
    
            if (x2 > x || x2 == x && y2 > y) {
                x = x2;
                y = y2;
            }
        }

        return convertMove(x, y);
    }
    public int[] getAllPossibleRotations(int move, int newRotation) {
        int xoff = - (boardSizeX/2);
        int yoff = - (boardSizeY/2);

        Coord p = convertMove(move);
        int x = p.x;
        int y = p.y;

        int x1 = (x + xoff) * rotx[newRotation];
        int y1 = (y + yoff) * roty[newRotation];
        if (rotf[newRotation] != 0) {
            int t = x1;
            x1 = y1;
            y1 = t;
        }

        int poss[] = new int[numPossibleRotations];
        for (int k = 0; k < numPossibleRotations; k++) {
            //rotate from std to test
            newRotation = possibleRotations[k];
            int x2 = x1;
            int y2 = y1;
            if (rotf[newRotation] != 0) {
                int t = x2;
                x2 = y2;
                y2 = t;
            }
            x2 = x2 * rotx[newRotation] - xoff;
            y2 = y2 * roty[newRotation] - yoff;
    
            poss[k] = convertMove(x2, y2);
        }

        return poss;
    }
 
    
    public void updateHash(HashCalculator calc) {

        if (moves.isEmpty()) {
            return;
        }
        else if (moves.size() == 1) {
        	for (int i = 0; i < 8; i++) {
        		hashes[0][i] = 0;
        	}
        }
        else if (moves.size() > 1) {
            // copy prev. moves hashes into current moves
            for (int i = 0; i < 8; i++) {
                hashes[moves.size() - 1][i] = hashes[moves.size() - 2][i];
            }
        }
            
        // record hash, rotation of move
        long maxHash = Long.MIN_VALUE;
        int maxRotation = 0;
        int move = getMove(moves.size() - 1);
        int p = 0;
        if (!outOfBounds(move)) {
            p = getPosition(move);
        }
        numPossibleRotations = 0;
        
        // for each possible rotation of this move
        for (int j = 0; j < 8; j++) {
            hashes[moves.size() - 1][j] = 
                calc.calcHash(hashes[moves.size() - 1][j], p, move, j);

            if (hashes[moves.size() - 1][j] == maxHash) {
                possibleRotations[numPossibleRotations++] = j;
            }
            if (hashes[moves.size() - 1][j] > maxHash) {
                maxHash = hashes[moves.size() - 1][j];
                numPossibleRotations = 0;
                possibleRotations[numPossibleRotations++] = j;
                maxRotation = j;
            }
        }
        
        
        rots[moves.size() - 1] = maxRotation;
        hashes[moves.size() - 1][8] = maxHash;
    }
    
    public void printBoard() {
        for (int i=0;i<boardSizeY;i++) {
            for (int j=0;j<boardSizeX;j++) {
                System.out.print(board[j][i] + " ");
            }
            System.out.println();
        }
    }
}