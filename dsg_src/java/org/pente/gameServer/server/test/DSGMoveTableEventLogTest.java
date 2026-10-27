package org.pente.gameServer.server.test;

import junit.framework.TestCase;
import org.pente.gameServer.event.DSGMoveTableEvent;

public class DSGMoveTableEventLogTest extends TestCase {
    public DSGMoveTableEventLogTest(String name) { super(name); }

    public void testFormatMoveForLog() {
        assertEquals("A19", DSGMoveTableEvent.formatMoveForLog(0));
        assertEquals("K10", DSGMoveTableEvent.formatMoveForLog(180));
        assertEquals("#361", DSGMoveTableEvent.formatMoveForLog(361));
        assertEquals("#380", DSGMoveTableEvent.formatMoveForLog(380));
        assertEquals("#721", DSGMoveTableEvent.formatMoveForLog(721));
        assertEquals("#722", DSGMoveTableEvent.formatMoveForLog(722));
        assertEquals("#-1", DSGMoveTableEvent.formatMoveForLog(-1));
    }

    public void testToStringNeverThrowsForTombstoneCodes() {
        for (int m : new int[]{0, 180, 361, 362, 380, 721, 722}) {
            String s = new DSGMoveTableEvent("alice", 3, m).toString();
            assertTrue(s, s.startsWith("move " + DSGMoveTableEvent.formatMoveForLog(m) + " "));
        }
        String bulk = new DSGMoveTableEvent(3, new int[]{180, 200, 361 + 5, 722}).toString();
        assertTrue(bulk, bulk.startsWith("moves K10,"));
        assertTrue(bulk, bulk.contains("#366,#722 "));
    }
}
