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
}
