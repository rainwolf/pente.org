package org.pente.gameServer.server.ssl;

import java.io.IOException;
import java.security.GeneralSecurityException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

/** Shared TLS context for the game server's raw sockets, backed by one reloadable key manager. */
public final class GameSsl {
    private static ReloadableKeyManager keyManager;
    private static SSLServerSocketFactory factory;

    private GameSsl() {}

    public static synchronized SSLServerSocketFactory serverSocketFactory() throws IOException, GeneralSecurityException {
        if (factory == null) {
            factory = newContext(keyManager()).getServerSocketFactory();
        }
        return factory;
    }

    public static synchronized ReloadableKeyManager keyManager() {
        if (keyManager == null) {
            try {
                char[] pw = SslKeystore.password();
                keyManager = new ReloadableKeyManager(SslKeystore.load(SslKeystore.PATH, pw), pw);
            } catch (IOException | GeneralSecurityException e) {
                throw new IllegalStateException("cannot load " + SslKeystore.PATH, e);
            }
        }
        return keyManager;
    }

    public static SSLContext newContext(ReloadableKeyManager km) throws GeneralSecurityException {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(new KeyManager[] { km }, null, null);
        return ctx;
    }
}
