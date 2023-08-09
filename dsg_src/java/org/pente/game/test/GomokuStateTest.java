package org.pente.game.test;

import junit.framework.*;

import org.pente.game.*;

public class GomokuStateTest extends GridStateTest {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                GomokuStateTest.class.getName()
        });
    }

    public static Test suite() {
        return new TestSuite(GomokuStateTest.class);
    }

    public GomokuStateTest(String name) {
        super(name);
    }

    GridState createGridState(int x, int y) {
        return new SimpleGomokuState(x, y);
    }

    public void testLeftRightCrossNotGameOver() {

        GridState state1 = createGridState(10, 10);

        addMoves(new int[]{17, 1, 18, 88, 19, 55, 20, 66, 21}, state1);

        assertEquals(false, state1.isGameOver());
    }

    public void testGameOver() {
        GridState state1 = createGridState(10, 10);

        addMoves(new int[]{2, 12, 3, 14, 4, 16, 5, 18, 6}, state1);

        assertEquals(true, state1.isGameOver());
        assertEquals(1, state1.getWinner());
    }

    public void testGameOverOverlinesOff() {

        // don't use createGridState here because pente, keryo-pente don't
        // always allow overlines, it can't be turned off
        SimpleGomokuState state1 = new SimpleGomokuState(10, 10);
        state1.allowOverlines(false);

        addMoves(new int[]{2, 12, 3, 14, 4, 16, 5, 18, 7, 11, 6}, state1);

        assertEquals(false, state1.isGameOver());
    }


    public void testGameOverOverlinesOn() {

        // don't use createGridState here because pente, keryo-pente don't
        // always allow overlines, it can't be turned off
        SimpleGomokuState state1 = new SimpleGomokuState(10, 10);
        state1.allowOverlines(true);

        addMoves(new int[]{2, 12, 3, 14, 4, 16, 5, 18, 7, 11, 6}, state1);

        assertEquals(true, state1.isGameOver());
        assertEquals(1, state1.getWinner());
    }

    public void testUndo() {

        GridState state = createGridState(10, 10);

        assertEquals(false, state.canPlayerUndo(1));
        assertEquals(false, state.canPlayerUndo(2));

        state.addMove(50);
        assertEquals(false, state.canPlayerUndo(1));
        assertEquals(false, state.canPlayerUndo(2));

        state.addMove(51);
        assertEquals(false, state.canPlayerUndo(1));
        assertEquals(true, state.canPlayerUndo(2));

        state.addMove(52);
        assertEquals(true, state.canPlayerUndo(1));
        assertEquals(false, state.canPlayerUndo(2));
    }


    public void testDraw() {

        SimpleGomokuState state1 = new SimpleGomokuState(19, 19);
        int moves[] = new int[361];
        for (int i = 0; i < 361; i++) {
            moves[i] = i;
        }
        addMoves(moves, state1);
        assertEquals(true, state1.isGameOver());
        assertEquals(true, state1.getWinner() == 0);

        state1.printBoard();
    }

    void addMoves(int moves[], GridState state) {
        for (int i = 0; i < moves.length; i++) {
            state.addMove(moves[i]);
        }
    }
}