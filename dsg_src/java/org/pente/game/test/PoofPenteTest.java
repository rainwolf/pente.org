package org.pente.game.test;

import junit.framework.*;

import org.pente.game.*;

public class PoofPenteTest extends PenteStateTest {
    
    public PoofPenteTest(String title) {
        super(title);
    }

    public static Test suite() {
        return new TestSuite(PoofPenteTest.class);
    }
    
    GridState createGridState(int x, int y) {
        return GridStateFactory.createGridState(
            GridStateFactory.POOF_PENTE, x, y);
    }
    
    public void testSimplePoof() {
        
        SimplePoofPenteState state = (SimplePoofPenteState) createGridState(19, 19);
        
        state.addMove(state.convertMove(9, 9));
        state.addMove(state.convertMove(10, 10));
        state.addMove(state.convertMove(12, 12));
        state.addMove(state.convertMove(11, 11));
       
        assertEquals(2, state.getNumCaptures(1));
        assertEquals(0, state.getNumCaptures(2));
        assertEquals(0, state.getPosition(10, 10));
        assertEquals(0, state.getPosition(11, 11));
        
        state.addMove(state.convertMove(10, 10));
        assertEquals(1, state.getPosition(10, 10));
    }

    public void testSimplePoofRepeat() {
        
        SimplePoofPenteState state = (SimplePoofPenteState) createGridState(19, 19);
        
        state.addMove(state.convertMove(9, 9));
        state.addMove(state.convertMove(10, 10));
        state.addMove(state.convertMove(12, 12));
        state.addMove(state.convertMove(11, 11));
       
        assertEquals(2, state.getNumCaptures(1));
        assertEquals(0, state.getNumCaptures(2));
        assertEquals(0, state.getPosition(10, 10));
        assertEquals(0, state.getPosition(11, 11));
        
        state.addMove(0);
        state.setPosition(state.convertMove(10, 10), 2);
        state.addMove(state.convertMove(11, 11));
        
        assertEquals(4, state.getNumCaptures(1));
        assertEquals(0, state.getNumCaptures(2));
        assertEquals(0, state.getPosition(10, 10));
        assertEquals(0, state.getPosition(11, 11));
    }

    public void testCaptureAndPoof() {
        SimplePoofPenteState state = (SimplePoofPenteState) createGridState(19, 19);
        
        state.setPosition(state.convertMove(9, 9), 1);
        state.setPosition(state.convertMove(9, 13), 1);
        state.setPosition(state.convertMove(9, 10), 2);
        state.setPosition(state.convertMove(9, 11), 2);
        state.setPosition(state.convertMove(9, 14), 2);

        state.addMove(state.convertMove(9, 12));
        
        assertEquals(2, state.getNumCaptures(1));
        assertEquals(2, state.getNumCaptures(2));

        SimplePoofPenteState state2 = (SimplePoofPenteState) createGridState(19, 19);
        state2.setPosition(state2.convertMove(9, 9), 1);
        state2.setPosition(state2.convertMove(9, 14), 2);

        assertEquals(true, state.positionEquals(state2));
    }
    
    public void testPoof3Stones() {

        SimplePoofPenteState state = (SimplePoofPenteState) createGridState(19, 19);
        
        state.setPosition(state.convertMove(9, 9), 2);
        state.setPosition(state.convertMove(11, 9), 2);
        state.setPosition(state.convertMove(8, 6), 2);
        state.setPosition(state.convertMove(9, 6), 2);

        state.setPosition(state.convertMove(9, 8), 1);
        state.setPosition(state.convertMove(10, 8), 1);

        state.addMove(state.convertMove(9, 7));
        
        assertEquals(0, state.getNumCaptures(1));
        assertEquals(3, state.getNumCaptures(2));

        SimplePoofPenteState state2 = (SimplePoofPenteState) createGridState(19, 19);
        
        state2.setPosition(state.convertMove(9, 9), 2);
        state2.setPosition(state.convertMove(11, 9), 2);
        state2.setPosition(state.convertMove(8, 6), 2);
        state2.setPosition(state.convertMove(9, 6), 2);
        
        assertEquals(true, state.positionEquals(state2));
    }
    
    /*
     * 
        0 0 0 0 0 0 0 2 0 0 
        0 0 0 0 0 0 0 1 0 0 
        0 0 2 1 1 1 1 X 0 0 
        0 0 0 0 0 0 0 2 0 0 
        0 0 0 0 0 0 0 0 0 0 
        0 0 0 0 0 0 0 0 0 0 
        0 0 0 0 0 0 0 0 0 0 
        0 0 0 0 0 0 0 0 0 0 
        0 0 0 0 0 0 0 0 0 0 
        0 0 0 0 0 0 0 0 0 0 
     */
    public void testFiveInRowBeforeCaptureNoWin() {
        
        SimplePoofPenteState state =
            (SimplePoofPenteState) createGridState(10, 10);
        
        // just get enough moves in the get > 9 so a win is possible
        for (int i = 0; i < 10; i++) {
            state.addMove(89 + i);
        }
        
        state.setPosition(22, 2);
        state.setPosition(23, 1);
        state.setPosition(24, 1);
        state.setPosition(25, 1);
        state.setPosition(26, 1);
        state.setPosition(17, 1);
        state.setPosition(7, 2);
        state.setPosition(37, 2);

        state.addMove(27); //w

        assertEquals(false, state.isGameOver());
        assertEquals(0, state.getNumCaptures(1));
        assertEquals(2, state.getNumCaptures(2));
        
        assertEquals(0, state.getPosition(27));
        assertEquals(0, state.getPosition(17));
        
        state.addMove(1); // dummy move for black
        state.addMove(27); // now a win for white

        assertEquals(true, state.isGameOver());
        assertEquals(1, state.getPosition(27));
        assertEquals(1, state.getWinner());
        assertEquals(2, state.getCurrentPlayer());
    }

    /** If both players end up with exactly ten captures, then
     *  first player to get next capture or 5 wins.
     */    
    public void testBothPlayersOverCaptureLimit() {

        SimplePoofPenteState state =
            (SimplePoofPenteState) createGridState(10, 10);
        
        // repeatedly poof capture 4 sets of stones to get 8
        state.setPosition(0, 1);
        state.addMove(3); // w
        for (int i = 0; i < 4; i++) {
            state.addMove(1); // b
            state.addMove(10 + 2*i); // w
            state.addMove(2); // b
            state.addMove(30 + 2*i); // w
        }
        assertEquals(8, state.getNumCaptures(1));
        assertEquals(0, state.getNumCaptures(2));

        // repeatedly poof capture 4 sets of stones to get 8
        state.setPosition(50, 2);
        state.addMove(53); // b
        for (int i = 0; i < 4; i++) {
            state.addMove(51); // w
            state.addMove(60 + 2*i); // b
            state.addMove(52); // w
            state.addMove(70 + 2*i); // b
        }
        assertEquals(8, state.getNumCaptures(1));
        assertEquals(8, state.getNumCaptures(2));
        
        state.setPosition(80, 1); //w
        state.setPosition(81, 2); //b
        state.setPosition(82, 2); //b
        state.setPosition(84, 1); //w
        state.setPosition(85, 2); //b
        state.addMove(83); //w

        assertEquals(10, state.getNumCaptures(1));
        assertEquals(10, state.getNumCaptures(2));
        assertEquals(false, state.isGameOver());
        
        state.addMove(81); //b
        state.setPosition(82, 2);
        state.setPosition(84, 1);
        state.setPosition(85, 2);
        state.addMove(83); //w
        
        assertEquals(12, state.getNumCaptures(1));
        assertEquals(12, state.getNumCaptures(2));
        assertEquals(false, state.isGameOver());
        
        state.addMove(81); //b
        state.setPosition(82, 2);
        state.addMove(83); //w
        assertEquals(14, state.getNumCaptures(1));
        assertEquals(true, state.isGameOver());
        assertEquals(1, state.getWinner());
    }
    
    public void testUndoPoofs() {

        SimplePoofPenteState state = (SimplePoofPenteState) createGridState(19, 19);
        
        state.addMove(state.convertMove(9, 9));
        state.setPosition(state.convertMove(9, 13), 1);
        state.addMove(state.convertMove(9, 10));
        state.setPosition(state.convertMove(9, 11), 2);
        state.setPosition(state.convertMove(9, 14), 2);

        state.addMove(state.convertMove(9, 12));
        
        assertEquals(2, state.getNumCaptures(1));
        assertEquals(2, state.getNumCaptures(2));
        
        SimplePoofPenteState state2 = (SimplePoofPenteState) createGridState(19, 19);
        state2.setPosition(state2.convertMove(9, 9), 1);
        state2.setPosition(state2.convertMove(9, 14), 2);

        assertEquals(true, state.positionEquals(state2));
        
        state.undoMove();

        assertEquals(0, state.getNumCaptures(1));
        assertEquals(0, state.getNumCaptures(2));

        state.addMove(state.convertMove(9, 12));
        assertEquals(2, state.getNumCaptures(1));
        assertEquals(2, state.getNumCaptures(2));
        assertEquals(true, state.positionEquals(state2));
    }
    
    public void testMaxPoofs() {

        SimplePoofPenteState state = (SimplePoofPenteState) createGridState(10, 10);
        state.addMove(0); //w
        state.addMove(11); //b
        state.addMove(2); //w
        state.addMove(12); //b
        state.addMove(4); //w
        state.addMove(13); //b
        state.addMove(20); //w
        state.addMove(21); //b
        state.addMove(23); //w
        state.addMove(54); //b dummy
        state.addMove(31); //w
        state.addMove(53); //b dummy
        state.addMove(32); //w
        state.addMove(52); //b dummy
        state.addMove(33); //w

        assertEquals(0, state.getNumCaptures(1));
        assertEquals(0, state.getNumCaptures(2));
        state.addMove(22); //b max poof
        
        assertEquals(5, state.getNumCaptures(1));
        assertEquals(0, state.getNumCaptures(2));
    }
    
    public void testGetWinnerPlayIntoPoof() {
        SimplePoofPenteState state =
            (SimplePoofPenteState) createGridState(10, 10);
        
        // repeatedly poof capture 4 sets of stones to get 8
        state.setPosition(0, 1);
        state.addMove(3); // w
        for (int i = 0; i < 4; i++) {
            state.addMove(1); // b
            state.addMove(10 + 2*i); // w
            state.addMove(2); // b
            state.addMove(30 + 2*i); // w
        }
        assertEquals(8, state.getNumCaptures(1));
        assertEquals(0, state.getNumCaptures(2));
        
        state.addMove(1); //b
        state.addMove(99); //w dummy
        state.addMove(2);

        assertEquals(10, state.getNumCaptures(1));
        assertEquals(0, state.getNumCaptures(2));
        assertEquals(true, state.isGameOver());
        assertEquals(1, state.getWinner());
        assertEquals(1, state.getCurrentPlayer());
    }
    
    public void testWeirdCase() {
        SimplePoofPenteState state =
            (SimplePoofPenteState) createGridState(19, 19);
        addMoves(new String[] {
            "K10", "L9", "N7", "L7", "L5", "O8", "M10", "J10", "L8", "O10",
            "M12", "O9", "O11", "O6", "O7", "P7", "K7", "M9", "N9", "L11",
            "N9", "M7", "Q6", "O8", "O7", "J6", "K9", "K8", "K11", "M8",
            "K13", "K12", "J13", "H13" }, state);
        
        assertEquals(6, state.getNumCaptures(1));
        assertEquals(6, state.getNumCaptures(2));
        
        state.addMove(getMove("M10"));
        assertEquals(8, state.getNumCaptures(1));
        assertEquals(8, state.getNumCaptures(2));
        
        state.addMove(getMove("L13"));
        assertEquals(8, state.getNumCaptures(1));
        assertEquals(10, state.getNumCaptures(2));
        assertEquals(true, state.isGameOver());
        assertEquals(2, state.getWinner());
    }
    
}