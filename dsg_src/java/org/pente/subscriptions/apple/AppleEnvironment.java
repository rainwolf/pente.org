package org.pente.subscriptions.apple;

import org.pente.subscriptions.play.PlayProducts;

/**
 * The two App Store environments the server talks to (spec 2026-10-15 §4.1). The name is what
 * dsg_subscribers_apple.environment and dsg_apple_poll_state.environment store.
 */
public enum AppleEnvironment {
    PRODUCTION(180),
    SANDBOX(30);

    private final int historyRetentionDays;

    AppleEnvironment(int historyRetentionDays) {
        this.historyRetentionDays = historyRetentionDays;
    }

    /** how far back Get Notification History reaches: 180 days in production, 30 in sandbox */
    public long historyRetentionMs() {
        return historyRetentionDays * PlayProducts.DAY_MS;
    }

    /** the stored name, or PRODUCTION for null or anything unknown */
    public static AppleEnvironment of(String name) {
        return SANDBOX.name().equals(name) ? SANDBOX : PRODUCTION;
    }
}
