package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

import java.text.ParseException;
import java.util.Date;

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

    private int xy(int x, int y) {
        return x + y * 15;
    }

    /** End-to-end coverage for the isRenju derivation at the format() and
     *  parse() call sites in PGNGameFormat (not just the formatMoveToken /
     *  parseMoveToken helpers, which are unit-tested above). Builds a renju
     *  GameData with 6 opening-ish moves followed by a 225 pass, formats it,
     *  asserts the literal "pass" token appears in the output, then parses
     *  the produced PGN back and asserts the move list -- including the
     *  pass -- round-trips intact. The PGN layer does not validate renju
     *  legality, so the opening moves need not be a legal Taraguchi opening. */
    public void testFormatAndParseRoundTripThroughPass() throws ParseException {
        GameData data = new DefaultGameData();
        data.setGame(GridStateFactory.RENJU_GAME.getName());
        data.setDate(new Date());
        data.setTimed(false);
        data.setRated(false);
        data.getPlayer1Data().setUserIDName("alice");
        data.getPlayer2Data().setUserIDName("bob");
        data.setWinner(GameData.UNKNOWN);

        int[] moves = {
                xy(7, 7), xy(7, 8), xy(8, 7), xy(8, 8), xy(6, 7), xy(6, 8), // 6 opening-ish moves
                225 // pass
        };
        for (int m : moves) {
            data.addMove(m);
        }

        PGNGameFormat format = new PGNGameFormat("\n");
        StringBuffer buffer = new StringBuffer();
        format.format(data, buffer);
        String pgn = buffer.toString();

        assertTrue("formatted PGN should contain the literal 'pass' token for the renju pass move:\n" + pgn,
                pgn.contains("pass"));

        GameData parsed = new DefaultGameData();
        format.parse(parsed, new StringBuffer(pgn));

        assertEquals(moves.length, parsed.getNumMoves());
        for (int i = 0; i < moves.length; i++) {
            assertEquals("move " + i + " should round-trip", moves[i], parsed.getMove(i));
        }
        assertEquals(225, parsed.getMove(moves.length - 1));
    }
}
