package org.pente.subscriptions.apple;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.apache.log4j.Category;
import org.pente.subscriptions.play.EntitlementLocks;
import org.pente.subscriptions.play.MonthPassStack;
import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStoreException;
import org.pente.subscriptions.play.PlayerGateway;
import org.pente.subscriptions.play.SubscriberRow;
import org.pente.subscriptions.play.SubscriberRule;
import org.pente.subscriptions.play.SubscriberStore;

/**
 * App Store entitlement logic shared by AppStorePurchaseServlet, AppStoreNotificationServlet and the
 * CacheDSGPlayerStorer pollers (spec docs/superpowers/specs/2026-10-15-ios-app-store-server-design.md §4.5).
 * Pure logic over AppleApi, AppleVerifier, AppleStore, SubscriberStore and PlayerGateway, so it is unit-tested
 * with fakes. Every grant for a pid runs inside EntitlementLocks.lockFor(pid), the lock Play uses too.
 *
 * MyISAM has no transactions: every step is keyed on a transaction id (the dsg_subscribers primary key) so a
 * crash between steps heals on the next sync, except the vacation grant, which the GRANTING state exposes.
 */
public class AppleEntitlementService {

    private static final Category log4j = Category.getInstance(AppleEntitlementService.class.getName());

    /** clamp only when Apple's end is more than 2 days before the row's implied expiry (encoding slack) */
    static final long CLAMP_SLACK_MS = 2 * PlayProducts.DAY_MS;
    /** a KNOWN reply to the buyer's own POST within this long of the row's creation still says "success" */
    static final long THANK_YOU_WINDOW_MS = 10 * 60 * 1000L;

    private final AppleApi api;
    private final AppleVerifier verifier;
    private final AppleStore store;
    private final SubscriberStore subscribers;
    private final PlayerGateway players;
    private final AppleAccountTokens tokens;
    private final AppleSandboxGate sandbox;
    private final LongSupplier clock;

    public AppleEntitlementService(AppleApi api, AppleVerifier verifier, AppleStore store, SubscriberStore subscribers,
                                   PlayerGateway players, AppleAccountTokens tokens, AppleSandboxGate sandbox,
                                   LongSupplier clock) {
        this.api = api;
        this.verifier = verifier;
        this.store = store;
        this.subscribers = subscribers;
        this.players = players;
        this.tokens = tokens;
        this.sandbox = sandbox;
        this.clock = clock;
    }

    /** the appAccountToken the iOS app passes to Product.PurchaseOption.appAccountToken */
    public UUID appAccountToken(long pid) {
        return tokens.create(pid);
    }

    // ---------------------------------------------------------------- client POST and GET (§4.4)

    /**
     * POST /gameServer/appStorePurchase signedTransaction=&lt;jwsRepresentation&gt;. Verify, re-read the transaction
     * server-side, resolve its owner, apply the sandbox gate, grant. Never trusts the client's copy beyond its
     * transaction id and environment.
     *
     * @throws AppleApiException  retryable only (OCSP or Apple unreachable, API not configured): "retry later"
     * @throws PlayStoreException a DB or player-storer failure: "retry later"
     */
    public PlayClassification purchase(String signedTransaction, long requesterPid)
            throws AppleApiException, PlayStoreException {
        AppleTransaction claimed;
        try {
            claimed = verifier.verifyTransaction(signedTransaction);
        } catch (AppleVerificationException e) {
            if (e.isRetryable()) {
                throw new AppleApiException("signed transaction cannot be verified now: " + e.getMessage(), true, e);
            }
            log4j.warn("AppleEntitlementService: signed transaction from pid " + requesterPid + " refused: " + e.getMessage());
            return PlayClassification.INVALID;
        }
        if (!AppleProducts.isKnown(claimed.productId) || claimed.familyShared) {
            return PlayClassification.INVALID;
        }
        AppleTransaction tx;
        try {
            tx = api.transactionInfo(claimed.environment, claimed.transactionId);
        } catch (AppleApiException e) {
            if (e.isRetryable()) {
                throw e;
            }
            log4j.warn("AppleEntitlementService: Apple does not know transaction " + claimed.transactionId + ": " + e);
            return PlayClassification.INVALID;
        }
        if (!claimed.transactionId.equals(tx.transactionId) || !AppleProducts.isKnown(tx.productId) || tx.familyShared) {
            return PlayClassification.INVALID;
        }
        if (tx.revocationDateMs != null) {
            revoke(tx, refundOwner(tx));
            return PlayClassification.INVALID;
        }

        // §4.3 resolvePid with a requester, first matching rule wins
        boolean subscription = AppleProducts.isSubscription(tx.productId);
        long owner = requesterPid;
        boolean shared = false;
        if (tx.appAccountToken != null) {
            Long parsed = tokens.parse(tx.appAccountToken);
            if (parsed == null) {
                log4j.warn("AppleEntitlementService: rejected appAccountToken on " + tx.transactionId);
                return PlayClassification.INVALID;
            }
            owner = parsed.longValue();
            shared = owner != requesterPid;
        } else if (subscription && ownedByOthersOnly(tx, requesterPid)) {
            return PlayClassification.SHARED;
        }
        if (!sandboxAllowed(tx.environment, owner)) {
            return PlayClassification.INVALID;
        }

        PlayClassification result = subscription
                ? syncSubscription(tx.originalTransactionId, tx.environment, Long.valueOf(owner))
                : syncOneTime(tx, owner);
        if (shared && result != PlayClassification.INVALID && result != PlayClassification.INACTIVE) {
            return PlayClassification.SHARED;
        }
        if (result == PlayClassification.KNOWN) {
            // a notification often records the grant before the buyer's own POST arrives
            AppleRow row = store.find(subscription ? tx.originalTransactionId : tx.transactionId);
            if (row != null && row.pid == requesterPid && clock.getAsLong() - row.createdMs < THANK_YOU_WINDOW_MS) {
                return PlayClassification.NEW;
            }
        }
        return result;
    }

    /**
     * A subscription without a token (old app versions, offer codes, purchases outside the app): true when its
     * dsg_subscribers_apple row, or else its chain in dsg_subscribers, belongs only to other pids.
     */
    private boolean ownedByOthersOnly(AppleTransaction tx, long requesterPid) throws AppleApiException, PlayStoreException {
        AppleRow row = store.find(tx.originalTransactionId);
        Set<Long> owners = new LinkedHashSet<Long>();
        if (row != null && row.pid != 0) {
            owners.add(Long.valueOf(row.pid));
        } else {
            owners.addAll(chainOwners(tx.environment, tx.originalTransactionId));
        }
        return !owners.isEmpty() && !owners.contains(Long.valueOf(requesterPid));
    }

    /** GET /gameServer/appStorePurchase */
    public AppleStoreInfo storeInfo(long pid) throws PlayStoreException {
        long now = clock.getAsLong();
        AppleStoreInfo info = new AppleStoreInfo();
        info.appAccountToken = tokens.create(pid).toString();
        Long expiration = players.subscriptionExpirationMs(pid);
        info.subscribedUntilMs = expiration != null && expiration.longValue() > now ? expiration : null;
        info.hasActiveAppleSubscription = store.hasActiveSubscription(pid, now);
        info.vacationAllowed = players.vacationAllowed(pid);
        info.vacationDaysLeft = players.vacationDaysLeft(pid);
        info.products = AppleProducts.ON_SALE;
        return info;
    }

    // ---------------------------------------------------------------- owners (§4.3)

    /** true unless env is SANDBOX and pid's username is not on the allow-list (spec §2 D5) */
    boolean sandboxAllowed(AppleEnvironment env, long pid) throws PlayStoreException {
        if (env != AppleEnvironment.SANDBOX) {
            return true;
        }
        if (sandbox.allows(pid)) {
            return true;
        }
        log4j.info("AppleEntitlementService: sandbox transaction for pid " + pid + " refused, not on the allow-list");
        return false;
    }

    /**
     * pids, never the tombstone pid 0, owning dsg_subscribers rows for any transaction of this subscription chain:
     * Get Transaction History V2 filtered to 1YRNOADSORLIMITS, the entries with this originalTransactionId, plus
     * the originalTransactionId itself, as the legacy verifyReceipt path records them.
     */
    Set<Long> chainOwners(AppleEnvironment env, String originalTransactionId) throws AppleApiException, PlayStoreException {
        Set<String> ids = new LinkedHashSet<String>();
        ids.add(originalTransactionId);
        for (AppleTransaction t : api.transactionHistory(env, originalTransactionId, AppleProducts.YEARLY)) {
            if (originalTransactionId.equals(t.originalTransactionId)) {
                ids.add(t.transactionId);
            }
        }
        Set<Long> owners = new LinkedHashSet<Long>();
        for (String id : ids) {
            SubscriberRow r = subscribers.findByTransactionId(id);
            if (r != null && r.pid != 0) {
                owners.add(Long.valueOf(r.pid));
            }
        }
        return owners;
    }

    /**
     * §4.3 rule 4, no requester (notifications and pollers): a valid token on the latest transaction, else the
     * dsg_subscribers_apple row, else the single owner of the chain. null when nothing resolves (logged; the
     * app's POST delivers it) or the token is forged.
     */
    Long subscriptionOwner(AppleTransaction latest, AppleEnvironment env) throws AppleApiException, PlayStoreException {
        if (latest.appAccountToken != null) {
            Long pid = tokens.parse(latest.appAccountToken);
            if (pid == null) {
                log4j.warn("AppleEntitlementService: rejected appAccountToken on " + latest.transactionId);
            }
            return pid;
        }
        AppleRow row = store.find(latest.originalTransactionId);
        // pid 0 is a tombstone written before any owner was known: no owner, fall through to the chain
        if (row != null && row.pid != 0) {
            return Long.valueOf(row.pid);
        }
        Set<Long> owners = chainOwners(env, latest.originalTransactionId);
        if (owners.size() == 1) {
            return owners.iterator().next();
        }
        log4j.info("AppleEntitlementService: no owner for subscription " + latest.originalTransactionId
                + " (" + owners.size() + " chain owners), left to the app's POST");
        return null;
    }

    // ---------------------------------------------------------------- subscriptions (§4.5)

    /** the lastTransactions item for exactly this chain, or null (the response lists every chain of the customer) */
    private AppleSubscriptionStatus readStatus(AppleEnvironment env, String originalTransactionId)
            throws AppleApiException {
        List<AppleSubscriptionStatus> all = api.subscriptionStatuses(env, originalTransactionId);
        for (AppleSubscriptionStatus s : all) {
            if (originalTransactionId.equals(s.originalTransactionId)) {
                return s;
            }
        }
        log4j.warn("AppleEntitlementService: no status item for " + originalTransactionId + " in " + all.size());
        return null;
    }

    /** the owner of this status item, or null when it must not be applied (not ours, Family Sharing, no owner) */
    private Long ownerOf(AppleSubscriptionStatus status, AppleEnvironment env, Long ownerPid)
            throws AppleApiException, PlayStoreException {
        AppleTransaction tx = status.transaction;
        if (tx == null || !AppleProducts.isSubscription(tx.productId)) {
            log4j.warn("AppleEntitlementService: status of " + status.originalTransactionId + " is not 1YRNOADSORLIMITS");
            return null;
        }
        if (tx.familyShared) {
            log4j.info("AppleEntitlementService: Family Sharing transaction " + tx.transactionId + " not granted");
            return null;
        }
        return ownerPid != null ? ownerPid : subscriptionOwner(tx, env);
    }

    /**
     * Read Apple's status of one subscription chain and bring dsg_subscribers and dsg_subscribers_apple in line:
     * grant the latest transaction, extend it during grace, clamp it on expiry or revocation, revoke a refunded one.
     *
     * @param ownerPid the owner a client POST already resolved (§4.3), or null for notifications and pollers
     */
    public PlayClassification syncSubscription(String originalTransactionId, AppleEnvironment env, Long ownerPid)
            throws AppleApiException, PlayStoreException {
        AppleSubscriptionStatus status = readStatus(env, originalTransactionId);
        if (status == null) {
            return PlayClassification.INVALID;
        }
        Long pid = ownerOf(status, env, ownerPid);
        if (pid == null || !sandboxAllowed(env, pid.longValue())) {
            return PlayClassification.INVALID;
        }
        synchronized (EntitlementLocks.lockFor(pid.longValue())) {
            // Re-read under the lock: an answer read before another sync of this chain was applied must never be
            // applied after it.
            AppleSubscriptionStatus fresh = readStatus(env, originalTransactionId);
            if (fresh == null) {
                return PlayClassification.INVALID;
            }
            Long freshPid = ownerOf(fresh, env, ownerPid);
            if (freshPid == null || freshPid.longValue() != pid.longValue()) {
                log4j.warn("AppleEntitlementService: owner of " + originalTransactionId + " changed during the sync");
                return PlayClassification.INVALID;
            }
            return syncSubscriptionLocked(fresh, env, pid.longValue());
        }
    }

    private PlayClassification syncSubscriptionLocked(AppleSubscriptionStatus status, AppleEnvironment env, long pid)
            throws PlayStoreException {
        long now = clock.getAsLong();
        AppleTransaction tx = status.transaction;
        String otid = status.originalTransactionId;
        String txId = tx.transactionId;
        String state = AppleStates.fromStatus(status.status);
        if (state == null) {
            log4j.warn("AppleEntitlementService: unknown status " + status.status + " for " + otid);
            return PlayClassification.INACTIVE;
        }
        AppleRow prior = store.find(otid);
        // §4.3 pid move: only a valid token for pid on the latest transaction moves the chain; already-written rows stay
        boolean moving = prior != null && prior.pid != 0 && prior.pid != pid;
        if (moving && !Long.valueOf(pid).equals(tx.appAccountToken == null ? null : tokens.parse(tx.appAccountToken))) {
            log4j.warn("AppleEntitlementService: subscription " + otid + " belongs to pid " + prior.pid
                    + ", not moved to " + pid + " without its token on the latest transaction");
            return PlayClassification.SHARED;
        }

        boolean revoked = tx.revocationDateMs != null;
        if (!revoked && AppleStates.REVOKED.equals(state)) {
            // trust revocationDate, not the status: after REFUND_REVERSED an active subscription can stay 5
            // (research 2026-10-15-apple-semantics-verified.md §1)
            state = tx.expiresDateMs != null && tx.expiresDateMs.longValue() > now ? AppleStates.ACTIVE : AppleStates.EXPIRED;
        }
        Long effective = tx.expiresDateMs;
        if (revoked) {
            effective = tx.revocationDateMs;
        } else if (AppleStates.GRACE.equals(state) && status.renewal != null
                && status.renewal.gracePeriodExpiresDateMs != null && tx.expiresDateMs != null) {
            effective = Long.valueOf(Math.max(tx.expiresDateMs.longValue(), status.renewal.gracePeriodExpiresDateMs.longValue()));
        }

        PlayClassification result = PlayClassification.INACTIVE;
        if (revoked) {
            revokeLocked(tx, pid, now);
            state = AppleStates.REVOKED;
        } else if ((AppleStates.ACTIVE.equals(state) || AppleStates.GRACE.equals(state))
                && effective != null && effective.longValue() > now) {
            // ACTIVE: expiresDate - 364 days. GRACE: gracePeriodExpiresDate - 364 days, never earlier than today's date.
            long paymentDate = effective.longValue() - PlayProducts.PAYMENTDATE_OFFSET_MS;
            if (subscribers.insert(new SubscriberRow(pid, txId, PlayProducts.YEARLY_LEVEL, paymentDate))) {
                result = txId.equals(otid) ? PlayClassification.NEW : PlayClassification.RENEWAL;
                players.granted(pid, tx.productId, result);
            } else {
                SubscriberRow existing = subscribers.findByTransactionId(txId);
                if (existing == null || existing.pid != pid) {
                    log4j.warn("AppleEntitlementService: transaction " + txId + " is recorded for another pid than " + pid);
                    return PlayClassification.SHARED;
                }
                result = PlayClassification.KNOWN;
                if (existing.level != 0 && paymentDate > existing.paymentDateMs) {
                    // a grace extension, or the re-assert after the legacy poller overwrote paymentdate (§4.8)
                    subscribers.updatePaymentDate(txId, paymentDate);
                    players.entitlementChanged(pid);
                }
            }
        } else if ((AppleStates.EXPIRED.equals(state) || AppleStates.REVOKED.equals(state)) && effective != null) {
            clamp(pid, txId, effective.longValue(), now);
        }

        AppleRow row = new AppleRow();
        row.originalTransactionId = otid;
        row.pid = pid;
        row.productId = tx.productId;
        row.latestTransactionId = txId;
        row.expiryTimeMs = effective;
        row.state = state;
        row.environment = env;
        row.tokenStamped = (prior != null && prior.tokenStamped) || tx.appAccountToken != null;
        if (prior == null || !row.sameContent(prior)) {
            store.upsert(row);
            if (moving) {
                // alert once, when the row actually moves: a sync that ends SHARED above never gets here
                log4j.warn("AppleEntitlementService: subscription " + otid + " moved from pid " + prior.pid + " to " + pid);
                players.adminAlert("App Store subscription " + otid + " moved from pid " + prior.pid + " to pid " + pid
                        + " (appAccountToken of the latest transaction)");
            }
        }
        if (!row.tokenStamped) {
            stamp(env, otid, pid);
        }
        return result;
    }

    /** §4.5 step 5: access ends at Apple's end (expiry or revocation), not at the year boundary */
    private void clamp(long pid, String txId, long endMs, long now) throws PlayStoreException {
        SubscriberRow existing = subscribers.findByTransactionId(txId);
        if (existing == null || existing.pid != pid || existing.level == 0) {
            return;
        }
        Long implied = SubscriberRule.impliedExpiryMs(existing.level, existing.paymentDateMs);
        if (implied != null && endMs < implied.longValue() - CLAMP_SLACK_MS) {
            long clamped = endMs - PlayProducts.PAYMENTDATE_OFFSET_MS;
            MonthPassStack.restack(subscribers, pid, txId, existing.level, clamped, now);
            subscribers.updatePaymentDate(txId, clamped);
            players.entitlementChanged(pid);
        }
    }

    /** §4.3: write our token onto a subscription that has none, once; a failure is retried at the next sync */
    private void stamp(AppleEnvironment env, String originalTransactionId, long pid) throws PlayStoreException {
        try {
            api.setAppAccountToken(env, originalTransactionId, tokens.create(pid));
            store.setTokenStamped(originalTransactionId);
            log4j.info("AppleEntitlementService: stamped appAccountToken of pid " + pid + " on " + originalTransactionId);
        } catch (AppleApiException e) {
            log4j.warn("AppleEntitlementService: setAppAccountToken failed for " + originalTransactionId
                    + ", retried at the next sync: " + e);
        }
    }

    // ---------------------------------------------------------------- consumables (§4.5)

    /**
     * Grant one consumable transaction to pid exactly once, in a fixed order: the dsg_subscribers gate row
     * (transactionid = transactionId), the dsg_subscribers_apple row in GRANTING, the grant, CONSUMED. A month pass
     * is granted by its gate row itself (MONTH_LEVEL, starting where the player's current access ends), so only
     * vacation days can be left half-done; the daily poller alerts on GRANTING rows older than 1 hour.
     */
    public PlayClassification syncOneTime(AppleTransaction tx, long pid) throws PlayStoreException {
        if (tx == null || !AppleProducts.isOneTime(tx.productId)) {
            return PlayClassification.INVALID;
        }
        if (tx.familyShared) {
            log4j.info("AppleEntitlementService: Family Sharing transaction " + tx.transactionId + " not granted");
            return PlayClassification.INVALID;
        }
        if (tx.revocationDateMs != null) {
            revoke(tx, Long.valueOf(pid));
            return PlayClassification.INVALID;
        }
        if (!sandboxAllowed(tx.environment, pid)) {
            return PlayClassification.INVALID;
        }
        synchronized (EntitlementLocks.lockFor(pid)) {
            return syncOneTimeLocked(tx, pid);
        }
    }

    private PlayClassification syncOneTimeLocked(AppleTransaction tx, long pid) throws PlayStoreException {
        long now = clock.getAsLong();
        String txId = tx.transactionId;
        int days = AppleProducts.vacationDays(tx.productId);
        int level = AppleProducts.level(tx.productId);
        long paymentDate = now;
        if (days == 0) {
            // month pass: stack after the current entitlement whatever its level, so it is never wasted
            Long current = players.subscriptionExpirationMs(pid);
            paymentDate = current != null && current.longValue() > now ? current.longValue() : now;
        }
        if (!subscribers.insert(new SubscriberRow(pid, txId, level, paymentDate))) {
            return alreadyGated(tx, pid);
        }
        store.upsert(consumableRow(tx, pid, AppleStates.GRANTING));
        if (days > 0) {
            if (!players.vacationAllowed(pid)) {
                // the app hides the vacation buttons during a last round (GET vacationAllowed); a purchase made
                // anyway is still granted, never lost (spec §4.5 syncOneTime step 3)
                log4j.info("AppleEntitlementService: " + tx.productId + " for pid " + pid
                        + " bought during a tourney's last round, granted anyway");
            }
            players.addVacationDays(pid, days);
        }
        store.setState(txId, AppleStates.CONSUMED);
        players.granted(pid, tx.productId, PlayClassification.NEW);
        return PlayClassification.NEW;
    }

    private static AppleRow consumableRow(AppleTransaction tx, long pid, String state) {
        AppleRow row = new AppleRow();
        row.originalTransactionId = tx.transactionId;
        row.pid = pid;
        row.productId = tx.productId;
        row.latestTransactionId = tx.transactionId;
        row.state = state;
        row.environment = tx.environment;
        row.tokenStamped = tx.appAccountToken != null;
        return row;
    }

    /**
     * The gate row already exists: KNOWN for pid's own grant, SHARED for another pid's, INVALID for a refund or a
     * tombstone. A missing apple row (a crash right after the gate insert) is written now: CONSUMED for a month
     * pass, whose gate row is the grant, GRANTING for vacation days, which may not have been added.
     */
    private PlayClassification alreadyGated(AppleTransaction tx, long pid) throws PlayStoreException {
        SubscriberRow gate = subscribers.findByTransactionId(tx.transactionId);
        AppleRow row = store.find(tx.transactionId);
        if ((row != null && AppleStates.isRevoked(row.state)) || (gate != null && gate.pid == 0)) {
            return PlayClassification.INVALID;
        }
        if (gate != null && gate.pid != pid) {
            return PlayClassification.SHARED;
        }
        if (row == null) {
            boolean vacation = AppleProducts.vacationDays(tx.productId) > 0;
            log4j.warn("AppleEntitlementService: " + tx.transactionId + " has a gate row but no apple row"
                    + (vacation ? ", vacation days unknown: GRANTING for the admin alert" : ""));
            store.upsert(consumableRow(tx, pid, vacation ? AppleStates.GRANTING : AppleStates.CONSUMED));
        }
        return PlayClassification.KNOWN;
    }

    /**
     * §4.6 ONE_TIME_CHARGE: grant when the owner resolves without a requester (a valid token, or the row the app's
     * POST already wrote); otherwise INACTIVE and the app's POST delivers it.
     */
    public PlayClassification notifyOneTime(AppleTransaction tx) throws PlayStoreException {
        if (tx == null || !AppleProducts.isOneTime(tx.productId) || tx.familyShared) {
            log4j.info("AppleEntitlementService: ONE_TIME_CHARGE not for a granted product, logged only");
            return PlayClassification.INVALID;
        }
        Long pid = null;
        if (tx.appAccountToken != null) {
            pid = tokens.parse(tx.appAccountToken);
            if (pid == null) {
                log4j.warn("AppleEntitlementService: rejected appAccountToken on " + tx.transactionId);
                return PlayClassification.INVALID;
            }
        } else {
            AppleRow row = store.find(tx.transactionId);
            if (row != null) {
                pid = Long.valueOf(row.pid);
            }
        }
        if (pid == null) {
            log4j.info("AppleEntitlementService: no owner for " + tx.transactionId + ", left to the app's POST");
            return PlayClassification.INACTIVE;
        }
        return syncOneTime(tx, pid.longValue());
    }

    // ---------------------------------------------------------------- revoke (§4.5)

    /**
     * A refunded or revoked transaction: level 0 on its dsg_subscribers row, month passes stacked after it
     * restacked, and its dsg_subscribers_apple row set to REVOKED (subscription) or REFUNDED (consumable).
     * Vacation days already added are not clawed back. A repeat revoke does nothing.
     *
     * @param pid the owner when known (a token, a row), else null
     */
    public void revoke(AppleTransaction revoked, Long pid) throws PlayStoreException {
        SubscriberRow current = subscribers.findByTransactionId(revoked.transactionId);
        long lockPid = current != null ? current.pid : (pid != null ? pid.longValue() : 0L);
        synchronized (EntitlementLocks.lockFor(lockPid)) {
            revokeLocked(revoked, lockPid, clock.getAsLong());
        }
    }

    private void revokeLocked(AppleTransaction revoked, long pid, long now) throws PlayStoreException {
        String txId = revoked.transactionId;
        boolean subscription = AppleProducts.isSubscription(revoked.productId);
        String revokedState = subscription ? AppleStates.REVOKED : AppleStates.REFUNDED;
        String key = subscription ? revoked.originalTransactionId : txId;
        SubscriberRow current = subscribers.findByTransactionId(txId);
        if (current == null && !subscribers.insert(new SubscriberRow(pid, txId, 0, now))) {
            // a grant holding another pid's lock inserted the gate row after the read above: revoke that row
            current = subscribers.findByTransactionId(txId);
        }
        if (current == null) {
            // tombstone (inserted just above): a level-0 row blocks a later grant from this path (the gate insert
            // fails) and from the legacy poller (hasiOSTransactionId is true)
            AppleRow row = store.find(key);
            if (row == null) {
                AppleRow tombstone = new AppleRow();
                tombstone.originalTransactionId = key;
                tombstone.pid = pid;
                tombstone.productId = revoked.productId;
                // a consumable tombstone has no latest transaction: it marks a purchase that was never granted
                tombstone.latestTransactionId = subscription ? txId : null;
                tombstone.expiryTimeMs = revoked.revocationDateMs;
                tombstone.state = revokedState;
                tombstone.environment = revoked.environment;
                tombstone.tokenStamped = revoked.appAccountToken != null;
                store.upsert(tombstone);
            } else if (!revokedState.equals(row.state) && (!subscription || txId.equals(row.latestTransactionId))) {
                store.setState(key, revokedState);
            }
            log4j.info("AppleEntitlementService: tombstone for revoked transaction " + txId + " (pid " + pid + ")");
            return;
        }
        boolean changed = false;
        if (current.level != 0) {
            // before setLevelZero, so a crash in between is redone by the repeat revoke
            MonthPassStack.restack(subscribers, current.pid, txId, 0, current.paymentDateMs, now);
            subscribers.setLevelZero(txId);
            changed = true;
        }
        AppleRow row = store.find(key);
        // a subscription row follows its latest transaction: an older year's refund leaves the chain's state alone
        if (row != null && !revokedState.equals(row.state) && (!subscription || txId.equals(row.latestTransactionId))) {
            store.setState(key, revokedState);
        }
        if (changed) {
            players.entitlementChanged(current.pid);
        }
    }

    // ---------------------------------------------------------------- refunds (§4.5)

    /**
     * REFUND, REVOKE and REFUND_REVERSED, state-based: re-read the transaction and revoke it when Apple shows a
     * revocationDate, or put it back when Apple does not and our tables say revoked. Then a subscription chain is
     * re-synced. Acting on Apple's current state makes the arrival order of notifications irrelevant.
     */
    public void refreshRefundState(String transactionId, AppleEnvironment env)
            throws AppleApiException, PlayStoreException {
        AppleTransaction tx = api.transactionInfo(env, transactionId);
        if (!AppleProducts.isKnown(tx.productId)) {
            log4j.info("AppleEntitlementService: refund state of " + transactionId + " (" + tx.productId + ") ignored");
            return;
        }
        if (tx.familyShared) {
            // a family member's transaction was never granted: nothing to revoke or restore
            log4j.info("AppleEntitlementService: refund state of Family Sharing transaction " + transactionId + " ignored");
            return;
        }
        Long owner = refundOwner(tx);
        if (env == AppleEnvironment.SANDBOX && (owner == null || !sandboxAllowed(env, owner.longValue()))) {
            log4j.info("AppleEntitlementService: sandbox refund state of " + transactionId + " ignored");
            return;
        }
        if (tx.revocationDateMs != null) {
            revoke(tx, owner);
        } else {
            unrevoke(tx);
        }
        if (AppleProducts.isSubscription(tx.productId)) {
            syncSubscription(tx.originalTransactionId, env, null);
        }
    }

    /** the owner of a refunded transaction: its dsg_subscribers row, else its token, else its apple row; or null */
    private Long refundOwner(AppleTransaction tx) throws PlayStoreException {
        SubscriberRow r = subscribers.findByTransactionId(tx.transactionId);
        if (r != null && r.pid != 0) {
            return Long.valueOf(r.pid);
        }
        if (tx.appAccountToken != null) {
            Long pid = tokens.parse(tx.appAccountToken);
            if (pid != null) {
                return pid;
            }
        }
        boolean subscription = AppleProducts.isSubscription(tx.productId);
        AppleRow row = store.find(subscription ? tx.originalTransactionId : tx.transactionId);
        return row == null || row.pid == 0 ? null : Long.valueOf(row.pid);
    }

    /** Apple no longer shows a revocation: restore the level, or grant a tombstoned purchase that was never granted */
    private void unrevoke(AppleTransaction tx) throws PlayStoreException {
        SubscriberRow seen = subscribers.findByTransactionId(tx.transactionId);
        if (seen == null || !revokedHere(tx, seen)) {
            return;
        }
        if (seen.pid == 0) {
            players.adminAlert("App Store refund reversed for " + tx.productId + " transaction " + tx.transactionId
                    + ", which was refunded before any player claimed it: grant it by hand once the owner is known");
            return;
        }
        synchronized (EntitlementLocks.lockFor(seen.pid)) {
            // re-read under the lock: a concurrent reversal (an Apple retry overlapping the daily replay) may have
            // restored it already, and the second one must then change nothing
            SubscriberRow current = subscribers.findByTransactionId(tx.transactionId);
            if (current == null || current.pid != seen.pid || !revokedHere(tx, current)) {
                return;
            }
            boolean subscription = AppleProducts.isSubscription(tx.productId);
            String key = subscription ? tx.originalTransactionId : tx.transactionId;
            AppleRow row = store.find(key);
            long now = clock.getAsLong();
            if (!subscription && row != null && row.latestTransactionId == null) {
                grantTombstoned(tx, current.pid, now);
                return;
            }
            int level = AppleProducts.level(tx.productId);
            if (level != 0 && current.level == 0) {
                long paymentDate = current.paymentDateMs;
                if (subscription && tx.expiresDateMs != null) {
                    // a subscription tombstone is dated at the refund: restore Apple's year, never a fresh one
                    paymentDate = tx.expiresDateMs.longValue() - PlayProducts.PAYMENTDATE_OFFSET_MS;
                    subscribers.updatePaymentDate(tx.transactionId, paymentDate);
                }
                subscribers.setLevel(tx.transactionId, level);
                Long end = SubscriberRule.impliedExpiryMs(level, paymentDate);
                if (end != null) {
                    pushPassesAfter(current.pid, tx.transactionId, paymentDate, end.longValue(), now);
                }
            }
            if (!subscription && row != null && AppleStates.isRevoked(row.state)) {
                store.setState(key, AppleStates.CONSUMED);
            }
            players.entitlementChanged(current.pid);
            log4j.info("AppleEntitlementService: refund of " + tx.transactionId + " reversed for pid " + current.pid);
        }
    }

    /** our tables still show tx revoked: its apple row is in a revoked state, or its level is zeroed */
    private boolean revokedHere(AppleTransaction tx, SubscriberRow current) throws PlayStoreException {
        boolean subscription = AppleProducts.isSubscription(tx.productId);
        AppleRow row = store.find(subscription ? tx.originalTransactionId : tx.transactionId);
        boolean revokedRow = row != null && AppleStates.isRevoked(row.state)
                && (!subscription || tx.transactionId.equals(row.latestTransactionId));
        return revokedRow || (AppleProducts.level(tx.productId) != 0 && current.level == 0);
    }

    /**
     * The refund pulled the pid's later month passes back (revokeLocked's restack); now that transactionId's level
     * is restored, move the passes that start at or after its start, and have not run out, to after its end again,
     * keeping their order and the time each has left.
     */
    private void pushPassesAfter(long pid, String transactionId, long restoredStartMs, long restoredEndMs, long now)
            throws PlayStoreException {
        List<SubscriberRow> passes = new java.util.ArrayList<SubscriberRow>();
        long first = Long.MAX_VALUE;
        for (SubscriberRow r : subscribers.findByPid(pid)) {
            if (transactionId.equals(r.transactionId) || r.transactionId == null
                    || (r.level & org.pente.gameServer.core.MySQLDSGPlayerStorer.ONEMONTH) == 0
                    || r.paymentDateMs < restoredStartMs) {
                continue;
            }
            Long end = SubscriberRule.impliedExpiryMs(r.level, r.paymentDateMs);
            if (end == null || end.longValue() <= now) {
                continue;
            }
            passes.add(r);
            first = Math.min(first, Math.max(now, r.paymentDateMs));
        }
        if (passes.isEmpty() || restoredEndMs <= first) {
            return;
        }
        long shift = restoredEndMs - first;
        for (SubscriberRow r : passes) {
            subscribers.updatePaymentDate(r.transactionId, r.paymentDateMs + shift);
        }
    }

    /** a consumable refunded before it was granted, now reversed: grant it, in syncOneTime's order */
    private void grantTombstoned(AppleTransaction tx, long pid, long now) throws PlayStoreException {
        int days = AppleProducts.vacationDays(tx.productId);
        if (days == 0) {
            Long currentEnd = players.subscriptionExpirationMs(pid);
            long paymentDate = currentEnd != null && currentEnd.longValue() > now ? currentEnd.longValue() : now;
            subscribers.updatePaymentDate(tx.transactionId, paymentDate);
            subscribers.setLevel(tx.transactionId, AppleProducts.level(tx.productId));
        }
        store.upsert(consumableRow(tx, pid, AppleStates.GRANTING));
        if (days > 0) {
            players.addVacationDays(pid, days);
        }
        store.setState(tx.transactionId, AppleStates.CONSUMED);
        players.granted(pid, tx.productId, PlayClassification.NEW);
    }
}
