package org.pente.subscriptions.play.test;

import java.util.Calendar;
import java.util.Date;

import junit.framework.TestCase;

import org.pente.turnBased.CacheTBStorer;
import org.pente.turnBased.MySQLTBGameStorer;
import org.pente.turnBased.TBVacation;

/**
 * CacheTBStorer.addExtraTBVacationDays normalises the record before adding (final review ENT-2): a player
 * with no tb_emergency_time row (lastPinched null, which storeTBVacation cannot write) or with a row from
 * an earlier year (which the next cache-hit getTBVacation would reset, wiping the purchased days) starts
 * from this year's allowance, with a lastPinched that is this year but does not block the next pinch.
 *
 * Run: ant test-play-billing
 */
public class TBVacationGrantTest extends TestCase {

    private static final int YEARLY_HOURS = MySQLTBGameStorer.FLOATINGVACATIONDAYS * 24;

    private long now;
    private long januaryFirst;

    public TBVacationGrantTest(String name) {
        super(name);
    }

    protected void setUp() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.OCTOBER, 11, 15, 0, 0);
        now = c.getTimeInMillis();
        c.clear();
        c.set(2026, Calendar.JANUARY, 1, 0, 0, 0);
        januaryFirst = c.getTimeInMillis();
    }

    private static TBVacation vacation(int hoursLeft, Long lastPinchedMs) {
        TBVacation v = new TBVacation();
        v.setHoursLeft(hoursLeft);
        v.setLastPinched(lastPinchedMs == null ? null : new Date(lastPinchedMs.longValue()));
        return v;
    }

    /** the guard in CacheTBStorer.newTimeout: a pinch within the last hour blocks the next one */
    private boolean blocksNextPinch(TBVacation v) {
        return v.getLastPinched() != null && new Date(now - 3600L * 1000).before(v.getLastPinched());
    }

    private static int year(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        return c.get(Calendar.YEAR);
    }

    public void testNeverPinchedPlayerGetsThisYearsRecord() {
        TBVacation v = vacation(YEARLY_HOURS, null);
        CacheTBStorer.normaliseForExtraDays(v, now);
        assertEquals(YEARLY_HOURS, v.getHoursLeft());
        assertNotNull(v.getLastPinched());
        assertEquals(januaryFirst, v.getLastPinched().getTime());
        assertTrue(!blocksNextPinch(v));
    }

    public void testEarlierYearRecordRestartsThisYear() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2025, Calendar.MARCH, 3, 9, 0, 0);
        TBVacation v = vacation(17, Long.valueOf(c.getTimeInMillis()));
        CacheTBStorer.normaliseForExtraDays(v, now);
        assertEquals(YEARLY_HOURS, v.getHoursLeft());
        assertEquals(2026, year(v.getLastPinched()));
        assertEquals(januaryFirst, v.getLastPinched().getTime());
        assertTrue(!blocksNextPinch(v));
    }

    public void testThisYearsRecordIsUntouched() {
        long pinched = now - 3 * 24 * 3600L * 1000;
        TBVacation v = vacation(55, Long.valueOf(pinched));
        CacheTBStorer.normaliseForExtraDays(v, now);
        assertEquals(55, v.getHoursLeft());
        assertEquals(pinched, v.getLastPinched().getTime());
    }

    public void testRecentPinchStillBlocksAfterNormalising() {
        long pinched = now - 10 * 60 * 1000L;
        TBVacation v = vacation(55, Long.valueOf(pinched));
        CacheTBStorer.normaliseForExtraDays(v, now);
        assertEquals(pinched, v.getLastPinched().getTime());
        assertTrue(blocksNextPinch(v));
    }
}
