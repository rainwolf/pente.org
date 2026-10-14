package org.pente.subscriptions.play;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.google.api.services.androidpublisher.model.ProductPurchase;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseLineItem;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.google.api.services.androidpublisher.model.VoidedPurchase;
import com.google.api.services.androidpublisher.model.VoidedPurchasesListResponse;

/** Maps androidpublisher v3 model objects to the PlayApi DTOs. Pure, so it is unit-tested. */
public final class GooglePlayApiMapping {

    private GooglePlayApiMapping() {
    }

    /** 400 (malformed token), 404 and 410 (unknown, or more than 60 days past expiry) mean "no such purchase" */
    public static boolean isNotFound(int status) {
        return status == 400 || status == 404 || status == 410;
    }

    /** RFC 3339 to epoch ms, null when absent or unparseable */
    public static Long parseTime(String rfc3339) {
        if (rfc3339 == null) {
            return null;
        }
        try {
            return Long.valueOf(OffsetDateTime.parse(rfc3339).toInstant().toEpochMilli());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static PlaySubscription toSubscription(SubscriptionPurchaseV2 v2) {
        PlaySubscription s = new PlaySubscription();
        s.subscriptionState = v2.getSubscriptionState();
        s.acknowledgementState = v2.getAcknowledgementState();
        s.linkedPurchaseToken = v2.getLinkedPurchaseToken();
        if (v2.getExternalAccountIdentifiers() != null) {
            s.obfuscatedAccountId = v2.getExternalAccountIdentifiers().getObfuscatedExternalAccountId();
        }
        if (v2.getOutOfAppPurchaseContext() != null) {
            s.expiredPurchaseToken = v2.getOutOfAppPurchaseContext().getExpiredPurchaseToken();
        }
        List<SubscriptionPurchaseLineItem> items = v2.getLineItems();
        if (items != null && !items.isEmpty()) {
            SubscriptionPurchaseLineItem item = items.get(0);
            for (SubscriptionPurchaseLineItem candidate : items) {
                if (PlayProducts.YEARLY.equals(candidate.getProductId())) {
                    item = candidate;
                    break;
                }
            }
            s.productId = item.getProductId();
            s.latestSuccessfulOrderId = item.getLatestSuccessfulOrderId();
            s.expiryTimeMs = parseTime(item.getExpiryTime());
        }
        return s;
    }

    public static PlayProduct toProduct(ProductPurchase p) {
        PlayProduct r = new PlayProduct();
        r.productId = p.getProductId();
        r.purchaseState = p.getPurchaseState();
        r.quantity = p.getQuantity();
        r.consumptionState = p.getConsumptionState();
        r.acknowledgementState = p.getAcknowledgementState();
        r.orderId = p.getOrderId();
        r.obfuscatedAccountId = p.getObfuscatedExternalAccountId();
        return r;
    }

    public static VoidedPage toVoidedPage(VoidedPurchasesListResponse response) {
        VoidedPage page = new VoidedPage();
        if (response.getVoidedPurchases() != null) {
            for (VoidedPurchase v : response.getVoidedPurchases()) {
                if (v.getOrderId() != null) {
                    page.orderIds.add(v.getOrderId());
                }
            }
        }
        if (response.getTokenPagination() != null) {
            page.nextPageToken = response.getTokenPagination().getNextPageToken();
        }
        return page;
    }
}
