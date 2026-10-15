package org.pente.subscriptions.apple;

import org.apache.log4j.Category;

/**
 * The App Store pieces the servlets and the CacheDSGPlayerStorer timers use, as one ServletContext attribute
 * (spec 2026-10-15 §4.6, §4.7). sweep and daily never throw, so a failure never kills a Timer thread.
 */
public class AppleBilling {

    private static final Category log4j = Category.getInstance(AppleBilling.class.getName());

    public final AppleEntitlementService service;
    public final AppleNotificationHandler notifications;
    public final AppleHistoryReplay history;

    public AppleBilling(AppleEntitlementService service, AppleNotificationHandler notifications, AppleHistoryReplay history) {
        this.service = service;
        this.notifications = notifications;
        this.history = history;
    }

    /** every 6 hours */
    public void sweep() {
        try {
            service.pollSubscriptions();
        } catch (Throwable t) {
            log4j.error("AppleBilling: subscription sweep failed", t);
        }
    }

    /** daily: the history replay per environment, then the admin alerts; one failing step never skips the others */
    public void daily() {
        for (AppleEnvironment env : AppleEnvironment.values()) {
            try {
                history.replay(env);
            } catch (Throwable t) {
                log4j.error("AppleBilling: " + env + " history replay failed, retried tomorrow", t);
            }
        }
        try {
            service.dailyAlerts();
        } catch (Throwable t) {
            log4j.error("AppleBilling: daily alerts failed", t);
        }
    }
}
