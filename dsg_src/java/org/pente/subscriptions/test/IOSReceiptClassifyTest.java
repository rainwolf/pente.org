package org.pente.subscriptions.test;

import junit.framework.TestCase;

import org.pente.subscriptions.IOSReceiptServlet;
import org.pente.subscriptions.IOSReceiptServlet.ReceiptClass;

/**
 * Classification of a validated iOS receipt POST (IOSReceiptServlet.classify)
 * and the reply contract shipped clients rely on.
 *
 * Run: ant test-one -Dtest=org.pente.subscriptions.test.IOSReceiptClassifyTest
 */
public class IOSReceiptClassifyTest extends TestCase {

    public IOSReceiptClassifyTest(String name) {
        super(name);
    }

    private static final long NOW = 1790000000000L; // fixed clock, 2026-09
    private static final long DAY = 24L * 3600 * 1000;

    /** paymentdate of an ios row whose subscription expires at expiryMs */
    private static Long paymentDateExpiringAt(long expiryMs) {
        return Long.valueOf(expiryMs - IOSReceiptServlet.PAYMENTDATE_OFFSET_MS);
    }

    private static final Long CURRENT_ROW = paymentDateExpiringAt(NOW + 10 * DAY);
    private static final Long LAPSED_ROW = paymentDateExpiringAt(NOW - 3 * 365 * DAY);
    /** paymentdate this receipt records (ReceiptInfo.startMs); a fresh one-year receipt starts about now */
    private static final long RECEIPT_PAYMENT = NOW;

    public void testGraceIsThirtyDays() {
        assertEquals(30L * DAY, IOSReceiptServlet.RENEWAL_GRACE_MS);
    }

    public void testFirstPurchaseIsNew() {
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(true, true, null, RECEIPT_PAYMENT, NOW, "1000", "1000"));
    }

    public void testRenewalOfCurrentIosSubscriberIsRenewal() {
        assertEquals(ReceiptClass.RENEWAL, IOSReceiptServlet.classify(true, true, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testRenewalJustAfterExpiryWithinGraceIsRenewal() {
        Long expiredFiveDaysAgo = paymentDateExpiringAt(NOW - 5 * DAY);
        assertEquals(ReceiptClass.RENEWAL, IOSReceiptServlet.classify(true, true, expiredFiveDaysAgo, RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testGraceBoundary() {
        Long atGraceEdge = paymentDateExpiringAt(NOW - IOSReceiptServlet.RENEWAL_GRACE_MS);
        Long pastGraceEdge = paymentDateExpiringAt(NOW - IOSReceiptServlet.RENEWAL_GRACE_MS - 1);
        assertEquals(ReceiptClass.RENEWAL, IOSReceiptServlet.classify(true, true, atGraceEdge, RECEIPT_PAYMENT, NOW, "1001", "1000"));
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(true, true, pastGraceEdge, RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testLapsedIosRowResubscribeIsNew() {
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(true, true, LAPSED_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
        Long lapsed31Days = paymentDateExpiringAt(NOW - 31 * DAY);
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(true, true, lapsed31Days, RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testResubscribeWithoutIosRowIsNew() {
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(true, true, null, RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testNewOriginalTransactionWithCurrentIosRowIsNew() {
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(true, true, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "2000", "2000"));
    }

    public void testMissingOriginalTransactionIsNew() {
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(true, true, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "1001", null));
    }

    public void testDuplicateForSamePlayerIsKnown() {
        assertEquals(ReceiptClass.KNOWN, IOSReceiptServlet.classify(false, true, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
        assertEquals(ReceiptClass.KNOWN, IOSReceiptServlet.classify(false, true, LAPSED_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
        assertEquals(ReceiptClass.KNOWN, IOSReceiptServlet.classify(false, true, null, RECEIPT_PAYMENT, NOW, "1000", "1000"));
    }

    public void testDuplicateForOtherPlayerIsShared() {
        assertEquals(ReceiptClass.SHARED, IOSReceiptServlet.classify(false, false, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
        assertEquals(ReceiptClass.SHARED, IOSReceiptServlet.classify(false, false, null, RECEIPT_PAYMENT, NOW, "1000", "1000"));
    }

    /**
     * A lapsed returner's earlier request upserted the ios row (paymentdate = this receipt's startMs)
     * then failed before the gate INSERT; the retry must not mistake that row for a previous subscription.
     */
    public void testOwnIosRowFromFailedOrConcurrentRequestIsNew() {
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(true, true, Long.valueOf(RECEIPT_PAYMENT),
                RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testIosRowLaterThanReceiptIsNew() {
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(true, true, Long.valueOf(RECEIPT_PAYMENT + DAY),
                RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testOlderIosRowWithinGraceIsStillRenewal() {
        Long expiredFiveDaysAgo = paymentDateExpiringAt(NOW - 5 * DAY);
        assertTrue(expiredFiveDaysAgo.longValue() < RECEIPT_PAYMENT);
        assertEquals(ReceiptClass.RENEWAL, IOSReceiptServlet.classify(true, true, expiredFiveDaysAgo,
                RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testReplyContract() {
        assertEquals("success", ReceiptClass.NEW.getReply());
        assertEquals("success:renewal", ReceiptClass.RENEWAL.getReply());
        assertEquals("success:known", ReceiptClass.KNOWN.getReply());
        assertEquals("success:shared", ReceiptClass.SHARED.getReply());
        // old shipped clients only test containsString:@"success" / @"invalid receipt"
        ReceiptClass[] all = ReceiptClass.values();
        for (int i = 0; i < all.length; i++) {
            assertTrue(all[i].getReply().indexOf("success") >= 0);
            assertTrue(all[i].getReply().indexOf("invalid receipt") < 0);
        }
    }
}
