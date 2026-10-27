package org.pente.game.test;

import java.util.Arrays;

import junit.framework.*;

import org.pente.game.*;

public class TombstoneFactoryTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                TombstoneFactoryTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(TombstoneFactoryTest.class);
    }

    public TombstoneFactoryTest(String name) {
        super(name);
    }

    private static void assertFalse(boolean cond) { assertTrue(!cond); }            // junit-3.7 has no assertFalse
    private static void assertFalse(String msg, boolean cond) { assertTrue(msg, !cond); }

private static final int[] IDS = {33, 34, 83};

public void testIds() {
    assertEquals(33, GridStateFactory.TOMBSTONE);
    assertEquals(34, GridStateFactory.SPEED_TOMBSTONE);
    assertEquals(83, GridStateFactory.TB_TOMBSTONE);
}

public void testIsTombstone() {
    for (int g : IDS) assertTrue("game " + g, GridStateFactory.isTombstone(g));
    for (int g : new int[]{0, 1, 31, 32, 35, 50, 51, 81, 82, 84}) assertFalse("game " + g, GridStateFactory.isTombstone(g));
}

public void testCreateGridState() {
    for (int g : IDS) {
        GridState s = GridStateFactory.createGridState(g);
        assertTrue("game " + g, s instanceof TombstoneState);
        assertEquals(19, s.getGridSizeX());
        assertEquals(19, s.getGridSizeY());
    }
    assertEquals(19, GridStateFactory.createGridState(GridStateFactory.TOMBSTONE, 9, 9).getGridSizeX());
}

public void testMoveDataReconstructionKeepsTombstoneType() {
    DefaultGameData d = new DefaultGameData();
    d.addMove(180);
    d.addMove(3 + 19 * 3);
    d.addMove(361 + 4 + 19 * 4);
    for (int g : IDS) {
        GridState s = GridStateFactory.createGridState(g, d);
        assertTrue("game " + g, s instanceof TombstoneState);
        assertEquals(3, s.getNumMoves());
        assertEquals(3, s.getPosition(4 + 19 * 4));
        assertEquals(1, s.getCurrentPlayer());
    }
}

public void testGamesAndNames() {
    assertEquals("Tombstone Pente", GridStateFactory.getGameName(33));
    assertEquals("Speed Tombstone Pente", GridStateFactory.getGameName(34));
    assertEquals("Tombstone Pente", GridStateFactory.getGameName(83));
    assertSame(GridStateFactory.TOMBSTONE_GAME, GridStateFactory.getGame(33));
    assertSame(GridStateFactory.SPEED_TOMBSTONE_GAME, GridStateFactory.getGame(34));
    assertSame(GridStateFactory.TB_TOMBSTONE_GAME, GridStateFactory.getGame(83));
    assertTrue(GridStateFactory.SPEED_TOMBSTONE_GAME.isSpeed());
    assertEquals(33, GridStateFactory.getGameId("Tombstone Pente"));
    assertEquals(34, GridStateFactory.getGameId("Speed Tombstone Pente"));
    assertEquals("Tombstone Pente", GridStateFactory.getDisplayName(33));
    assertEquals("Speed Tombstone Pente", GridStateFactory.getDisplayName(34));
    assertEquals("Turn-based Tombstone Pente", GridStateFactory.getDisplayName(83));
    assertEquals(34, GridStateFactory.getSpeedGame(GridStateFactory.TOMBSTONE_GAME).getId());
    assertEquals(33, GridStateFactory.getNormalGame(GridStateFactory.SPEED_TOMBSTONE_GAME).getId());
    assertTrue(GridStateFactory.isSpeedGame(34));
    assertFalse(GridStateFactory.isSpeedGame(33));
    assertTrue(GridStateFactory.isTurnbasedGame(83));
    assertEquals(33, GridStateFactory.getNormalGameFromTurnbased(83));
    assertTrue(GridStateFactory.isValidGame(33));
    assertTrue(GridStateFactory.isValidGame(34));
    assertFalse(GridStateFactory.isValidGame(35));
    assertEquals(83, GridStateFactory.getMaxGameId());
}

public void testArrayPlacement() {
    Game[] all = GridStateFactory.getAllGames();
    assertEquals(52, all.length);
    assertSame(GridStateFactory.RENJU_GAME, all[31]);
    assertSame(GridStateFactory.SPEED_RENJU_GAME, all[32]);
    assertSame(GridStateFactory.TOMBSTONE_GAME, all[33]);
    assertSame(GridStateFactory.SPEED_TOMBSTONE_GAME, all[34]);
    assertSame(GridStateFactory.TB_PENTE_GAME, all[35]);
    assertSame(GridStateFactory.TB_TOMBSTONE_GAME, all[51]);
    Game[] tb = GridStateFactory.getTbGames();
    assertEquals(17, tb.length);
    assertSame(GridStateFactory.TB_RENJU_GAME, tb[15]);
    assertSame(GridStateFactory.TB_TOMBSTONE_GAME, tb[16]);
    for (Game g : tb) assertSame(g, GridStateFactory.getGame(g.getId()));   // TB index formula holds for all
    Game[] normal = GridStateFactory.getNormalGames();
    assertEquals(17, normal.length);
    assertSame(GridStateFactory.TOMBSTONE_GAME, normal[16]);
    Game[] speed = GridStateFactory.getSpeedGames();
    assertEquals(17, speed.length);
    assertSame(GridStateFactory.SPEED_TOMBSTONE_GAME, speed[16]);
    Game[] display = GridStateFactory.getDisplayGames();
    assertEquals(51, display.length);
    assertSame(GridStateFactory.TOMBSTONE_GAME, display[16]);
    assertEquals(83, display[33].getId());
    assertEquals("Turn-based Tombstone Pente", display[33].getName());
    assertSame(GridStateFactory.SPEED_TOMBSTONE_GAME, display[50]);
}

public void testKothAndGlobalArraysUnchanged() {
    assertEquals(32, GridStateFactory.LIVE_GAMES.length);
    assertEquals(16, GridStateFactory.TB_GAMES.length);
    assertEquals(48, GridStateFactory.ALL_GAMES.length);
    for (int g : GridStateFactory.ALL_GAMES) assertFalse("game " + g, GridStateFactory.isTombstone(g));
    assertTrue(Arrays.equals(new int[]{33, 34}, GridStateFactory.NON_KOTH_LIVE_GAMES));
    assertTrue(Arrays.equals(new int[]{83}, GridStateFactory.NON_KOTH_TB_GAMES));
}

public void testSetAndCentreRulesUnchanged() {
    for (int g : IDS) {
        assertFalse("game " + g, GridStateFactory.isSingleGameSet(g));          // D6
        assertFalse("game " + g, GridStateFactory.firstMoveCanBeOffCenter(g));
        assertEquals("game " + g, 180, GridStateFactory.getCenterMove(g));
    }
}

public void testArchiveNoNextMove() {
    for (int g : IDS) assertEquals(723, TombstoneCodes.archiveNoNextMove(g));
    for (int g : new int[]{1, 19, 31, 81}) assertEquals(361, TombstoneCodes.archiveNoNextMove(g));
}

public void testPrototypeColourIsZero() {
    assertEquals(0, GridStateFactory.getColor(0, GridStateFactory.TOMBSTONE));   // spec §5.3: prototype has no history
}
}
