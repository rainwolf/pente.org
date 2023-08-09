package org.pente.game.test;

import junit.framework.*;

import org.pente.game.*;

public class Connect6StateTest extends GridStateTest {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                Connect6StateTest.class.getName()
        });
    }

    public static Test suite() {
        return new TestSuite(Connect6StateTest.class);
    }

    public Connect6StateTest(String name) {
        super(name);
    }

    SimpleConnect6State createGridState(int x, int y) {
        return new SimpleConnect6State(x, y);
    }


    public void testGameOver() {
        SimpleConnect6State state1 = createGridState(19, 19);

        addMoves(new int[]{180, 181, 182, 1, 2, 183, 184, 3, 4, 185},
                state1);

        state1.printBoard();
        assertEquals(false, state1.isGameOver());

        addMoves(new int[]{186}, state1);

        assertEquals(true, state1.isGameOver());
        assertEquals(2, state1.getWinner());

        state1.printBoard();
    }

    public void testDraw() {

        SimpleConnect6State state1 = createGridState(19, 19);
        int moves[] = new int[361];
        for (int i = 0; i < 361; i++) {
            moves[i] = i;
        }
        addMoves(moves, state1);
        assertEquals(true, state1.isGameOver());
        assertEquals(true, state1.getWinner() == 0);

        state1.printBoard();
    }


//    public void testUndo() {
//        
//        GridState state = createGridState(10, 10);
//        
//        assertEquals(false, state.canPlayerUndo(1));
//        assertEquals(false, state.canPlayerUndo(2));
//        
//        state.addMove(50);
//        assertEquals(false, state.canPlayerUndo(1));
//        assertEquals(false, state.canPlayerUndo(2));
//        
//        state.addMove(51);
//        assertEquals(false, state.canPlayerUndo(1));
//        assertEquals(true, state.canPlayerUndo(2));
//        
//        state.addMove(52);
//        assertEquals(true, state.canPlayerUndo(1));
//        assertEquals(false, state.canPlayerUndo(2));
//    }

    void addMoves(int moves[], GridState state) {
        for (int i = 0; i < moves.length; i++) {
            state.addMove(moves[i]);
        }
    }
}