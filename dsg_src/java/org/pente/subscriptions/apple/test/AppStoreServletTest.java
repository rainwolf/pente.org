package org.pente.subscriptions.apple.test;

import java.nio.charset.StandardCharsets;
import java.util.function.LongSupplier;

import org.pente.gameServer.client.web.SimpleLoginAccessController;
import org.pente.subscriptions.apple.AppStoreNotificationServlet;
import org.pente.subscriptions.apple.AppStorePurchaseServlet;
import org.pente.subscriptions.apple.AppleBilling;
import org.pente.subscriptions.apple.AppleHistoryReplay;
import org.pente.subscriptions.apple.AppleNotification;
import org.pente.subscriptions.apple.AppleNotificationHandler;
import org.pente.subscriptions.apple.AppleProducts;
import org.pente.subscriptions.apple.AppleTransaction;

/**
 * The servlets' request handling without a container (spec 2026-10-15 §4.4, §4.6): the POST reply lines, the
 * notification statuses, and which path needs a login.
 *
 * Run: ant test-apple-billing
 */
public class AppStoreServletTest extends AppleServiceTestCase {

    public AppStoreServletTest(String name) {
        super(name);
    }

    private AppleBilling billing() {
        LongSupplier clock = new LongSupplier() {
            public long getAsLong() {
                return now;
            }
        };
        AppleNotificationHandler handler = new AppleNotificationHandler(service, verifier, store, clock);
        return new AppleBilling(service, handler, new AppleHistoryReplay(api, verifier, store, handler, clock));
    }

    public void testPostReplies() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        verifier.transactions.put("jws", t);
        api.transactions.put(t.transactionId, t);
        assertEquals("retry later", AppStorePurchaseServlet.reply(null, "jws", PID));
        assertEquals("invalid receipt", AppStorePurchaseServlet.reply(service, null, PID));
        assertEquals("invalid receipt", AppStorePurchaseServlet.reply(service, " ", PID));
        StringBuilder huge = new StringBuilder();
        while (huge.length() <= 64 * 1024) {
            huge.append("xxxxxxxxxx");
        }
        assertEquals("invalid receipt", AppStorePurchaseServlet.reply(service, huge.toString(), PID));
        api.failTransactionInfo = 1;
        assertEquals("retry later", AppStorePurchaseServlet.reply(service, "jws", PID));
        store.failUpserts = 1;
        assertEquals("retry later", AppStorePurchaseServlet.reply(service, "jws", PID));
        assertEquals("success", AppStorePurchaseServlet.reply(service, " jws\n", PID));
    }

    public void testNotificationStatuses() {
        AppleBilling billing = billing();
        assertEquals(500, AppStoreNotificationServlet.status(null, "{}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(401, AppStoreNotificationServlet.status(billing, null));
        assertEquals(401, AppStoreNotificationServlet.status(billing, "garbage".getBytes(StandardCharsets.UTF_8)));
        AppleNotification n = new AppleNotification();
        n.notificationUuid = "u1";
        n.type = "TEST";
        verifier.notifications.put("payload-u1", n);
        assertEquals(200, AppStoreNotificationServlet.status(billing,
                "{\"signedPayload\":\"payload-u1\"}".getBytes(StandardCharsets.UTF_8)));
    }

    public void testOnlyThePurchasePathNeedsALogin() {
        SimpleLoginAccessController access = new SimpleLoginAccessController();
        assertTrue(access.requiresLogin("/gameServer/appStorePurchase"));
        assertTrue(!access.requiresLogin("/gameServer/appStoreNotifications"));
        assertTrue("unchanged for Play", access.requiresLogin("/gameServer/googlePlayPurchase"));
    }
}
