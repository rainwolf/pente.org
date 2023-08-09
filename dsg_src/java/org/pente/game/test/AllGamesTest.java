package org.pente.game.test;

import junit.framework.*;

public class AllGamesTest extends TestCase {

    public AllGamesTest(String name) {
        super(name);
    }

    public static Test suite() {
        TestSuite suite = new TestSuite();

        suite.addTestSuite(GridStateTest.class);
        suite.addTestSuite(GomokuStateTest.class);
        suite.addTestSuite(PenteStateTest.class);
        suite.addTestSuite(PoofPenteTest.class);

        return suite;
    }
}
