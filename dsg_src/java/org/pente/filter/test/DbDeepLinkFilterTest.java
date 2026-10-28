package org.pente.filter.test;

import junit.framework.TestCase;
import org.pente.filter.DbDeepLinkFilter;

public class DbDeepLinkFilterTest extends TestCase {

    public DbDeepLinkFilterTest(String name) {
        super(name);
    }

    private static final String INDEX = "/gameServer/db/index.html";

    public void testRoutesForwardToIndex() {
        assertEquals(INDEX, DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db/p/1/180"));
        assertEquals(INDEX, DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db/p/1/180-199-210"));
        assertEquals(INDEX, DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db/g/12345"));
        assertEquals(INDEX, DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db/w/77"));
        assertEquals(INDEX, DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db/a/9"));
        assertEquals(INDEX, DbDeepLinkFilter.forwardTarget("HEAD", "/gameServer/db/g/1"));
    }

    public void testAssetsAndRootPassThrough() {
        assertNull(DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db/"));
        assertNull(DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db/index.html"));
        assertNull(DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db/static/js/index.abc123.js"));
        assertNull(DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db/static/css/index.css"));
        assertNull(DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db/ai/ai.wasm"));
        assertNull(DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db/ai/ai.js"));
        // a missing asset-dir path must 404, not return HTML
        assertNull(DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db/static/missing"));
        assertNull(DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db/favicon.ico"));
    }

    public void testOtherPathsAndMethodsPassThrough() {
        assertNull(DbDeepLinkFilter.forwardTarget("GET", "/api/db/search"));
        assertNull(DbDeepLinkFilter.forwardTarget("GET", "/gameServer/live/x"));
        assertNull(DbDeepLinkFilter.forwardTarget("GET", "/gameServer/dbfoo/x"));
        assertNull(DbDeepLinkFilter.forwardTarget("GET", "/gameServer/db"));
        assertNull(DbDeepLinkFilter.forwardTarget("POST", "/gameServer/db/g/1"));
        assertNull(DbDeepLinkFilter.forwardTarget(null, "/gameServer/db/g/1"));
    }
}
