package org.pente.gameServer.server.test;

import java.io.File;
import junit.framework.TestCase;
import org.pente.gameServer.server.MMAIPlayer;

/** init() fail-fast behavior (spec §6.2 / §8.2): missing binary, missing data
 *  files. Uses /bin/sh as a stand-in "executable binary" — init() only checks
 *  executability and spawns; it does not talk to the process. */
public class MMAIPlayerInitTest extends TestCase {

    public MMAIPlayerInitTest(String name) {
        super(name);
    }

    private MMAIPlayer player(String binaryPath, String dataDirectory) {
        MMAIPlayer p = new MMAIPlayer();
        p.setGame(1);
        p.setLevel(1);
        p.setSeat(1);
        p.setOption("binaryPath", binaryPath);
        p.setOption("dataDirectory", dataDirectory);
        return p;
    }

    public void testInitFailsFastOnMissingBinary() {
        MMAIPlayer p = player("/nonexistent/mmai_player", "dsg_src/conf/marksAI");
        try {
            p.init();
            fail("expected RuntimeException for missing binary");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("binaryPath"));
        }
    }

    public void testInitFailsFastOnMissingDataFiles() throws Exception {
        File emptyDir = File.createTempFile("mmai_data", "");
        emptyDir.delete();
        emptyDir.mkdir();
        emptyDir.deleteOnExit();
        MMAIPlayer p = player("/bin/sh", emptyDir.getPath());
        try {
            p.init();
            fail("expected RuntimeException for missing data files");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("pente.tbl"));
        }
    }

    public void testInitFailsFastOnUnsetOptions() {
        MMAIPlayer p = new MMAIPlayer();
        try {
            p.init();
            fail("expected RuntimeException for unset binaryPath");
        } catch (RuntimeException expected) {
        }
    }

    public void testInitSpawnsWithValidConfig() {
        // /bin/sh <dataDir> exits immediately, but init() itself must succeed.
        MMAIPlayer p = player("/bin/sh", "dsg_src/conf/marksAI");
        p.init();
        p.destroy(); // must not throw on an already-dead process
    }

    public void testInitIdempotentOnLiveProcess() {
        // AIPlayerFactory.getAIPlayerThreaded calls init() twice; a second
        // init() on a still-live sidecar must NOT respawn (else one leaked
        // process per game). /usr/bin/yes stays alive (unlike /bin/sh), so
        // exactly one spawn must happen across two init() calls.
        if (!new File("/usr/bin/yes").canExecute()) {
            return; // platform without /usr/bin/yes; skip
        }
        MMAIPlayer p = player("/usr/bin/yes", "dsg_src/conf/marksAI");
        try {
            p.init();
            p.init();
            assertEquals(1, p.getSpawnCount());
        } finally {
            p.destroy();
        }
    }

    public void testValidateRejectsUnsupportedGame() {
        MMAIPlayer p = player("/bin/sh", "dsg_src/conf/marksAI");
        p.setGame(5); // Gomoku: not one of the six supported variants
        try {
            p.init();
            fail("expected RuntimeException for unsupported game id");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("unsupported game"));
        }
    }

    public void testValidateRejectsOutOfRangeLevel() {
        MMAIPlayer high = player("/bin/sh", "dsg_src/conf/marksAI");
        high.setLevel(9);
        try {
            high.init();
            fail("expected RuntimeException for level 9");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("level"));
        }
        MMAIPlayer low = player("/bin/sh", "dsg_src/conf/marksAI");
        low.setLevel(0);
        try {
            low.init();
            fail("expected RuntimeException for level 0");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("level"));
        }
    }

    public void testConfigDirectoryAlias() {
        // "configDirectory" is MarksAIPlayer's spelling; it must be accepted as
        // an alias for dataDirectory so init() does not fail "dataDirectory
        // not set".
        MMAIPlayer p = new MMAIPlayer();
        p.setGame(1);
        p.setLevel(1);
        p.setSeat(1);
        p.setOption("binaryPath", "/bin/sh");
        p.setOption("configDirectory", "dsg_src/conf/marksAI");
        p.init();
        p.destroy();
    }
}
