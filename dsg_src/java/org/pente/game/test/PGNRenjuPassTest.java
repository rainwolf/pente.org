package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class PGNRenjuPassTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{PGNRenjuPassTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(PGNRenjuPassTest.class);
    }

    public PGNRenjuPassTest(String name) {
        super(name);
    }

    public void testPassTokenRoundTrip() {
        assertEquals("pass", PGNGameFormat.formatMoveToken(225, true));
        assertEquals(225, PGNGameFormat.parseMoveToken("pass", true));
        // non-pass moves unchanged
        assertEquals(PGNGameFormat.formatCoordinates(100),
                PGNGameFormat.formatMoveToken(100, true));
    }
}
