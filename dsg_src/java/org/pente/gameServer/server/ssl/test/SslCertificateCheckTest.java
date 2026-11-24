package org.pente.gameServer.server.ssl.test;

import java.nio.file.*;
import java.security.cert.X509Certificate;
import java.time.*;
import java.util.*;
import junit.framework.TestCase;
import org.pente.gameServer.server.ssl.*;

public class SslCertificateCheckTest extends TestCase {

    private static final char[] PW = "testpw".toCharArray();
    private Path dir;
    private X509Certificate short1, long90;
    private List<String> messages;

    public SslCertificateCheckTest(String name) { super(name); }

    private static class Fake implements SslListener {
        final String name;
        final boolean reloadWorks;
        final X509Certificate disk;
        X509Certificate served;
        int reloads;
        Fake(String name, X509Certificate served, X509Certificate disk, boolean reloadWorks) {
            this.name = name; this.served = served; this.disk = disk; this.reloadWorks = reloadWorks;
        }
        public String name() { return name; }
        public X509Certificate served() { return served; }
        public void reload() { reloads++; if (reloadWorks) served = disk; }
    }

    protected void setUp() throws Exception {
        String tmp = System.getenv("TMPDIR");
        dir = tmp != null ? Files.createTempDirectory(Paths.get(tmp), "scc") : Files.createTempDirectory("scc");
        short1 = SslKeystore.leaf(SslKeystore.load(TestKeystores.create(dir, "s", 1, PW), PW));
        long90 = SslKeystore.leaf(SslKeystore.load(TestKeystores.create(dir, "l", 90, PW), PW));
        messages = new ArrayList<>();
    }

    protected void tearDown() throws Exception {
        if (dir != null) {
            try (var s = Files.list(dir)) { for (Path p : (Iterable<Path>) s::iterator) Files.deleteIfExists(p); }
            Files.deleteIfExists(dir);
        }
    }

    private Clock clockAt(Instant i) { return Clock.fixed(i, ZoneOffset.UTC); }

    private SslCertificateCheck check(X509Certificate disk, Clock clock, SslListener... ls) {
        return new SslCertificateCheck(() -> disk, Arrays.asList(ls), messages::add, clock);
    }

    private static String date(X509Certificate c) {
        return c.getNotAfter().toInstant().atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    public void testNoActionWhenCurrent() {
        Fake a = new Fake("a", long90, long90, true), b = new Fake("b", long90, long90, true);
        check(long90, clockAt(long90.getNotBefore().toInstant()), a, b).run();
        assertEquals(0, a.reloads);
        assertEquals(0, b.reloads);
        assertEquals(Collections.emptyList(), messages);
    }

    public void testReloadsAndNotifiesRenewed() {
        Fake a = new Fake("a", short1, long90, true), b = new Fake("b", short1, long90, true);
        check(long90, clockAt(long90.getNotBefore().toInstant()), a, b).run();
        assertEquals(1, a.reloads);
        assertEquals(1, b.reloads);
        assertEquals(1, messages.size());
        assertEquals("SSL certificate renewed, valid until " + date(long90), messages.get(0));
        assertTrue(messages.get(0).startsWith("SSL certificate renewed, valid until "));
    }

    public void testPartialReloadNamesListener() {
        Fake game = new Fake("game", short1, long90, false), web = new Fake("web", short1, long90, true);
        check(long90, clockAt(long90.getNotBefore().toInstant()), game, web).run();
        assertEquals(2, messages.size());
        assertEquals("SSL certificate reload failed for: game, disk cert valid until " + date(long90), messages.get(0));
        assertTrue(messages.get(1), messages.get(1).startsWith("SSL certificate expires in "));
        assertTrue(messages.get(1), messages.get(1).endsWith(" days (" + date(short1) + "), renewal is not working"));
    }

    public void testExpiryAlertAt44DaysNotAt45() {
        Fake a = new Fake("a", long90, long90, true);
        Instant end = long90.getNotAfter().toInstant();
        check(long90, clockAt(end.minus(Duration.ofDays(44))), a).run();
        assertEquals(1, messages.size());
        assertEquals("SSL certificate expires in 44 days (" + date(long90) + "), renewal is not working", messages.get(0));
        messages.clear();
        check(long90, clockAt(end.minus(Duration.ofDays(45))), a).run();
        assertEquals(Collections.emptyList(), messages);
    }

    public void testDiskReadFailureNotifies() {
        Fake a = new Fake("a", short1, long90, true);
        new SslCertificateCheck(() -> { throw new NoSuchFileException("/x"); },
                Arrays.<SslListener>asList(a), messages::add, clockAt(Instant.now())).run();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).startsWith("SSL certificate check failed: "));
        assertEquals(0, a.reloads);
    }

    public void testReloadThrowsIsNotReportedRenewed() {
        Fake bad = new Fake("bad", short1, long90, true) {
            public void reload() { reloads++; throw new IllegalStateException("boom"); }
        };
        Fake ok = new Fake("ok", short1, long90, true);
        check(long90, clockAt(long90.getNotBefore().toInstant()), bad, ok).run();
        for (String m : messages) assertTrue(m, !m.startsWith("SSL certificate renewed"));
        assertTrue(messages.toString(), messages.contains("SSL certificate check failed for bad: java.lang.IllegalStateException: boom"));
        assertTrue(messages.toString(), messages.contains("SSL certificate reload failed for: bad, disk cert valid until " + date(long90)));
        boolean alert = false;
        for (String m : messages) alert |= m.startsWith("SSL certificate expires in ") && m.contains("(" + date(short1) + ")");
        assertTrue(messages.toString(), alert);
    }
}
