package org.pente.subscriptions.test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import junit.framework.TestCase;

import org.json.JSONObject;
import org.pente.subscriptions.IOSReceiptServlet;
import org.pente.subscriptions.IOSReceiptServlet.ReceiptClass;

import static org.pente.subscriptions.IOSReceiptServlet.ChainOwnership.MINE;
import static org.pente.subscriptions.IOSReceiptServlet.ChainOwnership.MINE_AND_OTHERS;
import static org.pente.subscriptions.IOSReceiptServlet.ChainOwnership.OTHERS;
import static org.pente.subscriptions.IOSReceiptServlet.ChainOwnership.UNOWNED;

/**
 * Classification of a validated iOS receipt POST (IOSReceiptServlet.classify),
 * subscription chain ownership (chainOwnership, chainTransactionIds)
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
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(UNOWNED, true, true, null, RECEIPT_PAYMENT, NOW, "1000", "1000"));
    }

    public void testRenewalOfCurrentIosSubscriberIsRenewal() {
        assertEquals(ReceiptClass.RENEWAL, IOSReceiptServlet.classify(MINE, true, true, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testRenewalJustAfterExpiryWithinGraceIsRenewal() {
        Long expiredFiveDaysAgo = paymentDateExpiringAt(NOW - 5 * DAY);
        assertEquals(ReceiptClass.RENEWAL, IOSReceiptServlet.classify(MINE, true, true, expiredFiveDaysAgo, RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testGraceBoundary() {
        Long atGraceEdge = paymentDateExpiringAt(NOW - IOSReceiptServlet.RENEWAL_GRACE_MS);
        Long pastGraceEdge = paymentDateExpiringAt(NOW - IOSReceiptServlet.RENEWAL_GRACE_MS - 1);
        assertEquals(ReceiptClass.RENEWAL, IOSReceiptServlet.classify(MINE, true, true, atGraceEdge, RECEIPT_PAYMENT, NOW, "1001", "1000"));
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(MINE, true, true, pastGraceEdge, RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testLapsedIosRowResubscribeIsNew() {
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(MINE, true, true, LAPSED_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
        Long lapsed31Days = paymentDateExpiringAt(NOW - 31 * DAY);
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(MINE, true, true, lapsed31Days, RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testResubscribeWithoutIosRowIsNew() {
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(MINE, true, true, null, RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testNewOriginalTransactionWithCurrentIosRowIsNew() {
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(MINE, true, true, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "2000", "2000"));
    }

    public void testMissingOriginalTransactionIsNew() {
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(MINE, true, true, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "1001", null));
    }

    public void testDuplicateForSamePlayerIsKnown() {
        assertEquals(ReceiptClass.KNOWN, IOSReceiptServlet.classify(MINE, false, true, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
        assertEquals(ReceiptClass.KNOWN, IOSReceiptServlet.classify(MINE, false, true, LAPSED_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
        assertEquals(ReceiptClass.KNOWN, IOSReceiptServlet.classify(MINE, false, true, null, RECEIPT_PAYMENT, NOW, "1000", "1000"));
    }

    public void testDuplicateForOtherPlayerIsShared() {
        assertEquals(ReceiptClass.SHARED, IOSReceiptServlet.classify(MINE_AND_OTHERS, false, false, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
        assertEquals(ReceiptClass.SHARED, IOSReceiptServlet.classify(MINE_AND_OTHERS, false, false, null, RECEIPT_PAYMENT, NOW, "1000", "1000"));
    }

    /**
     * A lapsed returner's earlier request upserted the ios row (paymentdate = this receipt's startMs)
     * then failed before the gate INSERT; the retry must not mistake that row for a previous subscription.
     */
    public void testOwnIosRowFromFailedOrConcurrentRequestIsNew() {
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(MINE, true, true, Long.valueOf(RECEIPT_PAYMENT),
                RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testIosRowLaterThanReceiptIsNew() {
        assertEquals(ReceiptClass.NEW, IOSReceiptServlet.classify(MINE, true, true, Long.valueOf(RECEIPT_PAYMENT + DAY),
                RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testOlderIosRowWithinGraceIsStillRenewal() {
        Long expiredFiveDaysAgo = paymentDateExpiringAt(NOW - 5 * DAY);
        assertTrue(expiredFiveDaysAgo.longValue() < RECEIPT_PAYMENT);
        assertEquals(ReceiptClass.RENEWAL, IOSReceiptServlet.classify(MINE, true, true, expiredFiveDaysAgo,
                RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    /**
     * Shared Apple ID: player B posts a receipt whose chain is registered only to player A.
     * Nothing is written (inserted false), whatever the stored ios row looks like.
     */
    public void testChainOwnedByOthersIsShared() {
        assertEquals(ReceiptClass.SHARED, IOSReceiptServlet.classify(OTHERS, false, true, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
        assertEquals(ReceiptClass.SHARED, IOSReceiptServlet.classify(OTHERS, false, true, null, RECEIPT_PAYMENT, NOW, "1000", "1000"));
        // even if the flags would otherwise say NEW/RENEWAL
        assertEquals(ReceiptClass.SHARED, IOSReceiptServlet.classify(OTHERS, true, true, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
        assertEquals(ReceiptClass.SHARED, IOSReceiptServlet.classify(OTHERS, true, true, null, RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    /** the payer's renewal of their own chain is still a renewal, even if another player posted first before */
    public void testOwnChainRenewalIsRenewal() {
        assertEquals(ReceiptClass.RENEWAL, IOSReceiptServlet.classify(MINE_AND_OTHERS, true, true, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
        assertEquals(ReceiptClass.KNOWN, IOSReceiptServlet.classify(MINE_AND_OTHERS, false, true, CURRENT_ROW, RECEIPT_PAYMENT, NOW, "1001", "1000"));
    }

    public void testChainOwnership() {
        assertEquals(UNOWNED, IOSReceiptServlet.chainOwnership(5L, Collections.<Long>emptyList()));
        assertEquals(MINE, IOSReceiptServlet.chainOwnership(5L, Arrays.asList(5L)));
        assertEquals(MINE, IOSReceiptServlet.chainOwnership(5L, Arrays.asList(5L, 5L)));
        assertEquals(OTHERS, IOSReceiptServlet.chainOwnership(5L, Arrays.asList(7L)));
        assertEquals(OTHERS, IOSReceiptServlet.chainOwnership(5L, Arrays.asList(7L, 8L)));
        assertEquals(MINE_AND_OTHERS, IOSReceiptServlet.chainOwnership(5L, Arrays.asList(7L, 5L)));
        assertEquals(MINE_AND_OTHERS, IOSReceiptServlet.chainOwnership(5L, Arrays.asList(5L, 7L)));
    }

    public void testChainTransactionIds() {
        JSONObject json = new JSONObject(
                "{\"status\":0," +
                "\"receipt\":{\"in_app\":[" +
                "{\"product_id\":\"1YRNOADSORLIMITS\",\"transaction_id\":\"1000\",\"original_transaction_id\":\"1000\",\"purchase_date_ms\":\"1\"}," +
                "{\"product_id\":\"OTHER\",\"transaction_id\":\"9000\",\"original_transaction_id\":\"9000\"}]}," +
                "\"latest_receipt_info\":[" +
                "{\"product_id\":\"1YRNOADSORLIMITS\",\"transaction_id\":\"1001\",\"original_transaction_id\":\"1000\",\"expires_date_ms\":\"2\"}," +
                "{\"product_id\":\"1YRNOADSORLIMITS\",\"transaction_id\":\"1002\",\"original_transaction_id\":\"1000\",\"expires_date_ms\":\"3\"}]}");
        Set<String> ids = IOSReceiptServlet.chainTransactionIds(json);
        assertEquals(3, ids.size());
        assertTrue(ids.contains("1000"));
        assertTrue(ids.contains("1001"));
        assertTrue(ids.contains("1002"));
        assertTrue(!ids.contains("9000"));
    }

    public void testChainTransactionIdsFromExpiredInfoAndMissingFields() {
        JSONObject json = new JSONObject(
                "{\"status\":0," +
                "\"receipt\":{\"in_app\":[]}," +
                "\"latest_expired_receipt_info\":[" +
                "{\"product_id\":\"1YRNOADSORLIMITS\",\"transaction_id\":\"2001\"}]}");
        Set<String> ids = IOSReceiptServlet.chainTransactionIds(json);
        assertEquals(1, ids.size());
        assertTrue(ids.contains("2001"));

        assertTrue(IOSReceiptServlet.chainTransactionIds(new JSONObject("{\"status\":0}")).isEmpty());
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
