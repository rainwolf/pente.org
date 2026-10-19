package org.pente.gameServer.kids.test;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.pente.gameServer.kids.CacheKidStorer;
import org.pente.gameServer.kids.KidDataChangeListener;
import org.pente.gameServer.kids.KidSettings;
import org.pente.gameServer.server.RedisConnectionManager;
import org.pente.turnBased.test.SerializingRedisConnectionManager;

public class CacheKidStorerRedisTest extends TestCase {

    private SerializingRedisConnectionManager redis;
    private InMemoryKidStorer base;
    private CacheKidStorer cache;
    private List<Long> notified;

    public CacheKidStorerRedisTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        redis = new SerializingRedisConnectionManager();
        RedisConnectionManager.setInstance(redis);
        base = new InMemoryKidStorer();
        cache = new CacheKidStorer(base);
        notified = new ArrayList<Long>();
        cache.addKidDataChangeListener(new KidDataChangeListener() {
            public void kidDataChanged(long kidPid) {
                notified.add(kidPid);
            }
        });
    }

    protected void tearDown() throws Exception {
        RedisConnectionManager.resetInstance();
    }

    public void testWhitelistReadIsCached() throws Exception {
        cache.getWhitelist(10);
        cache.getWhitelist(10);
        assertEquals(1, base.whitelistReads);
    }

    public void testAddInvalidatesAndNotifies() throws Exception {
        cache.getWhitelist(10);
        cache.addToWhitelist(10, 20);
        assertTrue(cache.getWhitelist(10).contains(20L));
        assertEquals(1, notified.size());
        assertEquals(10L, (long) notified.get(0));
    }

    public void testRemoveInvalidatesAndNotifies() throws Exception {
        cache.addToWhitelist(10, 20);
        notified.clear();
        assertTrue(cache.getWhitelist(10).contains(20L));
        cache.removeFromWhitelist(10, 20);
        assertTrue(!cache.getWhitelist(10).contains(20L));
        assertEquals(1, notified.size());
        assertEquals(10L, (long) notified.get(0));
    }

    public void testSettingsDefaultOff() throws Exception {
        KidSettings s = cache.getSettings(10);
        assertTrue(!s.isCopyMessages());
        assertTrue(!s.isCopyLiveChat());
    }

    public void testStoreSettingsInvalidatesAndNotifies() throws Exception {
        cache.getSettings(10);
        cache.storeSettings(new KidSettings(10, true, false));
        KidSettings s = cache.getSettings(10);
        assertTrue(s.isCopyMessages());
        assertTrue(!s.isCopyLiveChat());
        assertEquals(1, notified.size());
        assertEquals(10L, (long) notified.get(0));
    }

    public void testDeleteKidDataClearsBoth() throws Exception {
        cache.addToWhitelist(10, 20);
        cache.storeSettings(new KidSettings(10, true, true));
        cache.getWhitelist(10);
        cache.getSettings(10);
        notified.clear();
        cache.deleteKidData(10);
        assertTrue(cache.getWhitelist(10).isEmpty());
        assertTrue(!cache.getSettings(10).isCopyMessages());
        assertTrue(!cache.getSettings(10).isCopyLiveChat());
        assertEquals(1, notified.size());
        assertEquals(10L, (long) notified.get(0));
    }

    public void testRedisDownFallsThroughToBase() throws Exception {
        redis.setAvailable(false);
        cache.addToWhitelist(10, 20);
        assertTrue(cache.getWhitelist(10).contains(20L));
        cache.getWhitelist(10);
        assertEquals(2, base.whitelistReads);
    }

    public void testThrowingListenerDoesNotStopOthers() throws Exception {
        CacheKidStorer c = new CacheKidStorer(base);
        final List<Long> got = new ArrayList<Long>();
        c.addKidDataChangeListener(new KidDataChangeListener() {
            public void kidDataChanged(long kidPid) {
                throw new RuntimeException("boom (test)");
            }
        });
        c.addKidDataChangeListener(new KidDataChangeListener() {
            public void kidDataChanged(long kidPid) {
                got.add(kidPid);
            }
        });
        c.addToWhitelist(10, 20);
        assertEquals(1, got.size());
        assertEquals(10L, (long) got.get(0));
    }

    public void testSetParentPidPassesThroughWithoutNotify() throws Exception {
        cache.setParentPid(10, 1L);
        assertEquals(1L, (long) base.parentPids.get(10L));
        assertTrue(notified.isEmpty());
    }
}
