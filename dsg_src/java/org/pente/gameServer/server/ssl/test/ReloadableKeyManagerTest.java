package org.pente.gameServer.server.ssl.test;

import java.io.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import junit.framework.TestCase;
import org.pente.gameServer.server.ssl.*;

public class ReloadableKeyManagerTest extends TestCase {

    private static final char[] PW = "testpw".toCharArray();
    private Path dir;
    private KeyStore a, b;
    private ReloadableKeyManager km;
    private SSLServerSocket server;

    public ReloadableKeyManagerTest(String name) { super(name); }

    protected void setUp() throws Exception {
        String tmp = System.getenv("TMPDIR");
        dir = tmp != null ? Files.createTempDirectory(Paths.get(tmp), "rkm") : Files.createTempDirectory("rkm");
        a = SslKeystore.load(TestKeystores.create(dir, "a", 1, PW), PW);
        b = SslKeystore.load(TestKeystores.create(dir, "b", 90, PW), PW);
        km = new ReloadableKeyManager(a, PW);
    }

    protected void tearDown() throws Exception {
        if (server != null) server.close();
        if (dir != null) {
            try (var s = Files.list(dir)) { for (Path p : (Iterable<Path>) s::iterator) Files.deleteIfExists(p); }
            Files.deleteIfExists(dir);
        }
    }

    public void testReloadSwapsCertificate() throws Exception {
        X509Certificate la = SslKeystore.leaf(a), lb = SslKeystore.leaf(b);
        assertEquals(la.getNotAfter(), km.current().getNotAfter());
        km.reload(b, PW);
        assertEquals(lb, km.current());
        assertEquals(lb, km.getCertificateChain("tomcat")[0]);
    }

    private int startServer(final boolean echo) throws Exception {
        server = (SSLServerSocket) GameSsl.newContext(km).getServerSocketFactory().createServerSocket(0);
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    final SSLSocket s = (SSLSocket) server.accept();
                    Thread h = new Thread(() -> {
                        try {
                            s.startHandshake();
                            if (echo) {
                                InputStream in = s.getInputStream();
                                OutputStream out = s.getOutputStream();
                                int c;
                                while ((c = in.read()) >= 0) { out.write(c); out.flush(); }
                            }
                        } catch (IOException ignored) {
                        } finally { try { s.close(); } catch (IOException ignored) {} }
                    });
                    h.setDaemon(true);
                    h.start();
                } catch (IOException e) { return; }
            }
        });
        t.setDaemon(true);
        t.start();
        return server.getLocalPort();
    }

    public void testServedCertFollowsReload() throws Exception {
        int port = startServer(false);
        assertEquals(SslKeystore.leaf(a), ServedCertReader.read("localhost", port, "pente.org", 5000));
        km.reload(b, PW);
        assertEquals(SslKeystore.leaf(b), ServedCertReader.read("localhost", port, "pente.org", 5000));
    }

    public void testOpenConnectionSurvivesReload() throws Exception {
        int port = startServer(true);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[] { new javax.net.ssl.X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String t) {}
            public void checkServerTrusted(X509Certificate[] c, String t) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        } }, null);
        try (SSLSocket c = (SSLSocket) ctx.getSocketFactory().createSocket("localhost", port)) {
            c.setSoTimeout(5000);
            c.getOutputStream().write(1); c.getOutputStream().flush();
            assertEquals(1, c.getInputStream().read());
            km.reload(b, PW);
            c.getOutputStream().write(2); c.getOutputStream().flush();
            assertEquals(2, c.getInputStream().read());
        }
    }
}
