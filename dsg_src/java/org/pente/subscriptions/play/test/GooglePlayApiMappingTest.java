package org.pente.subscriptions.play.test;

import java.util.Arrays;

import junit.framework.TestCase;

import com.google.api.services.androidpublisher.model.ExternalAccountIdentifiers;
import com.google.api.services.androidpublisher.model.OutOfAppPurchaseContext;
import com.google.api.services.androidpublisher.model.ProductPurchase;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseLineItem;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.google.api.services.androidpublisher.model.TokenPagination;
import com.google.api.services.androidpublisher.model.VoidedPurchase;
import com.google.api.services.androidpublisher.model.VoidedPurchasesListResponse;

import org.pente.subscriptions.play.GooglePlayApiMapping;
import org.pente.subscriptions.play.PlayProduct;
import org.pente.subscriptions.play.PlaySubscription;
import org.pente.subscriptions.play.VoidedPage;

/**
 * Mapping from androidpublisher v3 model objects to the PlayApi DTOs: latestSuccessfulOrderId
 * (not the removed latestOrderId), expiredPurchaseToken, RFC 3339 expiry, not-found statuses.
 *
 * Run: ant test-play-billing
 */
public class GooglePlayApiMappingTest extends TestCase {

    public GooglePlayApiMappingTest(String name) {
        super(name);
    }

    public void testSubscriptionMapping() {
        SubscriptionPurchaseV2 v2 = new SubscriptionPurchaseV2()
                .setSubscriptionState("SUBSCRIPTION_STATE_ACTIVE")
                .setAcknowledgementState("ACKNOWLEDGEMENT_STATE_PENDING")
                .setLinkedPurchaseToken("old-token")
                .setExternalAccountIdentifiers(new ExternalAccountIdentifiers().setObfuscatedExternalAccountId("42.abc"))
                .setOutOfAppPurchaseContext(new OutOfAppPurchaseContext().setExpiredPurchaseToken("expired-token"))
                .setLineItems(Arrays.asList(
                        new SubscriptionPurchaseLineItem().setProductId("other_product").setLatestSuccessfulOrderId("GPA.x"),
                        new SubscriptionPurchaseLineItem().setProductId("yearly_subscription")
                                .setExpiryTime("2027-10-11T09:30:00.123Z")
                                .setLatestSuccessfulOrderId("GPA.3345-1234-5678-12345..0")));
        PlaySubscription s = GooglePlayApiMapping.toSubscription(v2);
        assertEquals("SUBSCRIPTION_STATE_ACTIVE", s.subscriptionState);
        assertEquals("ACKNOWLEDGEMENT_STATE_PENDING", s.acknowledgementState);
        assertEquals("old-token", s.linkedPurchaseToken);
        assertEquals("42.abc", s.obfuscatedAccountId);
        assertEquals("expired-token", s.expiredPurchaseToken);
        assertEquals("yearly_subscription", s.productId);
        assertEquals("GPA.3345-1234-5678-12345..0", s.latestSuccessfulOrderId);
        assertEquals(Long.valueOf(1823247000123L), s.expiryTimeMs);
    }

    public void testSparseSubscription() {
        PlaySubscription s = GooglePlayApiMapping.toSubscription(new SubscriptionPurchaseV2()
                .setSubscriptionState("SUBSCRIPTION_STATE_PENDING"));
        assertNull(s.productId);
        assertNull(s.expiryTimeMs);
        assertNull(s.obfuscatedAccountId);
        assertNull(s.expiredPurchaseToken);
    }

    public void testParseTime() {
        assertEquals(Long.valueOf(0L), GooglePlayApiMapping.parseTime("1970-01-01T00:00:00Z"));
        assertEquals(Long.valueOf(1000L), GooglePlayApiMapping.parseTime("1970-01-01T01:00:01+01:00"));
        assertNull(GooglePlayApiMapping.parseTime(null));
        assertNull(GooglePlayApiMapping.parseTime("yesterday"));
    }

    public void testProductMapping() {
        ProductPurchase p = new ProductPurchase()
                .setProductId("month_pass")
                .setPurchaseState(Integer.valueOf(0))
                .setQuantity(Integer.valueOf(1))
                .setConsumptionState(Integer.valueOf(0))
                .setAcknowledgementState(Integer.valueOf(0))
                .setOrderId("GPA.7")
                .setObfuscatedExternalAccountId("42.abc");
        PlayProduct r = GooglePlayApiMapping.toProduct(p);
        assertEquals("month_pass", r.productId);
        assertEquals(Integer.valueOf(0), r.purchaseState);
        assertEquals(Integer.valueOf(1), r.quantity);
        assertEquals(Integer.valueOf(0), r.consumptionState);
        assertEquals(Integer.valueOf(0), r.acknowledgementState);
        assertEquals("GPA.7", r.orderId);
        assertEquals("42.abc", r.obfuscatedAccountId);
    }

    public void testVoidedMapping() {
        VoidedPurchasesListResponse response = new VoidedPurchasesListResponse()
                .setVoidedPurchases(Arrays.asList(new VoidedPurchase().setOrderId("GPA.1"),
                        new VoidedPurchase().setPurchaseToken("no-order"), new VoidedPurchase().setOrderId("GPA.2")))
                .setTokenPagination(new TokenPagination().setNextPageToken("next"));
        VoidedPage page = GooglePlayApiMapping.toVoidedPage(response);
        assertEquals(Arrays.asList("GPA.1", "GPA.2"), page.orderIds);
        assertEquals("next", page.nextPageToken);
        VoidedPage empty = GooglePlayApiMapping.toVoidedPage(new VoidedPurchasesListResponse());
        assertEquals(0, empty.orderIds.size());
        assertNull(empty.nextPageToken);
    }

    public void testNotFoundStatuses() {
        assertTrue(GooglePlayApiMapping.isNotFound(400));
        assertTrue(GooglePlayApiMapping.isNotFound(404));
        assertTrue(GooglePlayApiMapping.isNotFound(410));
        assertTrue(!GooglePlayApiMapping.isNotFound(401));
        assertTrue(!GooglePlayApiMapping.isNotFound(403));
        assertTrue(!GooglePlayApiMapping.isNotFound(500));
        assertTrue(!GooglePlayApiMapping.isNotFound(503));
    }
}
