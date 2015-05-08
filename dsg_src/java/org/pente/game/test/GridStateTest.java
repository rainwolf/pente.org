package org.pente.game.test;

import java.awt.*;

import junit.framework.*;

import org.pente.game.*;
import org.pente.gameServer.core.*;

public class GridStateTest extends TestCase {

    public static void main(String[] args) {
        //junit.swingui.TestRunner.main(new String[] {
        //    SimpleGridStateTest.class.getName()
        //});
        //junit.awtui.TestRunner.main(new String[] {
        //    SimpleGridStateTest.class.getName()
        //});
        junit.textui.TestRunner.main(new String[] {
            GridStateTest.class.getName()
        });
    }

    public static Test suite() {
        return new TestSuite(GridStateTest.class);
    }

    public GridStateTest(String name) {
        super(name);
    }

    protected void setUp() {
    }

    protected void tearDown() {
    }

    GridState createGridState(int x, int y) {
        return new SimpleGridState(x, y);
    }

    public void testSetGetPosition() {
        GridState state1 = createGridState(10, 10);

        state1.setPosition(50, 1);
        assertEquals(1, state1.getPosition(50));
        assertEquals(1, state1.getPosition(0, 5));

        state1.setPosition(1, 5, 2);
        assertEquals(2, state1.getPosition(51));
    }

    /** Make sure the position equals method works so we can test
     *  more important things.
     */
    public void testPositionEquals() {
        GridState state1 = createGridState(10, 10);
        GridState state2 = createGridState(11, 10);
        GridState state3 = createGridState(10, 11);
        GridState state4 = createGridState(10, 10);

        assertEquals(false, state1.positionEquals(state2));
        assertEquals(false, state1.positionEquals(state3));

        state1.setPosition(5, 5, 1);
        assertEquals(false, state1.positionEquals(state2));

        state4.setPosition(5, 5, 1);
        assertEquals(true, state1.positionEquals(state4));

        state4.setPosition(5, 5, 2);
        assertEquals(false, state1.positionEquals(state4));

        state4.setPosition(5, 5, 1);
        state4.setPosition(0, 0, 2);
        assertEquals(false, state1.positionEquals(state4));
    }

    public void testConvertMove() {
        GridState state1 = createGridState(5, 6);
        assertEquals(13, state1.convertMove(3, 2));
        assertEquals(new Point(3, 2), state1.convertMove(13));

        GridState state2 = createGridState(19, 10);
        assertEquals(0, state2.convertMove(0, 0));
        assertEquals(120, state2.convertMove(6, 6));
    }

    public void testClear() {
        GridState state1 = createGridState(10, 10);
        assertEquals(0, state1.getNumMoves());
        assertEquals(1, state1.getCurrentPlayer());
        state1.addMove(50);
        state1.addMove(60);
        state1.addMove(0);
        state1.addMove(10 * 10 - 1);
        assertEquals(4, state1.getNumMoves());
        assertEquals(1, state1.getCurrentPlayer());
        state1.clear();
        assertEquals(0, state1.getNumMoves());
        assertEquals(1, state1.getCurrentPlayer());

        GridState state2 = createGridState(10, 10);
        assertEquals(true, state1.positionEquals(state2));
    }

    public void testIsValidMove() {
        GridState state1 = createGridState(10, 10);

        // out of bounds
        assertEquals(true, state1.isValidMove(0, 1));
        assertEquals(true, state1.isValidMove(15, 1));
        assertEquals(false, state1.isValidMove(-1, 1));
        assertEquals(true, state1.isValidMove(10 * 10 - 1, 1));
        assertEquals(false, state1.isValidMove(10 * 10, 1));

        // not right player
        assertEquals(false, state1.isValidMove(15, 2));
        assertEquals(false, state1.isValidMove(15, 3));
        assertEquals(false, state1.isValidMove(15, 0));

        // not already played
        state1.addMove(15);
        assertEquals(false, state1.isValidMove(15, 1));
        assertEquals(false, state1.isValidMove(15, 2));
    }

    public void testAddMove() {
        GridState state1 = createGridState(10, 10);
        state1.addMove(50);
        state1.addMove(51);
        assertEquals(2, state1.getNumMoves());

        GridState state2 = createGridState(10, 10);
        state2.setPosition(50, 1);
        state2.setPosition(51, 2);
        assertEquals(true, state1.positionEquals(state2));

        try {
            state1.addMove(-1);
            fail("Illegal argument not thrown");
        } catch (IllegalArgumentException ex) {
        }
        try {
            state1.addMove(10 * 10);
            fail("Illegal argument not thrown");
        } catch (IllegalArgumentException ex) {
        }

        assertEquals(2, state1.getNumMoves());

        // get move
        assertEquals(50, state1.getMove(0));
        assertEquals(51, state1.getMove(1));

        try {
            assertEquals(52, state1.getMove(2));
            fail("Out of bounds not caught");
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            assertEquals(52, state1.getMove(-1));
            fail("Out of bounds not caught");
        } catch (ArrayIndexOutOfBoundsException e) {
        }
    }

    public void testUndoMove() {
        GridState state1 = createGridState(10, 10);
        state1.addMove(50);
        state1.addMove(51);
        state1.undoMove();

        GridState state2 = createGridState(10, 10);
        state2.addMove(50);

        assertEquals(true, state1.positionEquals(state2));
        assertEquals(state1.getNumMoves(), state2.getNumMoves());
        assertEquals(state1.getCurrentPlayer(), state2.getCurrentPlayer());

        state1.clear();
        state1.undoMove();
    }

    void addMoves(String moves[], GridState state) {
        
        for (int i = 0; i < moves.length; i++) {
            state.addMove(getMove(moves[i]));
        }
    }
    
    GridState gridState = GridStateFactory.createGridState(GridStateFactory.GOMOKU);
    GridCoordinates coordinates = new AlphaNumericGridCoordinates(19, 19);
    int getMove(String move) {
        
        Point p = coordinates.getPoint(move);
        return gridState.convertMove(p.x, p.y);
    }

    void printBoard(int board[][]) {
        
        int index = 0;
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board[0].length; j++) {
                System.out.print(board[j][i] + " ");
            }
            System.out.println();
        }
        System.out.println();
    }
}