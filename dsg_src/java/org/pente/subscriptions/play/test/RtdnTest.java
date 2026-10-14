package org.pente.subscriptions.play.test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.pente.subscriptions.play.JwtSignatureVerifier;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStates;
import org.pente.subscriptions.play.RtdnAuth;
import org.pente.subscriptions.play.RtdnDispatcher;
import org.pente.subscriptions.play.RtdnNotification;
import org.pente.subscriptions.play.VoidedPage;

/**
 * RTDN: Pub/Sub envelope parsing, JWT claim rules behind the signature interface, and dispatch with
 * 200/500 replies (spec 2026-10-11 §4.6, §5).
 *
 * Run: ant test-play-billing
 */
public class RtdnTest extends PlayServiceTestCase {

    private static final String PACKAGE = "be.submanifold.pentelive";
    private static final String AUDIENCE = "https://www.pente.org/gameServer/googlePlayRTDN";
    private static final String PUSH_ACCOUNT = "play-rtdn-push@pente-live.iam.gserviceaccount.com";

    public RtdnTest(String name) {
        super(name);
    }

    private static String envelope(String developerNotificationJson) {
        String data = Base64.getEncoder().encodeToString(developerNotificationJson.getBytes(StandardCharsets.UTF_8));
        return "{\"message\":{\"data\":\"" + data + "\",\"messageId\":\"136969346945\"},"
                + "\"subscription\":\"projects/pente-live/subscriptions/play-rtdn\"}";
    }

    private static Map<String, Object> claims(Object aud, String email, Object verified) {
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put("aud", aud);
        claims.put("email", email);
        claims.put("email_verified", verified);
        claims.put("iss", "https://accounts.google.com");
        return claims;
    }

    private static JwtSignatureVerifier returning(final Map<String, Object> claims) {
        return new JwtSignatureVerifier() {
            public Map<String, Object> verify(String jwt) {
                return claims;
            }
        };
    }

    // ---------------------------------------------------------------- parsing

    public void testParseSubscriptionNotification() {
        RtdnNotification n = RtdnNotification.parse(envelope("{\"version\":\"1.0\",\"packageName\":\"" + PACKAGE
                + "\",\"eventTimeMillis\":\"1790000000000\",\"subscriptionNotification\":{\"version\":\"1.0\","
                + "\"notificationType\":4,\"purchaseToken\":\"tok-abc\"}}"));
        assertEquals(RtdnNotification.Kind.SUBSCRIPTION, n.kind);
        assertEquals(PACKAGE, n.packageName);
        assertEquals(4, n.notificationType);
        assertEquals("tok-abc", n.purchaseToken);
    }

    public void testParseOneTimeVoidedAndTest() {
        RtdnNotification p = RtdnNotification.parse(envelope("{\"packageName\":\"" + PACKAGE
                + "\",\"oneTimeProductNotification\":{\"notificationType\":1,\"purchaseToken\":\"t1\",\"sku\":\"month_pass\"}}"));
        assertEquals(RtdnNotification.Kind.ONE_TIME_PRODUCT, p.kind);
        assertEquals(RtdnNotification.ONE_TIME_PRODUCT_PURCHASED, p.notificationType);
        assertEquals("month_pass", p.sku);
        RtdnNotification v = RtdnNotification.parse(envelope("{\"packageName\":\"" + PACKAGE
                + "\",\"voidedPurchaseNotification\":{\"purchaseToken\":\"t2\",\"orderId\":\"GPA.9\",\"productType\":2}}"));
        assertEquals(RtdnNotification.Kind.VOIDED, v.kind);
        assertEquals("GPA.9", v.orderId);
        RtdnNotification t = RtdnNotification.parse(envelope("{\"packageName\":\"" + PACKAGE
                + "\",\"testNotification\":{\"version\":\"1.0\"}}"));
        assertEquals(RtdnNotification.Kind.TEST, t.kind);
    }

    public void testParseRejectsMalformedBodies() {
        assertNull(RtdnNotification.parse(null));
        assertNull(RtdnNotification.parse("not json"));
        assertNull(RtdnNotification.parse("{}"));
        assertNull(RtdnNotification.parse("{\"message\":{}}"));
        assertNull(RtdnNotification.parse("{\"message\":{\"data\":\"%%%\"}}"));
        assertNull(RtdnNotification.parse("{\"message\":{\"data\":\"" + Base64.getEncoder().encodeToString("nope".getBytes()) + "\"}}"));
    }

    // ---------------------------------------------------------------- claim rules

    public void testValidClaimsAccepted() {
        assertTrue(RtdnAuth.authorized("Bearer x.y.z", returning(claims(AUDIENCE, PUSH_ACCOUNT, Boolean.TRUE)), AUDIENCE, PUSH_ACCOUNT));
        assertTrue(RtdnAuth.claimsAccepted(claims(Arrays.asList("other", AUDIENCE), PUSH_ACCOUNT, "true"), AUDIENCE, PUSH_ACCOUNT));
    }

    public void testWrongAudienceEmailOrUnverifiedRejected() {
        assertTrue(!RtdnAuth.claimsAccepted(claims("https://evil.example/hook", PUSH_ACCOUNT, Boolean.TRUE), AUDIENCE, PUSH_ACCOUNT));
        assertTrue(!RtdnAuth.claimsAccepted(claims(AUDIENCE, "someone@gmail.com", Boolean.TRUE), AUDIENCE, PUSH_ACCOUNT));
        assertTrue(!RtdnAuth.claimsAccepted(claims(AUDIENCE, PUSH_ACCOUNT, Boolean.FALSE), AUDIENCE, PUSH_ACCOUNT));
        assertTrue(!RtdnAuth.claimsAccepted(claims(AUDIENCE, PUSH_ACCOUNT, null), AUDIENCE, PUSH_ACCOUNT));
        assertTrue(!RtdnAuth.claimsAccepted(null, AUDIENCE, PUSH_ACCOUNT));
    }

    public void testMissingHeaderBadSignatureOrConfigRejected() {
        JwtSignatureVerifier ok = returning(claims(AUDIENCE, PUSH_ACCOUNT, Boolean.TRUE));
        JwtSignatureVerifier badSignature = new JwtSignatureVerifier() {
            public Map<String, Object> verify(String jwt) throws Exception {
                throw new Exception("signature");
            }
        };
        assertTrue(!RtdnAuth.authorized(null, ok, AUDIENCE, PUSH_ACCOUNT));
        assertTrue(!RtdnAuth.authorized("Basic abc", ok, AUDIENCE, PUSH_ACCOUNT));
        assertTrue(!RtdnAuth.authorized("Bearer ", ok, AUDIENCE, PUSH_ACCOUNT));
        assertTrue(!RtdnAuth.authorized("Bearer x.y.z", badSignature, AUDIENCE, PUSH_ACCOUNT));
        assertTrue(!RtdnAuth.authorized("Bearer x.y.z", null, AUDIENCE, PUSH_ACCOUNT));
        assertTrue(!RtdnAuth.authorized("Bearer x.y.z", ok, "", PUSH_ACCOUNT));
        assertTrue(!RtdnAuth.authorized("Bearer x.y.z", ok, AUDIENCE, null));
    }

    // ---------------------------------------------------------------- dispatch

    public void testSubscriptionNotificationGrantsWithoutTheApp() {
        subscription("tok", PID, PlayStates.ACTIVE, "GPA.1", now + 365 * DAY);
        int status = new RtdnDispatcher(service, PACKAGE).dispatch(envelope("{\"packageName\":\"" + PACKAGE
                + "\",\"subscriptionNotification\":{\"notificationType\":4,\"purchaseToken\":\"tok\"}}"));
        assertEquals(200, status);
        assertNotNull(row("GPA.1"));
        assertEquals(1, api.acknowledged.size());
    }

    public void testOneTimePurchaseNotificationGrantsAndConsumes() {
        product("p", PID, PlayProducts.MONTH_PASS, "GPA.p");
        int status = new RtdnDispatcher(service, PACKAGE).dispatch(envelope("{\"packageName\":\"" + PACKAGE
                + "\",\"oneTimeProductNotification\":{\"notificationType\":1,\"purchaseToken\":\"p\",\"sku\":\"month_pass\"}}"));
        assertEquals(200, status);
        assertNotNull(row("GPA.p"));
        assertEquals(1, api.consumed.size());
    }

    public void testCanceledOneTimeNotificationIgnored() {
        product("p", PID, PlayProducts.MONTH_PASS, "GPA.p");
        int status = new RtdnDispatcher(service, PACKAGE).dispatch(envelope("{\"packageName\":\"" + PACKAGE
                + "\",\"oneTimeProductNotification\":{\"notificationType\":2,\"purchaseToken\":\"p\",\"sku\":\"month_pass\"}}"));
        assertEquals(200, status);
        assertNull(row("GPA.p"));
    }

    public void testVoidedNotificationRevokesWhatGoogleListsAsVoided() throws Exception {
        product("p", PID, PlayProducts.MONTH_PASS, "GPA.p");
        service.syncProduct(PlayProducts.MONTH_PASS, "p", null);
        VoidedPage page = new VoidedPage();
        page.orderIds.add("GPA.p");
        api.voidedPages.add(page);
        int status = new RtdnDispatcher(service, PACKAGE).dispatch(envelope("{\"packageName\":\"" + PACKAGE
                + "\",\"voidedPurchaseNotification\":{\"purchaseToken\":\"p\",\"orderId\":\"GPA.p\",\"productType\":2}}"));
        assertEquals(200, status);
        assertEquals(0, row("GPA.p").level);
        assertEquals(1, api.voidedStartTimes.size());
    }

    public void testVoidedNotificationForAnOrderGoogleDoesNotListRevokesNothing() throws Exception {
        product("p", PID, PlayProducts.MONTH_PASS, "GPA.p");
        service.syncProduct(PlayProducts.MONTH_PASS, "p", null);
        int status = new RtdnDispatcher(service, PACKAGE).dispatch(envelope("{\"packageName\":\"" + PACKAGE
                + "\",\"voidedPurchaseNotification\":{\"purchaseToken\":\"p\",\"orderId\":\"GPA.p\",\"productType\":2}}"));
        assertEquals(200, status);
        assertEquals(PlayProducts.MONTH_LEVEL, row("GPA.p").level);
        assertEquals(PlayStates.CONSUMED, android("p").state);
    }

    public void testVoidedNotificationSendsNoGrantingAlert() throws Exception {
        product("v", PID, PlayProducts.VACATION_10, "GPA.v");
        service.syncProduct(PlayProducts.VACATION_10, "v", null);
        purchases.setState(hash("v"), PlayStates.GRANTING);
        now += 2 * 3600 * 1000L;
        new RtdnDispatcher(service, PACKAGE).dispatch(envelope("{\"packageName\":\"" + PACKAGE
                + "\",\"voidedPurchaseNotification\":{\"purchaseToken\":\"x\",\"orderId\":\"GPA.x\",\"productType\":2}}"));
        assertEquals(0, players.alerts.size());
    }

    public void testVoidedListFailureIs500() throws Exception {
        api.failVoided = 1;
        int status = new RtdnDispatcher(service, PACKAGE).dispatch(envelope("{\"packageName\":\"" + PACKAGE
                + "\",\"voidedPurchaseNotification\":{\"purchaseToken\":\"p\",\"orderId\":\"GPA.p\",\"productType\":2}}"));
        assertEquals(500, status);
    }

    public void testOneTimeNotificationUsesTheStoredProductOverTheSku() throws Exception {
        // Google's answer has no productId, and the token's first delivery stored vacation_10 but did not grant
        product("v", PID, PlayProducts.VACATION_10, "GPA.v").productId = null;
        subscribers.failInserts = 1;
        try {
            service.syncProduct(PlayProducts.VACATION_10, "v", null);
            fail("expected PlayStoreException");
        } catch (org.pente.subscriptions.play.PlayStoreException expected) {
            // redelivered
        }
        assertEquals(PlayProducts.VACATION_10, android("v").productId);
        int status = new RtdnDispatcher(service, PACKAGE).dispatch(envelope("{\"packageName\":\"" + PACKAGE
                + "\",\"oneTimeProductNotification\":{\"notificationType\":1,\"purchaseToken\":\"v\",\"sku\":\"vacation_60\"}}"));
        assertEquals(200, status);
        assertEquals(10, players.vacationDaysOf(PID));
    }

    public void testUnknownPidWrongPackageMalformedAndTestAre200() {
        subscription("tok", PID, PlayStates.ACTIVE, "GPA.1", now + 365 * DAY).obfuscatedAccountId = null;
        RtdnDispatcher d = new RtdnDispatcher(service, PACKAGE);
        assertEquals(200, d.dispatch(envelope("{\"packageName\":\"" + PACKAGE
                + "\",\"subscriptionNotification\":{\"notificationType\":4,\"purchaseToken\":\"tok\"}}")));
        assertEquals(200, d.dispatch(envelope("{\"packageName\":\"com.other\","
                + "\"subscriptionNotification\":{\"notificationType\":4,\"purchaseToken\":\"tok\"}}")));
        assertEquals(200, d.dispatch("garbage"));
        assertEquals(200, d.dispatch(envelope("{\"packageName\":\"" + PACKAGE + "\",\"testNotification\":{}}")));
        assertEquals(0, subscribers.rows.size());
    }

    public void testTransientGoogleErrorIs500() {
        subscription("tok", PID, PlayStates.ACTIVE, "GPA.1", now + 365 * DAY);
        api.failGets = 1;
        int status = new RtdnDispatcher(service, PACKAGE).dispatch(envelope("{\"packageName\":\"" + PACKAGE
                + "\",\"subscriptionNotification\":{\"notificationType\":2,\"purchaseToken\":\"tok\"}}"));
        assertEquals(500, status);
    }
}
