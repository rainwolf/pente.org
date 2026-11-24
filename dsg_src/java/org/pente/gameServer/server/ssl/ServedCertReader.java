package org.pente.gameServer.server.ssl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Reads the leaf certificate a TLS endpoint currently serves. Trust-all; sends no application data. */
public final class ServedCertReader {
    private ServedCertReader() {}

    public static X509Certificate read(String host, int port, String sniHost, int timeoutMillis) throws IOException {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] { new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String t) {}
                public void checkServerTrusted(X509Certificate[] c, String t) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            } }, null);
            try (Socket raw = new Socket()) {
                raw.connect(new InetSocketAddress(host, port), timeoutMillis);
                raw.setSoTimeout(timeoutMillis);
                try (SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket(raw, host, port, true)) {
                    SSLParameters p = s.getSSLParameters();
                    if (sniHost != null) p.setServerNames(List.<SNIServerName>of(new SNIHostName(sniHost)));
                    s.setSSLParameters(p);
                    s.startHandshake();
                    Certificate[] chain = s.getSession().getPeerCertificates();
                    return (X509Certificate) chain[0];
                }
            }
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }
}
