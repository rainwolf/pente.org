package org.pente.subscriptions.play.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayerGateway;
import org.pente.subscriptions.play.SubscriberRule;

/**
 * Player side effects recorded in memory. subscriptionExpirationMs applies the getSubscriberData
 * rule to the fake dsg_subscribers rows. sleepMs widens race windows for the concurrency tests.
 */
public class FakePlayerGateway implements PlayerGateway {

    private final FakeSubscriberStore subscribers;
    private final LongSupplier clock;

    /** pids in the last round of a turn-based tourney with incremental time */
    public final Set<Long> inTourney = new HashSet<Long>();
    public final Map<Long, Integer> vacationDays = new HashMap<Long, Integer>();
    /** "pid:productId:CLASSIFICATION" per granted() call */
    public final List<String> grants = new ArrayList<String>();
    public final List<Long> changed = new ArrayList<Long>();
    public final List<String> alerts = new ArrayList<String>();
    public volatile long sleepMs;

    public FakePlayerGateway(FakeSubscriberStore subscribers, LongSupplier clock) {
        this.subscribers = subscribers;
        this.clock = clock;
    }

    private void pause() {
        if (sleepMs > 0) {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public Long subscriptionExpirationMs(long pid) {
        Long expiration = SubscriberRule.expirationMs(subscribers.rowsOf(pid), clock.getAsLong());
        pause();
        return expiration;
    }

    public boolean vacationAllowed(long pid) {
        pause();
        synchronized (this) {
            return !inTourney.contains(Long.valueOf(pid));
        }
    }

    public void addVacationDays(long pid, int days) {
        pause();
        synchronized (this) {
            Integer current = vacationDays.get(Long.valueOf(pid));
            vacationDays.put(Long.valueOf(pid), Integer.valueOf((current == null ? 0 : current.intValue()) + days));
        }
    }

    public synchronized void granted(long pid, String productId, PlayClassification classification) {
        grants.add(pid + ":" + productId + ":" + classification.name());
    }

    public synchronized void entitlementChanged(long pid) {
        changed.add(Long.valueOf(pid));
    }

    public synchronized void adminAlert(String message) {
        alerts.add(message);
    }

    /** test helper: vacation days added for pid */
    public synchronized int vacationDaysOf(long pid) {
        Integer d = vacationDays.get(Long.valueOf(pid));
        return d == null ? 0 : d.intValue();
    }
}
