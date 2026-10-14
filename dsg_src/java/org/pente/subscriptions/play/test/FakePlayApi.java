package org.pente.subscriptions.play.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pente.subscriptions.play.PlayApi;
import org.pente.subscriptions.play.PlayApiException;
import org.pente.subscriptions.play.PlayProduct;
import org.pente.subscriptions.play.PlaySubscription;
import org.pente.subscriptions.play.VoidedPage;

/** In-memory Google Play Developer API. Thread-safe. */
public class FakePlayApi implements PlayApi {

    /** purchase token -> subscriptionsv2 response */
    public final Map<String, PlaySubscription> subscriptions = new HashMap<String, PlaySubscription>();
    /** purchase token -> products.get response */
    public final Map<String, PlayProduct> products = new HashMap<String, PlayProduct>();
    /**
     * purchase token -> the product it was bought as. When set, products.get with any other product id
     * answers null, as Google answers HTTP 400 for a token of another product (GooglePlayApi maps 400 to null).
     */
    public final Map<String, String> productTokens = new HashMap<String, String>();
    /** voided pages; page i is returned for page token null (i=0) or String.valueOf(i) */
    public final List<VoidedPage> voidedPages = new ArrayList<VoidedPage>();

    public final List<String> acknowledged = new ArrayList<String>();
    public final List<String> consumed = new ArrayList<String>();
    public final List<String> refunded = new ArrayList<String>();
    public final List<Long> voidedStartTimes = new ArrayList<Long>();

    /** number of upcoming calls of each kind that throw PlayApiException */
    public int failGets;
    public int failAcknowledges;
    public int failConsumes;
    public int failRefunds;
    public int failVoided;

    public synchronized PlaySubscription getSubscription(String purchaseToken) throws PlayApiException {
        if (failGets > 0) {
            failGets--;
            throw new PlayApiException("fake: subscriptionsv2.get failed");
        }
        return subscriptions.get(purchaseToken);
    }

    public synchronized void acknowledgeSubscription(String purchaseToken) throws PlayApiException {
        if (failAcknowledges > 0) {
            failAcknowledges--;
            throw new PlayApiException("fake: acknowledge failed");
        }
        acknowledged.add(purchaseToken);
        PlaySubscription s = subscriptions.get(purchaseToken);
        if (s != null) {
            s.acknowledgementState = "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED";
        }
    }

    public synchronized PlayProduct getProduct(String productId, String purchaseToken) throws PlayApiException {
        if (failGets > 0) {
            failGets--;
            throw new PlayApiException("fake: products.get failed");
        }
        String boughtAs = productTokens.get(purchaseToken);
        if (boughtAs != null && !boughtAs.equals(productId)) {
            return null;
        }
        return products.get(purchaseToken);
    }

    public synchronized void consumeProduct(String productId, String purchaseToken) throws PlayApiException {
        if (failConsumes > 0) {
            failConsumes--;
            throw new PlayApiException("fake: consume failed");
        }
        consumed.add(purchaseToken);
        PlayProduct p = products.get(purchaseToken);
        if (p != null) {
            p.consumptionState = Integer.valueOf(1);
            p.acknowledgementState = Integer.valueOf(1);
        }
    }

    public synchronized void refundOrder(String orderId) throws PlayApiException {
        if (failRefunds > 0) {
            failRefunds--;
            throw new PlayApiException("fake: refund failed");
        }
        refunded.add(orderId);
    }

    public synchronized VoidedPage listVoided(long startTimeMs, String pageToken) throws PlayApiException {
        if (failVoided > 0) {
            failVoided--;
            throw new PlayApiException("fake: voidedpurchases.list failed");
        }
        voidedStartTimes.add(Long.valueOf(startTimeMs));
        int index = pageToken == null ? 0 : Integer.parseInt(pageToken);
        if (index >= voidedPages.size()) {
            return new VoidedPage();
        }
        return voidedPages.get(index);
    }
}
