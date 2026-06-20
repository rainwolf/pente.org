package org.pente.gameServer.core.test;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.pente.gameServer.core.CacheDSGFollowerStorer;
import org.pente.gameServer.core.DSGFollowerStoreException;
import org.pente.gameServer.core.MySQLDSGFollowerStorer;
import org.pente.gameServer.server.RedisConnectionManager;
import org.pente.turnBased.test.SerializingRedisConnectionManager;

/**
 * Regression tests for {@link CacheDSGFollowerStorer}'s Redis-backed
 * follower/following lists, against a byte-round-tripping fake Redis.
 */
public class CacheDSGFollowerStorerRedisTest extends TestCase {

    /** In-memory base storer so the cache logic is exercised in isolation. */
    private static class FakeFollowerStorer extends MySQLDSGFollowerStorer {
        FakeFollowerStorer() {
            super(null);
        }
        @Override public void addFollower(long pid, long followerPid) { }
        @Override public void removeFollower(long pid, long followerPid) { }
        @Override public List<Long> getFollowers(long pid) {
            return new ArrayList<Long>();
        }
        @Override public List<Long> getFollowing(long pid) {
            return new ArrayList<Long>();
        }
    }

    private CacheDSGFollowerStorer cache;

    // junit-3.7's TestCase has no public no-arg constructor.
    public CacheDSGFollowerStorerRedisTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        RedisConnectionManager.setInstance(new SerializingRedisConnectionManager());
        cache = new CacheDSGFollowerStorer(new FakeFollowerStorer(), null, null);
    }

    /**
     * addFollower(pid, followerPid) means "followerPid follows pid", so pid must
     * be added to followerPid's following list exactly once. The dedup guard
     * used to test contains(followerPid) while adding pid, so a repeated call
     * (retry / double submit) appended pid again, inflating the cached following
     * list.
     */
    public void testAddFollowerTwiceDoesNotDuplicateFollowingEntry() throws Exception {
        cache.addFollower(100L, 200L);
        cache.addFollower(100L, 200L);

        List<Long> following = cache.getFollowing(200L);
        int occurrences = 0;
        for (Long f : following) {
            if (f != null && f.longValue() == 100L) {
                occurrences++;
            }
        }
        assertEquals("100 must appear exactly once in 200's following list",
                1, occurrences);
        assertEquals("following list must not contain duplicates",
                1, following.size());

        // Control: the followers list (already correct) stays deduped too.
        List<Long> followers = cache.getFollowers(100L);
        assertEquals(1, followers.size());
        assertEquals(Long.valueOf(200L), followers.get(0));
    }
}
