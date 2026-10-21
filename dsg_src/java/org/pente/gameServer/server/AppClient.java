package org.pente.gameServer.server;

public final class AppClient {

    private AppClient() {
    }

    /** True when the user agent is one of the pente.org Android or iOS app web views. */
    public static boolean isApp(String userAgent) {
        return userAgent != null
                && (userAgent.contains("penteLive-Android") || userAgent.contains("penteLive-iOS"));
    }
}
