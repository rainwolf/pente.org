package org.pente.subscriptions.play;

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
    /** a refunded month pass moves later stacked ONEMONTH rows back by this many days */
    static final int MONTH_SHIFT_DAYS = 31;
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
                    subscribers.updatePaymentDate(orderId, sub.expiryTimeMs.longValue() - PlayProducts.PAYMENTDATE_OFFSET_MS);
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
    // ---------------------------------------------------------------- revoke (§4.5)
    // ---------------------------------------------------------------- store page (§4.4)
    // ---------------------------------------------------------------- pollers (§4.7)
}
