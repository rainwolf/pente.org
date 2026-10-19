package org.pente.gameServer.core.test;

import java.io.IOException;

import junit.framework.TestCase;

import org.pente.gameServer.core.ReservedNames;
import org.pente.gameServer.core.UsernameFilter;

/** The reserved and offensive name checks shared by /join and kid creation. */
public class ReservedNamesTest extends TestCase {

    public ReservedNamesTest(String name) {
        super(name);
    }

    private static UsernameFilter filter() throws IOException {
        UsernameFilter f = UsernameFilter.loadShipped();
        assertNotNull(f);
        return f;
    }

    public void testDweReserved() throws IOException {
        assertTrue(ReservedNames.isReserved("dweebo1", filter()));
    }

    public void testMmPrefixReserved() throws IOException {
        assertTrue(ReservedNames.isReserved("mmfoo", filter()));
    }

    public void testMmAiReserved() throws IOException {
        assertTrue(ReservedNames.isReserved("xmm_ai", filter()));
    }

    public void testAdminReserved() throws IOException {
        assertTrue(ReservedNames.isReserved("badadmin", filter()));
    }

    public void testGuestReserved() throws IOException {
        assertTrue(ReservedNames.isReserved("myguest", filter()));
    }

    public void testNormalNameAllowed() throws IOException {
        assertTrue(!ReservedNames.isReserved("kidone", filter()));
    }

    public void testNullFilterSkipsOffensiveCheck() throws IOException {
        UsernameFilter f = new UsernameFilter(java.util.Arrays.asList("badword"));
        assertTrue(ReservedNames.isReserved("badword", f));
        assertTrue(!ReservedNames.isReserved("badword", null));
    }
}
