package org.pente.subscriptions.play;

/**
 * Stand-in when the Play service-account key cannot be loaded: every call is a transient failure,
 * so clients get "retry later", RTDN gets 500 and the pollers log, until the key is installed.
 */
public class UnconfiguredPlayApi implements PlayApi {

    private final String reason;

    public UnconfiguredPlayApi(String reason) {
        this.reason = reason;
    }

    private PlayApiException fail() {
        return new PlayApiException("Google Play API not configured: " + reason);
    }

    public PlaySubscription getSubscription(String purchaseToken) throws PlayApiException {
        throw fail();
    }

    public void acknowledgeSubscription(String purchaseToken) throws PlayApiException {
        throw fail();
    }

    public PlayProduct getProduct(String productId, String purchaseToken) throws PlayApiException {
        throw fail();
    }

    public void consumeProduct(String productId, String purchaseToken) throws PlayApiException {
        throw fail();
    }

    public void refundOrder(String orderId) throws PlayApiException {
        throw fail();
    }

    public VoidedPage listVoided(long startTimeMs, String pageToken) throws PlayApiException {
        throw fail();
    }
}
