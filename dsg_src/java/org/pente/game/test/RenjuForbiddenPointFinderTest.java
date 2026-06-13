package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class RenjuForbiddenPointFinderTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                RenjuForbiddenPointFinderTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(RenjuForbiddenPointFinderTest.class);
    }

    public RenjuForbiddenPointFinderTest(String name) {
        super(name);
    }

    private RenjuForbiddenPointFinder f;

    protected void setUp() {
        f = new RenjuForbiddenPointFinder(15);
    }

    // place a horizontal run of color c starting at (x,y) going right
    private void row(int x, int y, int len, char c) {
        for (int k = 0; k < len; k++) f.setStone(x + k, y, c);
    }

    public void testBlackExactFiveIsFive() {
        row(3, 7, 4, RenjuForbiddenPointFinder.BLACK); // x=3,4,5,6
        assertTrue(f.isFive(7, 7, 0));                  // filling x=7 makes 5
    }

    public void testBlackSixIsNotFive() {
        row(3, 7, 5, RenjuForbiddenPointFinder.BLACK); // x=3..7
        assertTrue(!f.isFive(8, 7, 0));                 // filling x=8 makes 6, not exact 5
    }

    public void testWhiteFiveIsFive() {
        row(3, 7, 4, RenjuForbiddenPointFinder.WHITE);
        assertTrue(f.isFive(7, 7, 1));
    }

    public void testWhiteSixIsFive() {
        row(3, 7, 5, RenjuForbiddenPointFinder.WHITE);
        assertTrue(f.isFive(8, 7, 1));                  // white wins on 6 too
    }

    public void testBlackOverline() {
        row(3, 7, 5, RenjuForbiddenPointFinder.BLACK); // x=3..7
        assertTrue(f.isOverline(8, 7));                 // filling x=8 makes 6
    }

    public void testExactFiveIsNotOverline() {
        row(3, 7, 4, RenjuForbiddenPointFinder.BLACK);
        assertTrue(!f.isOverline(7, 7));                // exactly 5 is a win, not overline
    }

    public void testForbiddenOverline() {
        row(3, 7, 5, RenjuForbiddenPointFinder.BLACK);
        assertTrue(f.isForbidden(8, 7));
    }

    // Double-three: two independent open threes meeting at (7,7).
    // Horizontal open three: stones at (5,7),(6,7); vertical open three: (7,5),(7,6).
    public void testDoubleThreeForbidden() {
        f.setStone(5, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 5, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 6, RenjuForbiddenPointFinder.BLACK);
        assertTrue(f.isDoubleThree(7, 7));
        assertTrue(f.isForbidden(7, 7));
    }

    // Single open three is NOT forbidden.
    public void testSingleThreeNotForbidden() {
        f.setStone(5, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 7, RenjuForbiddenPointFinder.BLACK);
        assertTrue(!f.isDoubleThree(7, 7));
        assertTrue(!f.isForbidden(7, 7));
    }

    // Double-four: two fours through (7,7).
    // Horizontal: (4,7),(5,7),(6,7) and (8,7),(9,7),(10,7) -> playing (7,7) makes 7-in-line
    // which is overline, so use two separate four-threats instead:
    // Horizontal four: (3,7),(4,7),(5,7),(6,7) already 4 -> (7,7) makes five (excluded).
    // Use a gapped four on each axis so (7,7) completes a four (not five) in two directions.
    // Horizontal: stones (4,7),(5,7),(6,7) with empty (3,7) and (8,7): (7,7) -> four.
    // Vertical:   stones (7,4),(7,5),(7,6) with empty (7,3) and (7,8): (7,7) -> four.
    public void testDoubleFourForbidden() {
        f.setStone(4, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(5, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 4, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 5, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 6, RenjuForbiddenPointFinder.BLACK);
        assertTrue(f.isDoubleFour(7, 7));
        assertTrue(f.isForbidden(7, 7));
    }

    public void testEmptyBoardNoForbidden() {
        assertTrue(!f.isForbidden(7, 7));
    }
}
