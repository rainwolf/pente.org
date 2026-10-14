package org.pente.subscriptions.play;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.apache.log4j.Category;
import org.pente.gameServer.core.MySQLDSGPlayerStorer;

/**
 * Google Play entitlement logic shared by GooglePlayPurchaseServlet, GooglePlayRtdnServlet and the
 * CacheDSGPlayerStorer pollers (spec docs/superpowers/specs/2026-10-11-android-play-billing-design.md §4.5).
 * Pure logic over PlayApi, the two stores and PlayerGateway, so it is unit-tested with fakes.
 *
 * MyISAM has no transactions: every step except the vacation grant is idempotent and keyed on the
 * order id or token hash, so a crash between steps heals on the next sync. Grants for one pid are
 * serialised by a per-pid lock so concurrent deliveries cannot stack from the same cached player data.
 */
public class PlayEntitlementService {

    private static final Category log4j = Category.getInstance(PlayEntitlementService.class.getName());

    /** clamp only when Play's expiry is more than 2 days before the row's implied expiry (encoding slack) */
    static final long CLAMP_SLACK_MS = 2 * PlayProducts.DAY_MS;
    /** GRANTING rows older than this get a daily admin alert */
    static final long GRANTING_STALE_MS = 3600L * 1000;
    /** voided purchases look-back: Play allows 30 days; one hour of margin keeps startTime inside the window */
    static final long VOIDED_WINDOW_MS = 30 * PlayProducts.DAY_MS - 3600L * 1000;
    /** safety stop for voided-purchases paging */
    static final int MAX_VOIDED_PAGES = 100;

    private final PlayApi api;
    private final AndroidPurchaseStore purchases;
    private final SubscriberStore subscribers;
    private final PlayerGateway players;
    private final AccountIds accountIds;
    private final LongSupplier clock;
    private final ConcurrentHashMap<Long, Object> pidLocks = new ConcurrentHashMap<Long, Object>();

    public PlayEntitlementService(PlayApi api, AndroidPurchaseStore purchases, SubscriberStore subscribers,
                                  PlayerGateway players, AccountIds accountIds, LongSupplier clock) {
        this.api = api;
        this.purchases = purchases;
        this.subscribers = subscribers;
        this.players = players;
        this.accountIds = accountIds;
        this.clock = clock;
    }

    /** the obfuscatedAccountId the Android client passes to BillingFlowParams */
    public String accountId(long pid) {
        return accountIds.create(pid);
    }

    private Object lockFor(long pid) {
        Object lock = pidLocks.get(Long.valueOf(pid));
        if (lock == null) {
            Object fresh = new Object();
            lock = pidLocks.putIfAbsent(Long.valueOf(pid), fresh);
            if (lock == null) {
                lock = fresh;
            }
        }
        return lock;
    }

    /**
     * §4.3 resolvePid: the HMAC-checked obfuscated account id; only when it is absent, the pid stored
     * for outOfAppPurchaseContext.expiredPurchaseToken. null when neither resolves.
     */
    Long resolvePid(String obfuscatedAccountId, String expiredPurchaseToken) throws PlayStoreException {
        if (obfuscatedAccountId != null && !obfuscatedAccountId.isEmpty()) {
            Long pid = accountIds.parse(obfuscatedAccountId);
            if (pid == null) {
                log4j.warn("PlayEntitlementService: rejected account id " + obfuscatedAccountId);
            }
            return pid;
        }
        if (expiredPurchaseToken != null && !expiredPurchaseToken.isEmpty()) {
            AndroidPurchaseRow expired = purchases.find(PlayTokens.sha256Hex(expiredPurchaseToken));
            if (expired != null) {
                return Long.valueOf(expired.pid);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- subscriptions (§4.5)

    /**
     * Re-read a subscription from Google and grant, extend (deferral), clamp, acknowledge and link.
     *
     * @param requesterPid the logged-in player for a client POST, null for RTDN and the pollers
     */
    public PlayClassification syncSubscription(String purchaseToken, Long requesterPid)
            throws PlayApiException, PlayStoreException {
        PlaySubscription sub = api.getSubscription(purchaseToken);
        PlayClassification rejected = rejectSubscription(sub, requesterPid);
        if (rejected != null) {
            return rejected;
        }
        long pid = resolvePid(sub.obfuscatedAccountId, sub.expiredPurchaseToken).longValue();
        synchronized (lockFor(pid)) {
            // Re-read under the lock: an answer read before another sync of this token was applied (say an
            // ACTIVE read racing a revoke clamp) must never be applied after it.
            PlaySubscription fresh = api.getSubscription(purchaseToken);
            rejected = rejectSubscription(fresh, requesterPid);
            if (rejected != null) {
                return rejected;
            }
            Long freshPid = resolvePid(fresh.obfuscatedAccountId, fresh.expiredPurchaseToken);
            if (freshPid.longValue() != pid) {
                log4j.warn("PlayEntitlementService: subscription token moved from pid " + pid + " to " + freshPid);
                return PlayClassification.INVALID;
            }
            return syncSubscriptionLocked(purchaseToken, fresh, pid);
        }
    }

    /** INVALID or SHARED when this answer must not be applied for requesterPid, else null */
    private PlayClassification rejectSubscription(PlaySubscription sub, Long requesterPid) throws PlayStoreException {
        if (sub == null || !PlayProducts.isSubscription(sub.productId)) {
            return PlayClassification.INVALID;
        }
        Long pid = resolvePid(sub.obfuscatedAccountId, sub.expiredPurchaseToken);
        if (pid == null) {
            log4j.info("PlayEntitlementService: no pid for subscription token, not granted or acknowledged");
            return PlayClassification.INVALID;
        }
        if (requesterPid != null && pid.longValue() != requesterPid.longValue()) {
            return PlayClassification.SHARED;
        }
        return null;
    }

    private PlayClassification syncSubscriptionLocked(String purchaseToken, PlaySubscription sub, long pid)
            throws PlayStoreException {
        long now = clock.getAsLong();
        String tokenHash = PlayTokens.sha256Hex(purchaseToken);
        String linkedHash = sub.linkedPurchaseToken == null ? null : PlayTokens.sha256Hex(sub.linkedPurchaseToken);
        AndroidPurchaseRow prior = purchases.find(tokenHash);

        AndroidPurchaseRow row = new AndroidPurchaseRow();
        row.tokenHash = tokenHash;
        row.purchaseToken = purchaseToken;
        row.pid = pid;
        row.productId = sub.productId;
        row.latestSuccessfulOrderId = sub.latestSuccessfulOrderId;
        row.linkedPurchaseTokenHash = linkedHash;
        row.expiryTimeMs = sub.expiryTimeMs;
        row.state = sub.subscriptionState;
        row.acknowledged = PlayStates.ACK_ACKNOWLEDGED.equals(sub.acknowledgementState);
        purchases.upsertSubscription(row);

        String orderId = sub.latestSuccessfulOrderId;
        PlayClassification result = PlayClassification.INACTIVE;
        boolean recorded = false;
        boolean grantable = orderId != null && PlayStates.isGrantable(sub.subscriptionState)
                && sub.expiryTimeMs != null && sub.expiryTimeMs.longValue() > now;
        if (grantable) {
            long paymentDate = sub.expiryTimeMs.longValue() - PlayProducts.PAYMENTDATE_OFFSET_MS;
            if (subscribers.insert(new SubscriberRow(pid, orderId, PlayProducts.YEARLY_LEVEL, paymentDate))) {
                recorded = true;
                result = isRenewal(prior, orderId, linkedHash, pid) ? PlayClassification.RENEWAL : PlayClassification.NEW;
                players.granted(pid, sub.productId, result);
            } else {
                SubscriberRow existing = subscribers.findByTransactionId(orderId);
                if (existing == null || existing.pid != pid) {
                    log4j.warn("PlayEntitlementService: order " + orderId + " is recorded for another pid than " + pid);
                    return PlayClassification.SHARED;
                }
                recorded = true;
                result = PlayClassification.KNOWN;
                if (existing.level != 0 && paymentDate > existing.paymentDateMs) {
                    // deferral: Play moved expiryTime later for an order we already recorded
                    subscribers.updatePaymentDate(orderId, paymentDate);
                    players.entitlementChanged(pid);
                }
            }
        } else if (orderId != null && PlayStates.isClamped(sub.subscriptionState) && sub.expiryTimeMs != null) {
            SubscriberRow existing = subscribers.findByTransactionId(orderId);
            if (existing != null && existing.pid == pid && existing.level != 0) {
                Long implied = SubscriberRule.impliedExpiryMs(existing.level, existing.paymentDateMs);
                if (implied != null && sub.expiryTimeMs.longValue() < implied.longValue() - CLAMP_SLACK_MS) {
                    // entitlement ends at Play's expiry (expired, revoked or on hold), not at the year boundary
                    long clamped = sub.expiryTimeMs.longValue() - PlayProducts.PAYMENTDATE_OFFSET_MS;
                    restackMonthPasses(pid, orderId, existing.level, clamped, now);
                    subscribers.updatePaymentDate(orderId, clamped);
                    players.entitlementChanged(pid);
                }
            }
        }

        if (recorded && PlayStates.ACK_PENDING.equals(sub.acknowledgementState)) {
            try {
                api.acknowledgeSubscription(purchaseToken);
                purchases.setAcknowledged(tokenHash);
            } catch (PlayApiException e) {
                log4j.warn("PlayEntitlementService: acknowledge failed, the 6-hour poller retries: " + e);
            }
        }
        if (linkedHash != null) {
            purchases.setState(linkedHash, PlayStates.SUPERSEDED);
        }
        return result;
    }

    private boolean isRenewal(AndroidPurchaseRow prior, String orderId, String linkedHash, long pid)
            throws PlayStoreException {
        if (prior != null && prior.latestSuccessfulOrderId != null && !prior.latestSuccessfulOrderId.equals(orderId)) {
            return true;
        }
        if (linkedHash != null) {
            AndroidPurchaseRow linked = purchases.find(linkedHash);
            return linked != null && linked.pid == pid;
        }
        return false;
    }

    // ---------------------------------------------------------------- one-time products (§4.5)

    /** a KNOWN reply to the buyer's own POST within this long of the token's first record still says "success" */
    static final long THANK_YOU_WINDOW_MS = 10 * 60 * 1000L;

    /**
     * client POST: the client's product id picks the API call; an API answer with a different product id is
     * INVALID, and an answer without one is granted as the requested product (spec 4.5 step 1).
     * The RTDN push often records the grant before the buyer's own POST arrives, so a KNOWN answer for a
     * token this pid first recorded under 10 minutes ago is reported as NEW and the buyer still sees the thank-you.
     */
    public PlayClassification syncFromClient(String productId, String purchaseToken, long requesterPid)
            throws PlayApiException, PlayStoreException {
        PlayClassification result;
        if (PlayProducts.isSubscription(productId)) {
            result = syncSubscription(purchaseToken, Long.valueOf(requesterPid));
        } else {
            result = syncProduct(productId, purchaseToken, Long.valueOf(requesterPid));
        }
        if (result == PlayClassification.KNOWN) {
            AndroidPurchaseRow row = purchases.find(PlayTokens.sha256Hex(purchaseToken));
            if (row != null && row.pid == requesterPid && clock.getAsLong() - row.createdMs < THANK_YOU_WINDOW_MS) {
                return PlayClassification.NEW;
            }
        }
        return result;
    }

    /**
     * Re-read a one-time purchase from Google, grant it once, then consume it.
     *
     * @param requestedProductId the product id products.get is called with; it must be on the whitelist. It
     *                           names the grant when Google's answer has no productId; an answer with a
     *                           different productId is INVALID. For RTDN and the pollers (requesterPid null)
     *                           a one-time product already stored for the token replaces it.
     * @param requesterPid       the logged-in player for a client POST, null for RTDN and the pollers
     */
    public PlayClassification syncProduct(String requestedProductId, String purchaseToken, Long requesterPid)
            throws PlayApiException, PlayStoreException {
        if (!PlayProducts.isOneTime(requestedProductId)) {
            return PlayClassification.INVALID;
        }
        if (requesterPid == null) {
            // an RTDN sku is only the push body's word: a product already stored for this token wins
            AndroidPurchaseRow stored = purchases.find(PlayTokens.sha256Hex(purchaseToken));
            if (stored != null && PlayProducts.isOneTime(stored.productId)) {
                requestedProductId = stored.productId;
            }
        }
        PlayProduct product = api.getProduct(requestedProductId, purchaseToken);
        PlayClassification rejected = rejectProduct(product, requestedProductId, requesterPid);
        if (rejected != null) {
            return rejected;
        }
        long pid = resolvePid(product.obfuscatedAccountId, null).longValue();
        synchronized (lockFor(pid)) {
            // Re-read under the lock: an answer read before another sync of this token was applied must never
            // be applied after it.
            PlayProduct fresh = api.getProduct(requestedProductId, purchaseToken);
            rejected = rejectProduct(fresh, requestedProductId, requesterPid);
            if (rejected != null) {
                return rejected;
            }
            Long freshPid = resolvePid(fresh.obfuscatedAccountId, null);
            if (freshPid.longValue() != pid) {
                log4j.warn("PlayEntitlementService: product token moved from pid " + pid + " to " + freshPid);
                return PlayClassification.INVALID;
            }
            String productId = fresh.productId != null ? fresh.productId : requestedProductId;
            return syncProductLocked(productId, purchaseToken, fresh, pid);
        }
    }

    /** INVALID, INACTIVE or SHARED when this answer must not be applied for requesterPid, else null */
    private PlayClassification rejectProduct(PlayProduct product, String requestedProductId, Long requesterPid)
            throws PlayStoreException {
        if (product == null) {
            return PlayClassification.INVALID;
        }
        // ProductPurchase.productId "may not be present" (spec 4.5 step 1). When it is absent the requested id
        // names the grant: products.get is keyed by that id, so a successful answer ties the token to it, and it
        // has passed the whitelist. An API id that differs from the requested one is refused.
        if (product.productId != null && !product.productId.equals(requestedProductId)) {
            log4j.warn("PlayEntitlementService: API productId " + product.productId + " differs from the requested "
                    + requestedProductId);
            return PlayClassification.INVALID;
        }
        String productId = product.productId != null ? product.productId : requestedProductId;
        if (!PlayProducts.isOneTime(productId)) {
            return PlayClassification.INVALID;
        }
        if (product.purchaseState == null || product.purchaseState.intValue() != 0) {
            boolean pending = product.purchaseState != null && product.purchaseState.intValue() == 2;
            return pending ? PlayClassification.INACTIVE : PlayClassification.INVALID;
        }
        if (product.quantity != null && product.quantity.intValue() != 1) {
            log4j.warn("PlayEntitlementService: rejected quantity " + product.quantity + " for " + productId);
            return PlayClassification.INVALID;
        }
        Long pid = resolvePid(product.obfuscatedAccountId, null);
        if (pid == null) {
            log4j.info("PlayEntitlementService: no pid for " + productId + " token, not granted or consumed");
            return PlayClassification.INVALID;
        }
        if (requesterPid != null && pid.longValue() != requesterPid.longValue()) {
            return PlayClassification.SHARED;
        }
        return null;
    }

    private PlayClassification syncProductLocked(String productId, String purchaseToken, PlayProduct product, long pid)
            throws PlayApiException, PlayStoreException {
        long now = clock.getAsLong();
        String tokenHash = PlayTokens.sha256Hex(purchaseToken);
        String key = PlayTokens.orderKey(product.orderId, tokenHash);

        AndroidPurchaseRow row = purchases.find(tokenHash);
        if (row == null) {
            row = new AndroidPurchaseRow();
            row.tokenHash = tokenHash;
            row.purchaseToken = purchaseToken;
            row.pid = pid;
            row.productId = productId;
            row.latestSuccessfulOrderId = key;
            row.state = PlayStates.PURCHASED;
            row.acknowledged = product.acknowledgementState != null && product.acknowledgementState.intValue() == 1;
            purchases.insertProductIfAbsent(row);
        }

        String state = row.state;
        if (PlayStates.REFUNDED.equals(state)) {
            return PlayClassification.REFUNDED;
        }
        if (PlayStates.REVOKED.equals(state)) {
            return PlayClassification.INVALID;
        }
        if (PlayStates.GRANTING.equals(state)) {
            // a crash between addExtraTBVacationDays and GRANTED: fixed by hand after the daily admin alert
            log4j.warn("PlayEntitlementService: " + productId + " for pid " + pid + " is stuck in GRANTING");
            return PlayClassification.KNOWN;
        }

        PlayClassification result;
        if (PlayStates.GRANTED.equals(state) || PlayStates.CONSUMED.equals(state)) {
            result = PlayClassification.KNOWN;
        } else {
            int days = PlayProducts.vacationDays(productId);
            if (days > 0 && !players.vacationAllowed(pid)) {
                if (product.orderId != null) {
                    api.refundOrder(product.orderId);
                }
                purchases.setState(tokenHash, PlayStates.REFUNDED);
                return PlayClassification.REFUNDED;
            }
            boolean inserted;
            if (days > 0) {
                // the level-0 insert is idempotent (keyed on the order), so it goes before GRANTING: a failed
                // insert is retried; only a crash in addVacationDays leaves the row in GRANTING
                inserted = subscribers.insert(new SubscriberRow(pid, key, 0, now));
                purchases.setState(tokenHash, PlayStates.GRANTING);
                players.addVacationDays(pid, days);
                purchases.setState(tokenHash, PlayStates.GRANTED);
            } else {
                // month pass: stack after the current entitlement whatever its level, so it is never wasted
                Long current = players.subscriptionExpirationMs(pid);
                long paymentDate = current != null && current.longValue() > now ? current.longValue() : now;
                inserted = subscribers.insert(new SubscriberRow(pid, key, PlayProducts.MONTH_LEVEL, paymentDate));
                purchases.setState(tokenHash, PlayStates.GRANTED);
            }
            result = inserted ? PlayClassification.NEW : PlayClassification.KNOWN;
            players.granted(pid, productId, result);
        }

        if (!PlayStates.CONSUMED.equals(state)) {
            try {
                if (product.consumptionState == null || product.consumptionState.intValue() != 1) {
                    api.consumeProduct(productId, purchaseToken);
                }
                purchases.setState(tokenHash, PlayStates.CONSUMED);
            } catch (PlayApiException e) {
                log4j.warn("PlayEntitlementService: consume failed, retried by resync, RTDN or the poller: " + e);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- revoke (§4.5)

    /**
     * A voided order (refund or chargeback): level=0 on its dsg_subscribers row, REVOKED on its one-time
     * dsg_subscribers_android rows (a yearly row is per token and keeps Google's state), month passes stacked after it (ONEMONTH rows dated after now) restacked
     * after the remaining entitlement, refreshPlayer.
     * Vacation days already granted are not clawed back. A repeat revoke is a no-op.
     */
    public void revoke(String orderId) throws PlayStoreException {
        if (orderId == null || orderId.isEmpty()) {
            return;
        }
        List<AndroidPurchaseRow> rows = purchases.findByOrderId(orderId);
        SubscriberRow subscriber = subscribers.findByTransactionId(orderId);
        Long pid = subscriber != null ? Long.valueOf(subscriber.pid)
                : (rows.isEmpty() ? null : Long.valueOf(rows.get(0).pid));
        if (pid == null) {
            return;
        }
        synchronized (lockFor(pid.longValue())) {
            boolean changed = false;
            SubscriberRow current = subscribers.findByTransactionId(orderId);
            if (current != null && current.level != 0) {
                // before setLevelZero, so a crash in between is redone by the repeat revoke
                restackMonthPasses(current.pid, orderId, 0, current.paymentDateMs, clock.getAsLong());
                subscribers.setLevelZero(orderId);
                changed = true;
            }
            for (AndroidPurchaseRow row : rows) {
                // a yearly row is per token, not per order: it keeps Google's state so the poller still
                // follows later renewals; the order's level 0 above already removes access
                if (PlayProducts.YEARLY.equals(row.productId)) {
                    continue;
                }
                // a REFUNDED vacation purchase (refunded during a tourney, nothing granted) stays REFUNDED when
                // its voided notification comes back, so its later replies stay success:refunded
                if (!PlayStates.REVOKED.equals(row.state) && !PlayStates.REFUNDED.equals(row.state)) {
                    purchases.setState(row.tokenHash, PlayStates.REVOKED);
                    changed = true;
                }
            }
            if (changed) {
                players.entitlementChanged(pid.longValue());
            }
        }
    }

    /**
     * A row about to be zeroed (revoke) or a ONEYEAR row about to be shortened (clamp), given as its new level and
     * paymentdate: month passes stacked after its old expiry (ONEMONTH rows dated after now) move back
     * to start, in their order, where the remaining entitlement now ends, or now. A row never moves
     * later. Idempotent, and run before the yearly row is written, so a crash in between is redone by
     * the repeat revoke or the next sync. Caller holds the pid lock.
     */
    private void restackMonthPasses(long pid, String transactionId, int newLevel, long newPaymentDateMs, long now)
            throws PlayStoreException {
        List<SubscriberRow> started = new ArrayList<SubscriberRow>();
        List<SubscriberRow> stacked = new ArrayList<SubscriberRow>();
        for (SubscriberRow r : subscribers.findByPid(pid)) {
            if (transactionId.equals(r.transactionId)) {
                r = new SubscriberRow(r.pid, r.transactionId, newLevel, newPaymentDateMs);
            }
            if ((r.level & MySQLDSGPlayerStorer.ONEMONTH) != 0 && r.paymentDateMs > now) {
                stacked.add(r);
            } else {
                started.add(r);
            }
        }
        if (stacked.isEmpty()) {
            return;
        }
        Collections.sort(stacked, new Comparator<SubscriberRow>() {
            public int compare(SubscriberRow a, SubscriberRow b) {
                return Long.compare(a.paymentDateMs, b.paymentDateMs);
            }
        });
        Long end = SubscriberRule.expirationMs(started, now);
        long start = end != null && end.longValue() > now ? end.longValue() : now;
        for (SubscriberRow r : stacked) {
            long paymentDate = r.paymentDateMs;
            if (start < paymentDate && r.transactionId != null) {
                subscribers.updatePaymentDate(r.transactionId, start);
                paymentDate = start;
            }
            start = Math.max(start, SubscriberRule.impliedExpiryMs(r.level, paymentDate).longValue());
        }
    }

    // ---------------------------------------------------------------- store page (§4.4)

    /** GET /gameServer/googlePlayPurchase */
    public StoreInfo storeInfo(long pid) throws PlayStoreException {
        StoreInfo info = new StoreInfo();
        info.accountId = accountIds.create(pid);
        info.vacationAllowed = players.vacationAllowed(pid);
        Long expiration = players.subscriptionExpirationMs(pid);
        info.subscribedUntilMs = expiration != null && expiration.longValue() > clock.getAsLong() ? expiration : null;
        info.autoRenewing = purchases.hasAutoRenewingSubscription(pid);
        return info;
    }

    // ---------------------------------------------------------------- pollers (§4.7)

    /** CheckAndroidSubscribersRunnable, every 6 hours. One failing token never stops the others. */
    public void pollSubscriptions() throws PlayStoreException {
        long now = clock.getAsLong();
        for (AndroidPurchaseRow row : purchases.subscriptionsToPoll(now)) {
            try {
                syncSubscription(row.purchaseToken, null);
            } catch (PlayApiException e) {
                log4j.warn("PlayEntitlementService: subscription poll failed for " + row.tokenHash + ": " + e);
            } catch (PlayStoreException e) {
                log4j.warn("PlayEntitlementService: subscription poll failed for " + row.tokenHash + ": " + e);
            }
        }
        for (AndroidPurchaseRow row : purchases.productsToPoll(now)) {
            try {
                syncProduct(row.productId, row.purchaseToken, null);
            } catch (PlayApiException e) {
                log4j.warn("PlayEntitlementService: product poll failed for " + row.tokenHash + ": " + e);
            } catch (PlayStoreException e) {
                log4j.warn("PlayEntitlementService: product poll failed for " + row.tokenHash + ": " + e);
            }
        }
    }

    /** CheckAndroidVoidedRunnable, daily: admin alert for stale GRANTING rows, then revoke every voided order. */
    public void pollVoided() throws PlayApiException, PlayStoreException {
        long now = clock.getAsLong();
        for (AndroidPurchaseRow row : purchases.staleGranting(now - GRANTING_STALE_MS)) {
            players.adminAlert("Google Play " + row.productId + " for pid " + row.pid
                    + " stuck in GRANTING (token hash " + row.tokenHash + "): check vacation days, then set the state by hand");
        }
        revokeVoided();
    }

    /**
     * Revoke every order on Google's voided-purchases list (last 30 days, all pages). The daily poller and
     * a voidedPurchaseNotification both use it, so only orders Google lists are revoked, never an order id
     * taken from a push body.
     */
    public void revokeVoided() throws PlayApiException, PlayStoreException {
        long now = clock.getAsLong();
        String pageToken = null;
        int pages = 0;
        do {
            VoidedPage page = api.listVoided(now - VOIDED_WINDOW_MS, pageToken);
            for (String orderId : page.orderIds) {
                revoke(orderId);
            }
            pageToken = page.nextPageToken;
            pages++;
        } while (pageToken != null && pages < MAX_VOIDED_PAGES);
    }

}
