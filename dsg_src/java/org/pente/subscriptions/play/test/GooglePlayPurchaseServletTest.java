package org.pente.subscriptions.play.test;

import junit.framework.TestCase;

import org.pente.subscriptions.play.GooglePlayPurchaseServlet;

/**
 * Who may use /gameServer/googlePlayPurchase: only a logged-in, non-guest player
 * (LoginFilter's "name" attribute; "guest" is what crawlers get). Spec 2026-10-11 §4.4.
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
}
