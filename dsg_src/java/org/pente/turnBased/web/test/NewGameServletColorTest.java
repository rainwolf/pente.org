package org.pente.turnBased.web.test;

import junit.framework.*;

import org.pente.game.GridStateFactory;
import org.pente.turnBased.web.NewGameServlet;

/**
 * The invitation colour dropdown sends 1 = White, 2 = Black.  NewGameServlet maps
 * that to the player slot the inviter takes.  Player 1 is white in the pente
 * variants but BLACK in Go and renju, so for those games choosing Black must mean
 * playing as player 1 (the colour -> slot mapping inverts).
 */
public class NewGameServletColorTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                NewGameServletColorTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(NewGameServletColorTest.class);
    }

    public NewGameServletColorTest(String name) {
        super(name);
    }

    private static final int WHITE = 1;
    private static final int BLACK = 2;

    public void testPenteIsWhiteFirst() {
        assertTrue(!NewGameServlet.isBlackFirst(GridStateFactory.TB_PENTE));
        // pente: white -> player 1, black -> player 2 (unchanged)
        assertEquals(1, NewGameServlet.inviterSlot(GridStateFactory.TB_PENTE, WHITE));
        assertEquals(2, NewGameServlet.inviterSlot(GridStateFactory.TB_PENTE, BLACK));
    }

    public void testGomokuIsWhiteFirst() {
        assertTrue(!NewGameServlet.isBlackFirst(GridStateFactory.TB_GOMOKU));
        assertEquals(1, NewGameServlet.inviterSlot(GridStateFactory.TB_GOMOKU, WHITE));
        assertEquals(2, NewGameServlet.inviterSlot(GridStateFactory.TB_GOMOKU, BLACK));
    }

    // The reported bug: for renju, choosing Black must put the inviter in slot 1.
    public void testRenjuBlackChoosesPlayer1() {
        assertTrue(NewGameServlet.isBlackFirst(GridStateFactory.TB_RENJU));
        assertEquals(1, NewGameServlet.inviterSlot(GridStateFactory.TB_RENJU, BLACK));
        assertEquals(2, NewGameServlet.inviterSlot(GridStateFactory.TB_RENJU, WHITE));
    }

    // Verify Go behaves the same (p1 is black) across all three board sizes.
    public void testGoVariantsBlackChoosesPlayer1() {
        int[] goGames = {
                GridStateFactory.TB_GO,
                GridStateFactory.TB_GO9,
                GridStateFactory.TB_GO13
        };
        for (int g : goGames) {
            assertTrue("Go should be black-first: " + g,
                    NewGameServlet.isBlackFirst(g));
            assertEquals("black -> player 1 for Go " + g, 1,
                    NewGameServlet.inviterSlot(g, BLACK));
            assertEquals("white -> player 2 for Go " + g, 2,
                    NewGameServlet.inviterSlot(g, WHITE));
        }
    }
}
