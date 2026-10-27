package org.pente.turnBased.web.test;

import junit.framework.TestCase;
import org.pente.game.GridStateFactory;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.web.MoveServlet;

public class MoveServletTombstoneTest extends TestCase {

    public MoveServletTombstoneTest(String name) {
        super(name); // junit-3.7 TestCase has no no-arg constructor
    }

    private static TBGame tb(int game, int... codes) {
        TBGame g = new TBGame();
        g.setGame(game);
        for (int c : codes) g.addMove(c);
        return g;
    }

    public void testUndoEntryCountTombstone() {
        assertEquals(2, MoveServlet.undoEntryCount(tb(GridStateFactory.TB_TOMBSTONE, 180, 181, 561)));
        assertEquals(1, MoveServlet.undoEntryCount(tb(GridStateFactory.TB_TOMBSTONE, 180, 722)));
        assertEquals(2, MoveServlet.undoEntryCount(tb(GridStateFactory.TB_TOMBSTONE, 180, 722, 361)));
        assertEquals(0, MoveServlet.undoEntryCount(tb(GridStateFactory.TB_TOMBSTONE, 180)));
    }

    public void testUndoEntryCountOtherGamesUnchanged() {
        assertEquals(2, MoveServlet.undoEntryCount(tb(GridStateFactory.TB_CONNECT6, 180, 181, 182)));
        assertEquals(1, MoveServlet.undoEntryCount(tb(GridStateFactory.TB_PENTE, 180, 181)));
    }
}
