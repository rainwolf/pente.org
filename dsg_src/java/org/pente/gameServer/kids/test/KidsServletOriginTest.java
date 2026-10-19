package org.pente.gameServer.kids.test;

import junit.framework.TestCase;

import org.pente.gameServer.kids.KidsServlet;

public class KidsServletOriginTest extends TestCase {

    public KidsServletOriginTest(String name) {
        super(name);
    }

    public void testSameHostOriginAllowed() {
        assertTrue(KidsServlet.sameOrigin("https://localhost", null, "localhost"));
        assertTrue(KidsServlet.sameOrigin("https://localhost:8443", "https://evil.example/x", "localhost"));
    }

    public void testCrossOriginRefused() {
        assertTrue(!KidsServlet.sameOrigin("https://evil.example", null, "localhost"));
        assertTrue(!KidsServlet.sameOrigin("https://evil.example",
                "https://localhost/gameServer/myprofile/kids", "localhost"));
        assertTrue(!KidsServlet.sameOrigin("null", null, "localhost"));
    }

    public void testRefererUsedWhenNoOrigin() {
        assertTrue(KidsServlet.sameOrigin(null,
                "https://localhost/gameServer/myprofile/kids", "localhost"));
        assertTrue(!KidsServlet.sameOrigin(null, "https://evil.example/page", "localhost"));
        assertTrue(!KidsServlet.sameOrigin(null, "not a uri ^", "localhost"));
    }

    public void testNoHeadersRefused() {
        assertTrue(!KidsServlet.sameOrigin(null, null, "localhost"));
        assertTrue(!KidsServlet.sameOrigin("", "", "localhost"));
    }

    // paramsAuthenticate(name2, encryptedPassword2, loggedInName, storedEncryptedPassword)

    public void testMatchingParamsAuthenticate() {
        assertTrue(KidsServlet.paramsAuthenticate("parent", "ENC", "parent", "ENC"));
        assertTrue(KidsServlet.paramsAuthenticate(" Parent ", "ENC", "parent", "ENC"));
    }

    public void testDummyParamsDoNotAuthenticate() {
        // a cross-site form riding the session with dummy name2/password2
        assertTrue(!KidsServlet.paramsAuthenticate("x", "ENCX", "parent", "ENC"));
        assertTrue(!KidsServlet.paramsAuthenticate("", "", "parent", "ENC"));
        assertTrue(!KidsServlet.paramsAuthenticate("parent", "ENCX", "parent", "ENC"));
        assertTrue(!KidsServlet.paramsAuthenticate("other", "ENC", "parent", "ENC"));
    }

    public void testMissingValuesDoNotAuthenticate() {
        assertTrue(!KidsServlet.paramsAuthenticate(null, "ENC", "parent", "ENC"));
        assertTrue(!KidsServlet.paramsAuthenticate("parent", null, "parent", "ENC"));
        assertTrue(!KidsServlet.paramsAuthenticate("parent", "ENC", null, "ENC"));
        assertTrue(!KidsServlet.paramsAuthenticate("parent", null, "parent", null));
        assertTrue(!KidsServlet.paramsAuthenticate("parent", "", "parent", ""));
    }
}
