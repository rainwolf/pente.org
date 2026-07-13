package org.pente.gameServer.server.test;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;
import org.pente.gameServer.server.MMAIPlayer;

/** Integration tests spawning the real mmai_player sidecar (spec §8.2).
 *  Prerequisites: ./dsg_src/mmai/build.sh has produced dsg_src/mmai/mmai_player.
 *  Run via: ant test-mmai-integration
 *  (ant's forked JVM runs in the project basedir, so relative paths work).
 *
 *  Level 1 everywhere: legality is what is under test, not strength.
 *  The engine is not deterministic — tests assert reply legality only. */
public class MMAIPlayerIntegrationTest extends TestCase {

    private static final String BINARY = "dsg_src/mmai/mmai_player";
    private static final String DATA = "dsg_src/conf/marksAI";

    private final List<MMAIPlayer> players = new ArrayList<MMAIPlayer>();

    public MMAIPlayerIntegrationTest(String name) {
        super(name);
    }

    protected void setUp() {
        assertTrue("missing " + BINARY + " — run ./dsg_src/mmai/build.sh first",
            new File(BINARY).canExecute());
    }

    protected void tearDown() {
        for (MMAIPlayer p : players) {
            p.destroy();
        }
        players.clear();
    }

    private MMAIPlayer newPlayer(int game) {
        return newPlayer(game, BINARY, "60");
    }

    private MMAIPlayer newPlayer(int game, String binary, String timeoutSecs) {
        MMAIPlayer p = new MMAIPlayer();
        p.setGame(game);
        p.setLevel(1);
        p.setSeat(1);
        p.setOption("binaryPath", binary);
        p.setOption("dataDirectory", DATA);
        p.setOption("moveTimeoutSeconds", timeoutSecs);
        p.init();
        players.add(p);
        return p;
    }

    private void assertLegal(int move, List<Integer> played) {
        assertTrue("move in 0-360, was " + move, move >= 0 && move <= 360);
        // junit-3.7 has no assertFalse
        assertTrue("move must be an empty intersection: " + move,
            !played.contains(Integer.valueOf(move)));
    }

    /** Feed a scripted opening, ask for one move, assert it is legal. */
    private int playAndAssert(MMAIPlayer p, int[] opening) throws Exception {
        List<Integer> played = new ArrayList<Integer>();
        for (int m : opening) {
            p.addMove(m);
            played.add(m);
        }
        int move = p.getMove();
        assertLegal(move, played);
        return move;
    }

    // Six canonical game ids (spec §8.2). Openings: 180 = center (K10),
    // 182 an ordinary reply; the AI then plays the 3rd stone. Connect6 uses
    // 180 / {181,182}: P1 one stone, P2 a full two-stone turn, AI is P1.

    public void testPenteLegalReply() throws Exception {
        playAndAssert(newPlayer(1), new int[] {180, 182});
    }

    public void testKeryoLegalReply() throws Exception {
        playAndAssert(newPlayer(3), new int[] {180, 182});
    }

    public void testPoofLegalReply() throws Exception {
        playAndAssert(newPlayer(11), new int[] {180, 182});
    }

    public void testConnect6LegalReply() throws Exception {
        playAndAssert(newPlayer(13), new int[] {180, 181, 182});
    }

    public void testBoatLegalReply() throws Exception {
        playAndAssert(newPlayer(15), new int[] {180, 182});
    }

    public void testOPenteLegalReply() throws Exception {
        playAndAssert(newPlayer(25), new int[] {180, 182});
    }

    // --- Connect6 two-stone bridge (spec §6.3 / §8.2) ---

    public void testConnect6FirstTurnSingleStoneSentinel() throws Exception {
        MMAIPlayer p = newPlayer(13);
        int m1 = p.getMove(); // empty board: engine replies m1*362 + 361
        assertLegal(m1, new ArrayList<Integer>());
        p.addMove(m1); // controller echo
        // opponent's full two-stone turn on distinct empty points
        int o1 = (m1 + 1) % 361;
        int o2 = (m1 + 2) % 361;
        p.addMove(o1);
        p.addMove(o2);
        int before = p.getSidecarRequestCount();
        int next = p.getMove();
        assertEquals("sentinel must NOT cache a second stone -> round-trip",
            before + 1, p.getSidecarRequestCount());
        assertLegal(next, Arrays.asList(m1, o1, o2));
    }

    public void testConnect6SecondStoneServedFromCache() throws Exception {
        MMAIPlayer p = newPlayer(13);
        List<Integer> played =
            new ArrayList<Integer>(Arrays.asList(180, 181, 182));
        for (int m : played) {
            p.addMove(m);
        }
        int m1 = p.getMove(); // AI's two-stone turn, first stone
        assertLegal(m1, played);
        p.addMove(m1); // controller echoes the AI's own move
        played.add(m1);
        int before = p.getSidecarRequestCount();
        int m2 = p.getMove(); // second stone: cache only
        assertEquals("second stone must not consult the sidecar",
            before, p.getSidecarRequestCount());
        assertLegal(m2, played);
    }

    // --- Failure semantics (spec §6.4 / §8.2) ---

    public void testProcessDeathRecovery() throws Exception {
        MMAIPlayer p = newPlayer(1);
        p.addMove(180);
        p.addMove(182);
        int first = p.getMove();
        p.addMove(first);
        p.addMove(200 == first ? 201 : 200); // opponent reply on empty point
        // kill the sidecar out from under the player
        Runtime.getRuntime()
            .exec(new String[] {"pkill", "-f", "dsg_src/mmai/mmai_player"})
            .waitFor();
        Thread.sleep(300);
        // next getMove() must respawn and answer from the full-list replay
        int second = p.getMove();
        assertTrue("respawned reply in range", second >= 0 && second <= 360);
    }

    public void testMalformedReplyThrowsRuntimeException() throws Exception {
        File stub = stubBinary("#!/bin/sh\nread line\necho GARBAGE\n");
        MMAIPlayer p = newPlayer(1, stub.getPath(), "60");
        p.addMove(180);
        try {
            p.getMove();
            fail("expected RuntimeException on garbage reply");
        } catch (RuntimeException expected) {
        }
    }

    public void testReplyTimeoutThrowsRuntimeException() throws Exception {
        File stub = stubBinary("#!/bin/sh\nsleep 3600\n");
        MMAIPlayer p = newPlayer(1, stub.getPath(), "2");
        p.addMove(180);
        long start = System.currentTimeMillis();
        try {
            p.getMove();
            fail("expected RuntimeException on reply timeout");
        } catch (RuntimeException expected) {
            long elapsed = System.currentTimeMillis() - start;
            assertTrue("timed out near the 2s guard, took " + elapsed + "ms",
                elapsed >= 2000 && elapsed < 30000);
        }
    }

    private File stubBinary(String script) throws Exception {
        File stub = File.createTempFile("mmai_stub", ".sh");
        stub.deleteOnExit();
        PrintWriter w = new PrintWriter(stub);
        w.print(script);
        w.close();
        stub.setExecutable(true);
        return stub;
    }
}
