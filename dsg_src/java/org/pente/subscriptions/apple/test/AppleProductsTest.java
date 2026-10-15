package org.pente.subscriptions.apple.test;

import junit.framework.TestCase;

import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleProducts;
import org.pente.subscriptions.apple.AppleStates;
import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayProducts;

/**
 * The product whitelist, the status-code mapping and the reply strings (spec 2026-10-15 §3, §4.4, §4.5).
 *
 * Run: ant test-apple-billing
 */
public class AppleProductsTest extends TestCase {

    public AppleProductsTest(String name) {
        super(name);
    }

    public void testWhitelist() {
        assertTrue(AppleProducts.isSubscription("1YRNOADSORLIMITS"));
        assertTrue(!AppleProducts.isSubscription(PlayProducts.YEARLY));
        assertTrue(AppleProducts.isOneTime("month_pass"));
        assertTrue(AppleProducts.isOneTime("vacation_10"));
        assertTrue(AppleProducts.isOneTime("vacation_30"));
        assertTrue(AppleProducts.isOneTime("vacation_60"));
        assertTrue(!AppleProducts.isKnown("vacation_20"));
        assertTrue(!AppleProducts.isKnown(null));
        assertTrue(!AppleProducts.isKnown("1yrnoadsorlimits"));
        assertEquals(5, AppleProducts.ON_SALE.size());
        assertEquals("1YRNOADSORLIMITS", AppleProducts.ON_SALE.get(0));
    }

    public void testGrants() {
        assertEquals(PlayProducts.YEARLY_LEVEL, AppleProducts.level(AppleProducts.YEARLY));
        assertEquals(PlayProducts.MONTH_LEVEL, AppleProducts.level(AppleProducts.MONTH_PASS));
        assertEquals(0, AppleProducts.level(AppleProducts.VACATION_30));
        assertEquals(10, AppleProducts.vacationDays("vacation_10"));
        assertEquals(30, AppleProducts.vacationDays("vacation_30"));
        assertEquals(60, AppleProducts.vacationDays("vacation_60"));
        assertEquals(0, AppleProducts.vacationDays(AppleProducts.YEARLY));
    }

    public void testStatusCodes() {
        assertEquals(AppleStates.ACTIVE, AppleStates.fromStatus(1));
        assertEquals(AppleStates.EXPIRED, AppleStates.fromStatus(2));
        assertEquals(AppleStates.BILLING_RETRY, AppleStates.fromStatus(3));
        assertEquals(AppleStates.GRACE, AppleStates.fromStatus(4));
        assertEquals(AppleStates.REVOKED, AppleStates.fromStatus(5));
        assertNull(AppleStates.fromStatus(0));
        assertNull(AppleStates.fromStatus(6));
        assertTrue(AppleStates.isLive(AppleStates.BILLING_RETRY));
        assertTrue(!AppleStates.isLive(AppleStates.EXPIRED));
        assertTrue(AppleStates.isRevoked(AppleStates.REFUNDED));
        assertTrue(!AppleStates.isRevoked(AppleStates.CONSUMED));
    }

    public void testEnvironments() {
        assertEquals(AppleEnvironment.SANDBOX, AppleEnvironment.of("SANDBOX"));
        assertEquals(AppleEnvironment.PRODUCTION, AppleEnvironment.of("PRODUCTION"));
        assertEquals(AppleEnvironment.PRODUCTION, AppleEnvironment.of(null));
        assertEquals(180 * PlayProducts.DAY_MS, AppleEnvironment.PRODUCTION.historyRetentionMs());
        assertEquals(30 * PlayProducts.DAY_MS, AppleEnvironment.SANDBOX.historyRetentionMs());
    }

    public void testRepliesMatchPlayExactly() {
        assertEquals("success", PlayClassification.NEW.getReply());
        assertEquals("success:renewal", PlayClassification.RENEWAL.getReply());
        assertEquals("success:known", PlayClassification.KNOWN.getReply());
        assertEquals("success:shared", PlayClassification.SHARED.getReply());
        assertEquals("invalid receipt", PlayClassification.INVALID.getReply());
        assertEquals("inactive", PlayClassification.INACTIVE.getReply());
        assertEquals("retry later", PlayClassification.RETRY_REPLY);
    }
}
