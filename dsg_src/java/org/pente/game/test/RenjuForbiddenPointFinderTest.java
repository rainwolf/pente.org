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

    // ---- diagonal coverage (dir 3 = '/', x & y increase together) ----

    // Black exact five along dir 3: (3,3),(4,4),(5,5),(6,6) then filling (7,7) makes 5.
    public void testDiagonalDir3ExactFiveIsFive() {
        f.setStone(3, 3, RenjuForbiddenPointFinder.BLACK);
        f.setStone(4, 4, RenjuForbiddenPointFinder.BLACK);
        f.setStone(5, 5, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 6, RenjuForbiddenPointFinder.BLACK);
        assertTrue(f.isFive(7, 7, 0));
        assertTrue(f.isFive(7, 7, 0, 3)); // single-direction variant along dir 3
    }

    // Black exact five along dir 4 ('\', x+y constant): (3,7),(4,6),(5,5),(6,4) then (7,3).
    public void testDiagonalDir4ExactFiveIsFive() {
        f.setStone(3, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(4, 6, RenjuForbiddenPointFinder.BLACK);
        f.setStone(5, 5, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 4, RenjuForbiddenPointFinder.BLACK);
        assertTrue(f.isFive(7, 3, 0));
        assertTrue(f.isFive(7, 3, 0, 4)); // single-direction variant along dir 4
    }

    // A 6-long black diagonal is an overline, not a five.
    public void testDiagonalSixIsOverlineNotFive() {
        f.setStone(2, 2, RenjuForbiddenPointFinder.BLACK);
        f.setStone(3, 3, RenjuForbiddenPointFinder.BLACK);
        f.setStone(4, 4, RenjuForbiddenPointFinder.BLACK);
        f.setStone(5, 5, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 6, RenjuForbiddenPointFinder.BLACK);
        assertTrue(f.isOverline(7, 7));   // filling (7,7) makes 6 along dir 3
        assertTrue(!f.isFive(7, 7, 0));   // 6 is not an exact five for black
    }

    // Diagonal double-three: an open three on dir 3 ((5,5),(6,6)) and one on dir 4
    // ((5,9),(6,8)) both meet at (7,7) -> double-three -> forbidden.
    public void testDiagonalDoubleThreeForbidden() {
        f.setStone(5, 5, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 6, RenjuForbiddenPointFinder.BLACK);
        f.setStone(5, 9, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 8, RenjuForbiddenPointFinder.BLACK);
        assertTrue(f.isOpenThree(7, 7, 0, 3));
        assertTrue(f.isOpenThree(7, 7, 0, 4));
        assertTrue(f.isDoubleThree(7, 7));
        assertTrue(f.isForbidden(7, 7));
    }

    // isOpenFour == 2: the 5-stone "double-blockable" variant. Shape X . X X X . X on
    // row 7 (black at 4,6,8,10): filling either gap (5,7) or (9,7) completes a five, so
    // placing (7,7) yields an open four whose contiguous run is 3 (nLine != 4) -> ret 2.
    // That single line is itself a double-four (two five-completions), hence forbidden.
    public void testOpenFourReturnsTwo() {
        f.setStone(4, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(8, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(10, 7, RenjuForbiddenPointFinder.BLACK);
        assertEquals(2, f.isOpenFour(7, 7, 0, 1));
        assertTrue(!f.isFive(7, 7, 0));   // run is broken: not a five
        assertTrue(!f.isOverline(7, 7));  // and not an overline
        assertTrue(f.isDoubleFour(7, 7)); // the ret-2 line counts as two fours
    }

    // A one-sided (closed) simple four: white blocks the left end at (3,7), black at
    // (4,7),(5,7),(6,7); placing (7,7) makes a four that completes a five only to the
    // right -> isFour true, but it is NOT an open four (blocked) -> isOpenFour == 0.
    public void testOneSidedSimpleFourNotOpen() {
        f.setStone(3, 7, RenjuForbiddenPointFinder.WHITE);
        f.setStone(4, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(5, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 7, RenjuForbiddenPointFinder.BLACK);
        assertTrue(f.isFour(7, 7, 0, 1));
        assertEquals(0, f.isOpenFour(7, 7, 0, 1));
    }

    // Mutual-recursion exclusion in isOpenThree: a candidate three is NOT an open three
    // when its open-end four-completion is itself a double-four.
    //
    // Control: black (6,7),(7,7); candidate (5,7) is a clean open three on dir 1.
    public void testOpenThreeCleanControl() {
        f.setStone(6, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 7, RenjuForbiddenPointFinder.BLACK);
        assertTrue(f.isOpenThree(5, 7, 0, 1));
    }

    // Exclusion: keep (6,7),(7,7), block the left completion with white (3,7), and add a
    // vertical run (8,4),(8,5),(8,6) so the right completion point (8,7) is a DOUBLE-FOUR.
    // The right end is still geometrically an open-four completion (isOpenFour==1) but the
    // !isDoubleFour guard suppresses it, so isOpenThree(5,7,...) is false.
    public void testOpenThreeExcludedByDoubleFourCompletion() {
        f.setStone(6, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(8, 4, RenjuForbiddenPointFinder.BLACK);
        f.setStone(8, 5, RenjuForbiddenPointFinder.BLACK);
        f.setStone(8, 6, RenjuForbiddenPointFinder.BLACK);
        f.setStone(3, 7, RenjuForbiddenPointFinder.WHITE);

        // With the candidate filled, the completion (8,7) IS an open four but also a
        // double-four (horizontal + vertical) -> the open-three guard excludes it.
        f.setStone(5, 7, RenjuForbiddenPointFinder.BLACK);
        assertEquals(1, f.isOpenFour(8, 7, 0, 1));
        assertTrue(f.isDoubleFour(8, 7));
        f.setStone(5, 7, RenjuForbiddenPointFinder.EMPTY);

        assertTrue(!f.isOpenThree(5, 7, 0, 1));
        assertTrue(!f.isDoubleThree(5, 7)); // and therefore (5,7) is not a double-three
    }

    // findForbiddenPoints() must list a known forbidden point and omit a known-legal one.
    public void testFindForbiddenPointsContainsAndExcludes() {
        f.setStone(5, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 5, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 6, RenjuForbiddenPointFinder.BLACK);
        java.util.List<Coord> pts = f.findForbiddenPoints();
        boolean hasForbidden = false;
        boolean hasLegal = false;
        for (int i = 0; i < pts.size(); i++) {
            Coord c = pts.get(i);
            if (c.x == 7 && c.y == 7) hasForbidden = true;
            if (c.x == 0 && c.y == 0) hasLegal = true;
        }
        assertTrue(hasForbidden);   // the double-three point (7,7) is forbidden
        assertTrue(!hasLegal);      // an empty corner (0,0) is legal
    }
}
