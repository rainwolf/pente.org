package org.pente.subscriptions.play.test;

import junit.framework.TestCase;

import org.pente.subscriptions.play.GooglePlayPurchaseServlet;
import org.pente.subscriptions.play.PlayProducts;

/**
 * Who may use /gameServer/googlePlayPurchase: only a logged-in, non-guest player
 * (LoginFilter's "name" attribute; "guest" is what crawlers get). Spec 2026-10-11 §4.4.
 * The billing log names a client's product id only when it is on the whitelist.
 *
 * Run: ant test-play-billing
 */
public class GooglePlayPurchaseServletTest extends TestCase {

    public GooglePlayPurchaseServletTest(String name) {
        super(name);
    }

    public void testGuestAndAnonymousRejected() {
        assertTrue(!GooglePlayPurchaseServlet.acceptsPlayerName(null));
        assertTrue(!GooglePlayPurchaseServlet.acceptsPlayerName(""));
        assertTrue(!GooglePlayPurchaseServlet.acceptsPlayerName("  "));
        assertTrue(!GooglePlayPurchaseServlet.acceptsPlayerName("guest"));
        assertTrue(!GooglePlayPurchaseServlet.acceptsPlayerName("Guest"));
        assertTrue(!GooglePlayPurchaseServlet.acceptsPlayerName(" guest "));
    }

    public void testRealPlayersAccepted() {
        assertTrue(GooglePlayPurchaseServlet.acceptsPlayerName("rainwolf"));
        assertTrue(GooglePlayPurchaseServlet.acceptsPlayerName("guestwho"));
    }

    public void testOnlyWhitelistedProductIdsAreLogged() {
        assertEquals(PlayProducts.YEARLY, GooglePlayPurchaseServlet.loggableProductId(PlayProducts.YEARLY));
        assertEquals(PlayProducts.MONTH_PASS, GooglePlayPurchaseServlet.loggableProductId(PlayProducts.MONTH_PASS));
        assertEquals(PlayProducts.VACATION_10, GooglePlayPurchaseServlet.loggableProductId(PlayProducts.VACATION_10));
        assertEquals(PlayProducts.VACATION_30, GooglePlayPurchaseServlet.loggableProductId(PlayProducts.VACATION_30));
        assertEquals(PlayProducts.VACATION_60, GooglePlayPurchaseServlet.loggableProductId(PlayProducts.VACATION_60));
        String unknown = GooglePlayPurchaseServlet.UNKNOWN_PRODUCT;
        assertEquals("<unknown product>", unknown);
        assertEquals(unknown, GooglePlayPurchaseServlet.loggableProductId("gold_pass"));
        assertEquals(unknown, GooglePlayPurchaseServlet.loggableProductId("month_pass\r\nINFO forged line"));
        assertEquals(unknown, GooglePlayPurchaseServlet.loggableProductId(null));
    }
}
