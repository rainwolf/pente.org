package org.pente.gameServer.server.test;

import junit.framework.TestCase;

import org.pente.gameServer.server.AppClient;

public class AppClientTest extends TestCase {

    public AppClientTest(String name) {
        super(name);
    }

    public void testAndroidAppAgent() {
        assertTrue(AppClient.isApp("Mozilla/5.0 (Linux; Android 14) ... Chrome/130 Mobile Safari/537.36 penteLive-Android"));
    }

    public void testIosAppAgent() {
        assertTrue(AppClient.isApp("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 penteLive-iOS"));
    }

    public void testDesktopAgent() {
        assertTrue(!AppClient.isApp("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15"));
    }

    public void testMobileSafariIsNotApp() {
        assertTrue(!AppClient.isApp("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Version/18.0 Mobile/15E148 Safari/604.1"));
    }

    public void testNull() {
        assertTrue(!AppClient.isApp(null));
    }
}
