package org.pente.turnBased.test;

import java.awt.Point;

import org.pente.game.*;
import org.pente.gameServer.core.AlphaNumericGridCoordinates;
import org.pente.gameServer.core.GridCoordinates;

public class PoofTest {

    public PoofTest() {
        super();

    }

    /**
     * @param args
     */
    public static void main(String[] args) {

        GridState state = GridStateFactory.createGridState(
                GridStateFactory.TB_KERYO, new MoveData() {

                    final int moveInts[] = new int[]{
                            180, 200, 184, 162, 182, 183, 144, 238, 219, 125, 104, 84, 142, 123, 143, 145, 141, 140, 105, 203, 106, 107, 89, 107, 103, 102, 163, 125, 165, 125, 145, 201
                    };

                    public int getMove(int num) {
                        //Point move = coordinates.getPoint(moves[num]);
                        //return state.convertMove(move.x, 18 - move.y);
                        return moveInts[num];
                    }

                    public int getNumMoves() {
                        return moveInts.length;
                    }

                    public void addMove(int move) {
                    }

                    public int[] getMoves() {
                        return null;
                    }

                    public void undoMove() {
                    }

                });
    }

}
