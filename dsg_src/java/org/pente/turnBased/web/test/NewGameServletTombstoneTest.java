package org.pente.turnBased.web.test;

import junit.framework.*;

import org.pente.game.GridStateFactory;
import org.pente.turnBased.web.NewGameServlet;

/** Tombstone Pente is never offered as a TB King of the Hill game. */
public class NewGameServletTombstoneTest extends TestCase {

    public NewGameServletTombstoneTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                NewGameServletTombstoneTest.class.getName()});
    }

    public void testKothRejectedForTombstone() {
        assertEquals("King of the Hill is not available for Tombstone Pente.",
                NewGameServlet.kothUnavailableError(GridStateFactory.TB_TOMBSTONE));
    }

    public void testKothAllowedForOtherTbGames() {
        assertNull(NewGameServlet.kothUnavailableError(GridStateFactory.TB_PENTE));
        assertNull(NewGameServlet.kothUnavailableError(GridStateFactory.TB_RENJU));
    }
}
