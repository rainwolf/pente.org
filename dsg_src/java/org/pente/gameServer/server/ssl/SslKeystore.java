package org.pente.gameServer.server.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;

/** Location and loading conventions of the shared PKCS12 keystore. */
public final class SslKeystore {
    public static final Path PATH = Path.of("/etc/ssl/pente/keystore.p12");
    public static final String ALIAS = "tomcat";

    private SslKeystore() {}

    public static KeyStore load(Path file, char[] password) throws IOException, GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(file)) {
            ks.load(in, password);
        }
        return ks;
    }

    public static X509Certificate leaf(KeyStore ks) throws KeyStoreException {
        return (X509Certificate) ks.getCertificate(ALIAS);
    }

    public static char[] password() {
        String pw = System.getenv("SSL_PWD");
        if (pw == null) pw = System.getProperty("SSL_PWD");
        return pw == null ? null : pw.toCharArray();
    }
}
