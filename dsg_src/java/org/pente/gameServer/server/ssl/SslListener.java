package org.pente.gameServer.server.ssl;

import java.security.cert.X509Certificate;

/** A TLS endpoint whose served certificate can be read and reloaded from disk. */
public interface SslListener {
    String name();

    X509Certificate served() throws Exception;

    void reload() throws Exception;
}
