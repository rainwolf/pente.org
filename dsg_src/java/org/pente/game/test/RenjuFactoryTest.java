package org.pente.game.test;

import junit.framework.*;

import org.pente.game.*;

/**
 * Verifies RenjuState is correctly wired into GridStateFactory:
 * id allocation, state creation (incl. board size + type), the MoveData
 * reconstruction path, and the normal/speed/turn-based metadata mappings.
 */
public class RenjuFactoryTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                RenjuFactoryTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(RenjuFactoryTest.class);
    }

    public RenjuFactoryTest(String name) {
        super(name);
    }

    public void testRenjuIds() {
        assertEquals(31, GridStateFactory.RENJU);
        assertEquals(32, GridStateFactory.SPEED_RENJU);
        assertEquals(81, GridStateFactory.TB_RENJU);
    }

    public void testCreatesRenjuState15x15() {
        GridState normal = GridStateFactory.createGridState(GridStateFactory.RENJU);
        assertTrue(normal instanceof RenjuState);
        assertEquals(15, normal.getGridSizeX());
        assertEquals(15, normal.getGridSizeY());

        GridState speed = GridStateFactory.createGridState(GridStateFactory.SPEED_RENJU);
        assertTrue(speed instanceof RenjuState);
        assertEquals(15, speed.getGridSizeX());

        GridState tb = GridStateFactory.createGridState(GridStateFactory.TB_RENJU);
        assertTrue(tb instanceof RenjuState);
        assertEquals(15, tb.getGridSizeX());
    }

    // The reconstruct-from-moves path must return a RenjuState, not a bare gomoku.
    public void testMoveDataReconstructionKeepsRenjuType() {
        SimpleGridState moves = new SimpleGridState(15, 15);
        moves.addMove(moves.convertMove(7, 7)); // a center opening move

        GridState live = GridStateFactory.createGridState(GridStateFactory.RENJU, moves);
        assertTrue(live instanceof RenjuState);
        assertEquals(15, live.getGridSizeX());
        assertEquals(1, live.getNumMoves());

        GridState tb = GridStateFactory.createGridState(GridStateFactory.TB_RENJU, moves);
        assertTrue(tb instanceof RenjuState);
        assertEquals(15, tb.getGridSizeX());
    }

    public void testGameMetadata() {
        assertEquals("Renju", GridStateFactory.getGameName(GridStateFactory.RENJU));
        assertEquals("Speed Renju", GridStateFactory.getGameName(GridStateFactory.SPEED_RENJU));
        assertEquals("Renju", GridStateFactory.getGameName(GridStateFactory.TB_RENJU));

        assertTrue(GridStateFactory.isValidGame(GridStateFactory.RENJU));
        assertTrue(GridStateFactory.isValidGame(GridStateFactory.SPEED_RENJU));
        assertEquals(GridStateFactory.TB_RENJU, GridStateFactory.getMaxGameId());
    }

    public void testNormalSpeedTurnbasedMappings() {
        Game normal = GridStateFactory.getGame(GridStateFactory.RENJU);
        assertEquals(GridStateFactory.SPEED_RENJU, GridStateFactory.getSpeedGame(normal).getId());

        Game speed = GridStateFactory.getGame(GridStateFactory.SPEED_RENJU);
        assertEquals(GridStateFactory.RENJU, GridStateFactory.getNormalGame(speed).getId());

        assertTrue(GridStateFactory.isSpeedGame(GridStateFactory.SPEED_RENJU));
        assertTrue(!GridStateFactory.isSpeedGame(GridStateFactory.RENJU));

        assertTrue(GridStateFactory.isTurnbasedGame(GridStateFactory.TB_RENJU));
        assertEquals(GridStateFactory.RENJU,
                GridStateFactory.getNormalGameFromTurnbased(GridStateFactory.TB_RENJU));
    }

    public void testRenjuIdLookupByName() {
        assertEquals(GridStateFactory.RENJU, GridStateFactory.getGameId("Renju"));
    }

    // The auto-placed opening "center" move must come from the game's board size:
    // 19x19 games -> 180 (9+9*19), Renju 15x15 -> 112 (7+7*15). A hardcoded 180
    // for Renju lands at (0,12) = "A3" instead of the center.
    public void testCenterMoveIsBoardAware() {
        assertEquals(180, GridStateFactory.getCenterMove(GridStateFactory.PENTE));
        assertEquals(180, GridStateFactory.getCenterMove(GridStateFactory.TB_PENTE));
        assertEquals(112, GridStateFactory.getCenterMove(GridStateFactory.RENJU));
        assertEquals(112, GridStateFactory.getCenterMove(GridStateFactory.TB_RENJU));
    }
}
