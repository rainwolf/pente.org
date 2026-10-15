package org.pente.subscriptions.apple;

import java.util.function.LongSupplier;

import org.apache.log4j.Category;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStoreException;

/**
 * Daily notification-history replay (spec 2026-10-15 §4.7): Get Notification History from the high-water mark
 * minus 1 hour to now, every page, each payload not seen yet routed like a live delivery. The mark advances only
 * when every payload was handled, so a failed day is retried the next day. The first run starts 3 days back; a
 * start older than Apple's retention (180 days production, 30 sandbox) is moved inside it.
 */
public class AppleHistoryReplay {

    private static final Category log4j = Category.getInstance(AppleHistoryReplay.class.getName());

    static final long FIRST_RUN_LOOKBACK_MS = 3 * PlayProducts.DAY_MS;
    static final long OVERLAP_MS = 3600L * 1000;
    /** safety stop: 20 notifications a page */
    static final int MAX_PAGES = 500;

    private final AppleApi api;
    private final AppleVerifier verifier;
    private final AppleStore store;
    private final AppleNotificationHandler handler;
    private final LongSupplier clock;

    public AppleHistoryReplay(AppleApi api, AppleVerifier verifier, AppleStore store, AppleNotificationHandler handler,
                              LongSupplier clock) {
        this.api = api;
        this.verifier = verifier;
        this.store = store;
        this.handler = handler;
        this.clock = clock;
    }

    /** @return true when the high-water mark advanced to now */
    public boolean replay(AppleEnvironment env) throws AppleApiException, PlayStoreException {
        long now = clock.getAsLong();
        Long until = store.historyUntil(env);
        long start = until == null ? now - FIRST_RUN_LOOKBACK_MS : until.longValue() - OVERLAP_MS;
        long oldest = now - env.historyRetentionMs() + OVERLAP_MS;
        if (start < oldest) {
            log4j.warn("AppleHistoryReplay: " + env + " replay start is older than Apple keeps history; notifications"
                    + " before " + new java.util.Date(oldest) + " cannot be replayed");
            start = oldest;
        }
        boolean failed = false;
        int handled = 0;
        String token = null;
        int pages = 0;
        do {
            AppleNotificationPage page = api.notificationHistory(env, start, now, token);
            for (String payload : page.signedPayloads) {
                AppleNotification n;
                try {
                    n = verifier.verifyNotification(payload);
                } catch (AppleVerificationException e) {
                    if (e.isRetryable()) {
                        failed = true;
                    } else {
                        log4j.warn("AppleHistoryReplay: " + env + " history payload not verified, skipped: " + e.getMessage());
                    }
                    continue;
                }
                int status = handler.processForReplay(n);
                if (status == 200) {
                    handled++;
                } else if (status == AppleNotificationHandler.FINAL_FAILURE) {
                    // a retry cannot fix it: holding the mark would only replay an ever-growing window
                    handler.adminAlert("App Store " + env + " notification " + n.type + " " + n.notificationUuid
                            + " failed for good in the daily replay (see subscriptions.log); handle it by hand");
                } else {
                    failed = true;
                }
            }
            token = page.hasMore ? page.paginationToken : null;
            pages++;
        } while (token != null && pages < MAX_PAGES);
        if (token != null) {
            log4j.warn("AppleHistoryReplay: " + env + " stopped after " + MAX_PAGES + " pages");
            failed = true;
        }
        if (failed) {
            log4j.warn("AppleHistoryReplay: " + env + " replay incomplete, the high-water mark stays; retried tomorrow");
            return false;
        }
        store.setHistoryUntil(env, now);
        log4j.info("AppleHistoryReplay: " + env + " replayed " + handled + " notifications on " + pages + " pages");
        return true;
    }
}
