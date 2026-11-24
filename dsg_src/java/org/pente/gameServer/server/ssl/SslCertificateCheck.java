package org.pente.gameServer.server.ssl;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.apache.log4j.Category;

/** Daily check: reloads listeners serving an older certificate than the one on disk and notifies admins. */
public final class SslCertificateCheck implements Runnable {
    private static final Category log = Category.getInstance(SslCertificateCheck.class.getName());

    static final int ALERT_DAYS = 45;

    private final Callable<X509Certificate> diskCert;
    private final List<SslListener> listeners;
    private final Consumer<String> notifier;
    private final Clock clock;

    public SslCertificateCheck(Callable<X509Certificate> diskCert, List<SslListener> listeners,
                               Consumer<String> notifier, Clock clock) {
        this.diskCert = diskCert;
        this.listeners = listeners;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Override
    public void run() {
        try {
            check();
        } catch (Throwable t) {
            fail("SSL certificate check failed: " + t, t);
        }
    }

    private void check() {
        X509Certificate disk;
        try {
            disk = diskCert.call();
        } catch (Throwable e) {
            fail("SSL certificate check failed: " + e, e);
            return;
        }
        Date diskEnd = disk.getNotAfter();
        log.info("SSL certificate check: disk cert valid until " + diskEnd);
        boolean reloaded = false;
        List<String> behind = new ArrayList<>();
        List<Date> ends = new ArrayList<>();

        for (SslListener l : listeners) {
            boolean attempted = false;
            try {
                X509Certificate served = l.served();
                Date end = served.getNotAfter();
                ends.add(end);
                if (end.before(diskEnd)) {
                    reloaded = true;
                    attempted = true;
                    log.info("SSL certificate check: " + l.name() + " serves " + end + ", reloading");
                    l.reload();
                    X509Certificate after = l.served();
                    Date afterEnd = after.getNotAfter();
                    if (afterEnd.before(diskEnd)) behind.add(l.name());
                    if (afterEnd.after(end)) ends.set(ends.size() - 1, afterEnd);
                }
            } catch (Throwable e) {
                if (attempted) behind.add(l.name());
                fail("SSL certificate check failed for " + l.name() + ": " + e, e);
            }
        }

        if (reloaded) {
            if (behind.isEmpty()) {
                notify("SSL certificate renewed, valid until " + day(diskEnd));
            } else {
                notify("SSL certificate reload failed for: " + String.join(", ", behind)
                        + ", disk cert valid until " + day(diskEnd));
            }
        }

        Date minServed = null;
        for (Date d : ends) if (minServed == null || d.before(minServed)) minServed = d;
        if (minServed != null) {
            long days = Math.floorDiv(Duration.between(Instant.now(clock), minServed.toInstant()).toMillis(),
                    Duration.ofDays(1).toMillis());
            if (days < ALERT_DAYS) {
                notify("SSL certificate expires in " + days + " days (" + day(minServed)
                        + "), renewal is not working");
            }
        }
    }

    private void notify(String message) {
        log.info("SSL certificate check: admin notification: " + message);
        notifier.accept(message);
    }

    private void fail(String message, Throwable t) {
        log.error(message, t);
        try {
            notifier.accept(message);
        } catch (Throwable ignored) {
            // run() must never throw
        }
    }

    private static String day(Date d) {
        return d.toInstant().atZone(ZoneOffset.UTC).toLocalDate().toString();
    }
}
