package org.pente.gameServer.server.test;

import java.util.Collection;
import junit.framework.TestCase;
import org.pente.game.GridStateFactory;
import org.pente.gameServer.core.AIData;
import org.pente.gameServer.server.XMLAIConfigurator;

/** The shipped ai_config.xml registers the MMAI sidecar player. The @conf@
 *  token is left unsubstituted (it is only an option-value string here). */
public class MMAIConfigTest extends TestCase {

    public MMAIConfigTest(String name) {
        super(name);
    }

    public void testMmaiEntryIsRegistered() throws Throwable {
        Collection<AIData> all = new XMLAIConfigurator().getAIData("dsg_src/conf/ai_config.xml");
        AIData mmai = null;
        for (AIData d : all) {
            if ("mmai".equals(d.getName())) {
                mmai = d;
            }
        }
        assertNotNull("mmai entry missing", mmai);
        assertEquals("org.pente.gameServer.server.MMAIPlayer", mmai.getClassName());
        assertEquals(8, mmai.getNumLevels());
        int[] games = {GridStateFactory.PENTE, GridStateFactory.KERYO, GridStateFactory.POOF_PENTE,
                GridStateFactory.CONNECT6, GridStateFactory.BOAT_PENTE, GridStateFactory.OPENTE};
        for (int g : games) {
            assertTrue("game " + g, mmai.isValidForGame(g));
        }
        assertTrue(!mmai.isValidForGame(GridStateFactory.SPEED_PENTE));
        assertEquals("/usr/local/bin/mmai_player", mmai.getOption("binaryPath"));
        assertEquals("@conf@/marksAI", mmai.getOption("dataDirectory"));
        assertNull(mmai.getOption("moveTimeoutSeconds"));
    }
}
