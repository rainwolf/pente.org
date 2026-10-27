package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

import java.text.ParseException;
import java.util.Date;

public class PGNTombstoneTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{PGNTombstoneTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(PGNTombstoneTest.class);
    }

    public PGNTombstoneTest(String name) {
        super(name);
    }

    // bundled JUnit 3.7 has no assertFalse
    private static void assertNotTrue(String msg, boolean cond) {
        assertTrue(msg, !cond);
    }

    private static GameData game(String name, int... codes) {
        GameData data = new DefaultGameData();
        data.setGame(name);
        data.setDate(new Date());
        data.setTimed(false);
        data.setRated(false);
        data.getPlayer1Data().setUserIDName("alice");
        data.getPlayer2Data().setUserIDName("bob");
        data.setWinner(GameData.UNKNOWN);
        for (int c : codes) data.addMove(c);
        return data;
    }

    private static String format(GameData data) {
        StringBuffer buffer = new StringBuffer();
        new PGNGameFormat("\n").format(data, buffer);
        return buffer.toString();
    }

    private static GameData parse(String pgn) throws ParseException {
        GameData parsed = new DefaultGameData();
        new PGNGameFormat("\n").parse(parsed, new StringBuffer(pgn));
        return parsed;
    }

    // centre; P2 D16 + dead E15; P1 A1; P2 pass + dead A19; P1 pass
    private static final int[] CODES = {180, 60, 361 + 80, 342, 722, 361, 722};

    public void testFormatAndParseRoundTrip() throws ParseException {
        String pgn = format(game("Tombstone Pente", CODES));
        assertTrue(pgn, pgn.contains("xE15"));
        assertTrue(pgn, pgn.contains("xA19"));
        assertTrue(pgn, pgn.contains(" pass "));
        assertNotTrue("old garbage token for 722: " + pgn, pgn.contains("A-19"));
        GameData parsed = parse(pgn);
        assertEquals(CODES.length, parsed.getNumMoves());
        for (int i = 0; i < CODES.length; i++) assertEquals("entry " + i, CODES[i], parsed.getMove(i));
    }

    public void testSpeedTombstoneUsesTokens() throws ParseException {
        String pgn = format(game("Speed Tombstone Pente", 180, 722, 361));
        assertTrue(pgn, pgn.contains("xA19"));
        GameData parsed = parse(pgn);
        assertEquals(3, parsed.getNumMoves());
        assertEquals(722, parsed.getMove(1));
        assertEquals(361, parsed.getMove(2));
    }

    public void testParseAcceptsUpperCasePass() throws ParseException {
        String pgn = format(game("Tombstone Pente", CODES)).replace(" pass ", " PASS ");
        assertTrue("no pass token was written: " + pgn, pgn.contains(" PASS "));
        GameData parsed = parse(pgn);
        assertEquals(722, parsed.getMove(4));
        assertEquals(722, parsed.getMove(6));
    }

    public void testOtherGamesUnchanged() throws ParseException {
        String pgn = format(game("Pente", 180, 181));
        assertTrue(pgn, pgn.contains("K10 L10"));
        GameData parsed = parse(pgn);
        assertEquals(2, parsed.getNumMoves());
        assertEquals(181, parsed.getMove(1));
    }
}
