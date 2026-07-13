package org.pente.gameServer.server.test;

import java.util.*;
import junit.framework.TestCase;
import org.pente.gameServer.server.MMAIProtocol;

/** Pure-unit tests for the mmai sidecar wire protocol (spec §8.1). No process. */
public class MMAIProtocolTest extends TestCase {

    public MMAIProtocolTest(String name) {
        super(name);
    }

    public void testEncodeMoveRequest() {
        assertEquals("MOVE 1 8 3 180 182 200",
            MMAIProtocol.encodeMoveRequest(1, 8, Arrays.asList(180, 182, 200)));
        assertEquals("MOVE 13 1 0",
            MMAIProtocol.encodeMoveRequest(13, 1, new ArrayList<Integer>()));
    }

    public void testParseOkReply() throws Exception {
        assertEquals(200, MMAIProtocol.parseOkReply("OK 200"));
        assertEquals(0, MMAIProtocol.parseOkReply("OK 0"));
        assertEquals(-1, MMAIProtocol.parseOkReply("OK -1")); // caller treats < 0 as failure
    }

    public void testParseErrReplyThrows() {
        try {
            MMAIProtocol.parseOkReply("ERR data file load failure under /x");
            fail("expected ProtocolException");
        } catch (MMAIProtocol.ProtocolException expected) {
            assertTrue(expected.getMessage().contains("data file load failure"));
        }
    }

    public void testParseGarbageAndNullThrow() {
        String[] bad = {null, "", "GARBAGE", "OK", "OK notanumber", "OK 1 2"};
        for (String line : bad) {
            try {
                MMAIProtocol.parseOkReply(line);
                fail("expected ProtocolException for: " + line);
            } catch (MMAIProtocol.ProtocolException expected) {
            }
        }
    }

    public void testIsConnect6() {
        assertTrue(MMAIProtocol.isConnect6(13));
        assertTrue(MMAIProtocol.isConnect6(14)); // Speed twin
        assertTrue(!MMAIProtocol.isConnect6(1)); // junit-3.7 has no assertFalse
        assertTrue(!MMAIProtocol.isConnect6(25));
    }

    public void testPendingMoveTwoStoneTurn() {
        MMAIProtocol.PendingMove pm = new MMAIProtocol.PendingMove();
        assertTrue(!pm.hasPending());
        // packed two-stone turn: m1=100, m2=101 -> 100*362+101 = 36301
        assertEquals(100, pm.acceptPacked(100 * 362 + 101));
        assertTrue(pm.hasPending());
        assertEquals(101, pm.consume()); // return-then-clear
        assertTrue(!pm.hasPending());
        assertEquals(-1, pm.consume()); // consuming empty is -1
    }

    public void testPendingMoveSingleStoneSentinel() {
        MMAIProtocol.PendingMove pm = new MMAIProtocol.PendingMove();
        // m1=180, m2=361 sentinel -> single-stone turn, nothing cached
        assertEquals(180, pm.acceptPacked(180 * 362 + 361));
        assertTrue(!pm.hasPending());
    }

    public void testMoveOwnerStandardAlternates() {
        // Every non-Connect6 game must stay byte-identical to moveNum % 2 + 1.
        int[] games = {1, 2, 3, 11, 15, 25};
        for (int g : games) {
            for (int m = 0; m < 8; m++) {
                assertEquals(m % 2 + 1, MMAIProtocol.moveOwner(g, m));
            }
        }
    }

    public void testMoveOwnerConnect6TwoStonePattern() {
        // P1 opens with one stone, then two stones per turn:
        // owner = P1, P2, P2, P1, P1, P2, P2, P1, P1 ...
        int[] expected = {1, 2, 2, 1, 1, 2, 2, 1, 1, 2, 2, 1};
        for (int m = 0; m < expected.length; m++) {
            assertEquals(expected[m], MMAIProtocol.moveOwner(13, m));
            assertEquals(expected[m], MMAIProtocol.moveOwner(14, m)); // Speed twin
        }
    }

    public void testPendingMoveClear() {
        // clear() is the undoMove()/stopThinking() path (spec §6.2)
        MMAIProtocol.PendingMove pm = new MMAIProtocol.PendingMove();
        pm.acceptPacked(100 * 362 + 101);
        assertTrue(pm.hasPending());
        pm.clear();
        assertTrue(!pm.hasPending());
        assertEquals(-1, pm.consume());
    }
}
