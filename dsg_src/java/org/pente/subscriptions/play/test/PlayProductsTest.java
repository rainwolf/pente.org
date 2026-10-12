package org.pente.subscriptions.play.test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import junit.framework.TestCase;

import org.pente.gameServer.core.MySQLDSGPlayerStorer;
import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.SubscriberRow;
import org.pente.subscriptions.play.SubscriberRule;

/**
 * Product whitelist, level bits, reply strings and the paymentdate = expiry - 364d encoding
 * round-tripped through the getSubscriberData rule (spec 2026-10-11 §2, §4.5, §5).
 *
 * Run: ant test-play-billing
 */
public class PlayProductsTest extends TestCase {

    private static final long DAY = PlayProducts.DAY_MS;

    public PlayProductsTest(String name) {
        super(name);
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, day, hour, minute, 0);
        return c.getTimeInMillis();
    }

    public void testWhitelist() {
        assertTrue(PlayProducts.isSubscription("yearly_subscription"));
        assertTrue(!PlayProducts.isSubscription("month_pass"));
        assertTrue(PlayProducts.isOneTime("month_pass"));
        assertTrue(PlayProducts.isOneTime("vacation_10"));
        assertTrue(PlayProducts.isOneTime("vacation_30"));
        assertTrue(PlayProducts.isOneTime("vacation_60"));
        assertTrue(!PlayProducts.isOneTime("yearly_subscription"));
        assertTrue(!PlayProducts.isOneTime("vacation_20"));
        assertTrue(!PlayProducts.isOneTime(null));
        assertTrue(!PlayProducts.isSubscription(null));
    }

    public void testVacationDays() {
        assertEquals(10, PlayProducts.vacationDays("vacation_10"));
        assertEquals(30, PlayProducts.vacationDays("vacation_30"));
        assertEquals(60, PlayProducts.vacationDays("vacation_60"));
        assertEquals(0, PlayProducts.vacationDays("month_pass"));
        assertEquals(0, PlayProducts.vacationDays(null));
    }

    public void testLevels() {
        int yearly = MySQLDSGPlayerStorer.ONEYEAR | MySQLDSGPlayerStorer.UNLIMITEDTBGAMES
                | MySQLDSGPlayerStorer.NOADS | MySQLDSGPlayerStorer.DBACCESS;
        int month = MySQLDSGPlayerStorer.ONEMONTH | MySQLDSGPlayerStorer.UNLIMITEDTBGAMES
                | MySQLDSGPlayerStorer.NOADS | MySQLDSGPlayerStorer.DBACCESS;
        assertEquals(yearly, PlayProducts.YEARLY_LEVEL);
        assertEquals(month, PlayProducts.MONTH_LEVEL);
    }

    public void testReplies() {
        assertEquals("success", PlayClassification.NEW.getReply());
        assertEquals("success:renewal", PlayClassification.RENEWAL.getReply());
        assertEquals("success:known", PlayClassification.KNOWN.getReply());
        assertEquals("success:shared", PlayClassification.SHARED.getReply());
        assertEquals("success:refunded", PlayClassification.REFUNDED.getReply());
        assertEquals("invalid receipt", PlayClassification.INVALID.getReply());
        String retry = PlayClassification.RETRY_REPLY;
        assertTrue(!retry.contains("success"));
        assertTrue(!retry.contains("invalid receipt"));
        assertTrue(!PlayClassification.INACTIVE.getReply().contains("success"));
        assertTrue(!PlayClassification.INACTIVE.getReply().contains("invalid receipt"));
    }

    /** expiry - 364d, read back by the ONEYEAR rule, ends no earlier than Play's expiry and at most 2 days later */
    public void testYearlyEncodingRoundTrip() {
        long[] expiries = {
                at(2027, 10, 11, 9, 30),
                at(2027, 10, 11, 0, 0),
                at(2027, 10, 11, 23, 59),
                at(2028, 3, 1, 12, 0),
                at(2028, 2, 29, 12, 0),
                at(2027, 1, 1, 0, 30)
        };
        for (long expiry : expiries) {
            long paymentDate = expiry - PlayProducts.PAYMENTDATE_OFFSET_MS;
            long implied = SubscriberRule.impliedExpiryMs(PlayProducts.YEARLY_LEVEL, paymentDate).longValue();
            assertTrue("implied " + implied + " before expiry " + expiry, implied >= expiry);
            assertTrue("implied " + implied + " too late for expiry " + expiry, implied <= expiry + 2 * DAY);
            List<SubscriberRow> rows = new ArrayList<SubscriberRow>();
            rows.add(new SubscriberRow(1L, "GPA.1", PlayProducts.YEARLY_LEVEL, paymentDate));
            long now = expiry - 10 * DAY;
            assertEquals(implied, SubscriberRule.expirationMs(rows, now).longValue());
            assertEquals(PlayProducts.YEARLY_LEVEL, SubscriberRule.level(rows, now));
        }
    }

    public void testMonthRule() {
        long now = at(2026, 10, 11, 15, 0);
        List<SubscriberRow> rows = new ArrayList<SubscriberRow>();
        rows.add(new SubscriberRow(1L, "GPA.m1", PlayProducts.MONTH_LEVEL, now));
        assertEquals(at(2026, 11, 11, 0, 0), SubscriberRule.expirationMs(rows, now).longValue());
        rows.add(new SubscriberRow(1L, "GPA.m2", PlayProducts.MONTH_LEVEL, at(2026, 11, 11, 0, 0)));
        assertEquals(at(2026, 12, 12, 0, 0), SubscriberRule.expirationMs(rows, now).longValue());
        assertEquals(PlayProducts.MONTH_LEVEL, SubscriberRule.level(rows, now));
    }

    public void testLapsedAndLevelZeroRowsIgnored() {
        long now = at(2026, 10, 11, 15, 0);
        List<SubscriberRow> rows = new ArrayList<SubscriberRow>();
        rows.add(new SubscriberRow(1L, "GPA.old", PlayProducts.MONTH_LEVEL, now - 40 * DAY));
        rows.add(new SubscriberRow(1L, "GPA.vac", 0, now));
        assertNull(SubscriberRule.expirationMs(rows, now));
        assertEquals(0, SubscriberRule.level(rows, now));
        assertNull(SubscriberRule.impliedExpiryMs(0, now));
    }
}
