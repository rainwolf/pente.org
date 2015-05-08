package org.pente.tutorial;

/** MoveTutorialStep.java
 *  Copyright (C) 2003 Dweebo's Stone Games (http://www.pente.org/)
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

import java.awt.*;

import org.pente.game.*;
import org.pente.gameServer.core.*;

public class MoveTutorialStep extends AbstractTutorialStep {

    private static final GridState state = new SimpleGomokuState();

    private static final GridCoordinates coordinates =
        new AlphaNumericGridCoordinates(19, 19);

    private int move;
    private int moves[];
    private GridPiece pieces[];
    private boolean highlightPieceVisible = false;
    
    public MoveTutorialStep(int move) {
        this.move = move;
    }
    public MoveTutorialStep(int moves[]) {
        this.moves = moves;
    }
    public MoveTutorialStep(String movesStr[]) {
        
        moves = new int[movesStr.length];
        for (int i = 0; i < movesStr.length; i++) {
            Point m = coordinates.getPoint(movesStr[i]);
            moves[i] = state.convertMove(m.x, 18 - m.y);
        }
    }
    public MoveTutorialStep(GridPiece pieces[]) {
        this.pieces = pieces;
    }
    public MoveTutorialStep(boolean highlightPieceVisible) {
        pieces = new GridPiece[0];
        setHighlightPieceVisible(highlightPieceVisible);
    }
    
    public void setHighlightPieceVisible(boolean visible) {
        this.highlightPieceVisible = visible;
    }
    
    public void addMoves(String movesStr[], int player) {
        GridPiece pieces2[] = new GridPiece[pieces.length + movesStr.length];
        for (int i = 0; i < pieces.length; i++) {
            pieces2[i] = pieces[i];
        }
        for (int i = 0; i < movesStr.length; i++) {
            Point m = coordinates.getPoint(movesStr[i]);
            pieces2[i + pieces.length] = new SimpleGridPiece(m.x, m.y, player);
        }
        pieces = pieces2;
    }
    
    public void go() {

        if (pieces != null) {
            for (int i = 0; i < pieces.length; i++) {
                screen.addMove(pieces[i]);
            }
            if (highlightPieceVisible) {
                screen.addHighlightMove(pieces[pieces.length - 1]);
            }
        }
        else if (moves != null) {
            for (int i = 0; i < moves.length; i++) {
                screen.addMove(moves[i]);
            }
        }
        else {
           screen.addMove(move);
        }        
    }
}
