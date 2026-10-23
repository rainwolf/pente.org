package org.pente.gameServer.core.test;

import junit.framework.TestCase;

import org.pente.gameServer.core.AgeBand;
import org.pente.gameServer.core.SimpleDSGPlayerData;

public class AgeBandTest extends TestCase {

    public AgeBandTest(String name) {
        super(name);
    }

    public void testValidBands() {
        assertEquals("UNDER_13", AgeBand.parse("UNDER_13"));
        assertEquals("13_15", AgeBand.parse("13_15"));
        assertEquals("16_17", AgeBand.parse("16_17"));
        assertEquals("18_PLUS", AgeBand.parse("18_PLUS"));
    }

    public void testCaseInsensitiveAndTrimmed() {
        assertEquals("UNDER_13", AgeBand.parse("under_13"));
        assertEquals("18_PLUS", AgeBand.parse(" 18_plus "));
    }

    public void testInvalid() {
        assertNull(AgeBand.parse(null));
        assertNull(AgeBand.parse(""));
        assertNull(AgeBand.parse("adult"));
        assertNull(AgeBand.parse("18+"));
        assertNull(AgeBand.parse("13-15"));
        assertNull(AgeBand.parse("UNDER_13; DROP TABLE"));
    }

    public void testIsAdult() {
        assertTrue(AgeBand.isAdult("18_PLUS"));
        assertTrue(!(AgeBand.isAdult("16_17")));
        assertTrue(!(AgeBand.isAdult("UNDER_13")));
        assertTrue(!(AgeBand.isAdult(null)));
    }

    public void testPlayerDataCarriesBand() {
        SimpleDSGPlayerData p = new SimpleDSGPlayerData();
        assertNull(p.getMobileAgeBand());
        p.setMobileAgeBand("13_15");
        assertEquals("13_15", p.getMobileAgeBand());
    }
}
