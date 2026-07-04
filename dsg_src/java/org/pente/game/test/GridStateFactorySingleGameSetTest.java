package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class GridStateFactorySingleGameSetTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{GridStateFactorySingleGameSetTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(GridStateFactorySingleGameSetTest.class);
    }

    public GridStateFactorySingleGameSetTest(String name) {
        super(name);
    }

    public void testGoFamilyIsSingleGameSet() {
        int[] go = {
                GridStateFactory.GO, GridStateFactory.GO9, GridStateFactory.GO13,
                GridStateFactory.SPEED_GO, GridStateFactory.SPEED_GO9, GridStateFactory.SPEED_GO13,
                GridStateFactory.TB_GO, GridStateFactory.TB_GO9, GridStateFactory.TB_GO13
        };
        for (int game : go) {
            assertTrue("game " + game, GridStateFactory.isSingleGameSet(game));
        }
    }

    public void testRenjuFamilyIsSingleGameSet() {
        int[] renju = {
                GridStateFactory.RENJU, GridStateFactory.SPEED_RENJU, GridStateFactory.TB_RENJU
        };
        for (int game : renju) {
            assertTrue("game " + game, GridStateFactory.isSingleGameSet(game));
        }
    }

    public void testNonSingleGameSetGamesAreFalse() {
        int[] notSingle = {
                GridStateFactory.PENTE, GridStateFactory.GOMOKU, GridStateFactory.SPEED_GOMOKU,
                GridStateFactory.TB_GOMOKU
        };
        for (int game : notSingle) {
            assertTrue("game " + game, !GridStateFactory.isSingleGameSet(game));
        }
    }
}
