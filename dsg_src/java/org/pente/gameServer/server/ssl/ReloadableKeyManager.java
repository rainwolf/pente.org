package org.pente.gameServer.server.ssl;

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

/** Key manager whose certificate can be swapped at runtime; established connections are unaffected. */
public class ReloadableKeyManager extends X509ExtendedKeyManager {
    private final AtomicReference<X509ExtendedKeyManager> delegate = new AtomicReference<>();
    private volatile X509Certificate current;

    public ReloadableKeyManager(KeyStore ks, char[] pw) throws GeneralSecurityException {
        reload(ks, pw);
    }

    public void reload(KeyStore ks, char[] pw) throws GeneralSecurityException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, pw);
        X509ExtendedKeyManager inner = null;
        for (KeyManager km : kmf.getKeyManagers()) {
            if (km instanceof X509ExtendedKeyManager) { inner = (X509ExtendedKeyManager) km; break; }
        }
        if (inner == null) throw new GeneralSecurityException("no X509ExtendedKeyManager available");
        X509Certificate leaf = SslKeystore.leaf(ks);
        if (leaf == null) throw new GeneralSecurityException("no certificate under alias " + SslKeystore.ALIAS);
        current = leaf;
        delegate.set(inner);
    }

    public X509Certificate current() { return current; }

    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return delegate.get().getClientAliases(keyType, issuers);
    }
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
        return delegate.get().chooseClientAlias(keyType, issuers, socket);
    }
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return delegate.get().getServerAliases(keyType, issuers);
    }
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        return delegate.get().chooseServerAlias(keyType, issuers, socket);
    }
    public X509Certificate[] getCertificateChain(String alias) {
        return delegate.get().getCertificateChain(alias);
    }
    public PrivateKey getPrivateKey(String alias) {
        return delegate.get().getPrivateKey(alias);
    }
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
        return delegate.get().chooseEngineClientAlias(keyType, issuers, engine);
    }
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
        return delegate.get().chooseEngineServerAlias(keyType, issuers, engine);
    }
}
