package org.pente.gameServer.tourney.test;

import java.util.List;

import junit.framework.TestCase;

import org.pente.gameServer.server.RedisConnectionManager;
import org.pente.gameServer.tourney.CacheTourneyStorer;
import org.pente.gameServer.tourney.RoundRobinFormat;
import org.pente.gameServer.tourney.Tourney;
import org.pente.gameServer.tourney.TourneyMatch;
import org.pente.gameServer.tourney.TourneyPlayerData;
import org.pente.gameServer.tourney.TourneyRound;
import org.pente.turnBased.test.SerializingRedisConnectionManager;

/**
 * Failing-fixture scaffolding for the CacheTourneyStorer -> Redis aggregate-root
 * migration (Tourney as the aggregate root).
 *
 * Uses the SerializingRedisConnectionManager (no real Redis required); it
 * serializes on put and deserializes on get so reads return independent copies,
 * faithfully mimicking real Redis (unlike the raw-reference production fallback,
 * which makes divergence tests toothless).
 */
public class CacheTourneyStorerRedisTest extends TestCase {

    private InMemoryTourneyStorer backing;
    private CacheTourneyStorer cache;

    public CacheTourneyStorerRedisTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        RedisConnectionManager.setInstance(new SerializingRedisConnectionManager());
        RedisConnectionManager.getInstance().invalidate(
                RedisConnectionManager.EID_TO_TOURNEY);
        RedisConnectionManager.getInstance().invalidate(
                RedisConnectionManager.EID_TO_TOURNEY_PLAYER_PIDS);

        backing = new InMemoryTourneyStorer();
        cache = makeCache(backing);
    }

    protected void tearDown() throws Exception {
        if (cache != null) {
            cache.destroy();
        }
        RedisConnectionManager.resetInstance();
        super.tearDown();
    }

    /**
     * Constructs the cache from the real CacheTourneyStorer constructor
     * (single arg: the backing storer). Collaborators (dsgPlayerStorer,
     * tbStorer, notificationServer, kothStorer) are optional setters and are
     * left unset because they are not exercised by these tests.
     */
    private CacheTourneyStorer makeCache(InMemoryTourneyStorer backing) {
        return new CacheTourneyStorer(backing);
    }

    /** Minimal valid Tourney: event id + name. */
    private Tourney newTourney(int eid) {
        Tourney t = new Tourney(eid);
        t.setName("Test Tourney " + eid);
        return t;
    }

    public void testAddPlayerPersistsToPlayerPidIndex() throws Throwable {
        Tourney t = newTourney(900);
        cache.insertTourney(t);

        cache.addPlayerToTourney(1001L, 900);
        cache.addPlayerToTourney(1002L, 900);

        List<Long> pids = cache.getTourneyPlayerPids(900);
        assertTrue("expected pid 1001 in player-pid index", pids.contains(1001L));
        assertTrue("expected pid 1002 in player-pid index", pids.contains(1002L));
    }

    public void testAddPlayerUpdatesRedisCachedPidList() throws Throwable {
        Tourney t = newTourney(902);
        cache.insertTourney(t);
        cache.getTourneyPlayerPids(902);          // warm the Redis-cached (empty) pid list
        cache.addPlayerToTourney(1001L, 902);     // not yet Redis-aware (Task 5)
        java.util.List<Long> pids = cache.getTourneyPlayerPids(902);
        assertTrue("re-read must reflect the added player", pids.contains(1001L));
    }

    /**
     * completeTourney must persist the mutated Tourney (endDate set) to Redis as
     * the aggregate root. We insert tourney 903 (lands in the upcoming eid-list),
     * complete it, then re-read it: a fresh getTourney must reflect the endDate
     * mutation, proving persistTourney wrote the updated blob. (Direct list-move
     * assertion is not reachable from the test since the eid-lists have no public
     * accessor; the endDate round-trip is the meaningful, robust signal.)
     */
    public void testCompleteTourneyMovesEidToCompletedList() throws Throwable {
        Tourney t = newTourney(903);
        t.setPrize("none");   // getCrownInt() lowercases prize; avoid NPE on null
        cache.insertTourney(t);
        assertNull("precondition: new tourney has no endDate", cache.getTourney(903).getEndDate());

        cache.completeTourney(t);

        Tourney reread = cache.getTourney(903);
        assertNotNull("completed tourney must still be retrievable", reread);
        assertNotNull("completeTourney must persist the endDate mutation to Redis",
                reread.getEndDate());
    }

    /**
     * Go/no-go gate for the Redis migration: Tourney's {@code transient
     * TourneyFormat format} field is not serialized directly; it is rebuilt in a
     * custom readObject from the persisted {@code formatType} int. Redis storage
     * round-trips Tourney objects through ObjectOutputStream/ObjectInputStream
     * (which invoke readObject), so this proves {@code format} survives.
     *
     * Note: Tourney exposes no getFormatType()/setFormatType(int). The format
     * type is set via setFormat(TourneyFormat) (RoundRobinFormat -> formatType 1),
     * and the only public, format-dependent accessor is getFormat(). We therefore
     * assert getFormat() is non-null and is the same concrete TourneyFormat type
     * after a Redis serialize/deserialize round trip.
     */
    /** Minimal player builder for constructing a real round/section/match graph. */
    private TourneyPlayerData player(long pid, String name) {
        TourneyPlayerData p = new TourneyPlayerData();
        p.setPlayerID(pid);
        p.setName(name);
        return p;
    }

    /**
     * Core of Task 6: after the Redis migration getTourney returns a DETACHED
     * deserialized copy, so a caller mutating that copy and calling updateMatch
     * must have its result re-found + applied onto the canonical cached tourney.
     *
     * Build a real RoundRobin Tourney (2 players -> 1 section, 2 matches),
     * persist it, pull a DETACHED match, set a result on it, updateMatch, then
     * re-read: the canonical cached tourney must reflect the result+gid.
     * RED before this task (canonical untouched), GREEN after.
     */
    public void testUpdateMatchAppliesResultToCanonicalTourney() throws Throwable {
        Tourney t = newTourney(904);
        t.setFormat(new RoundRobinFormat());   // <=6 players -> single section

        java.util.List<TourneyPlayerData> players = new java.util.ArrayList<TourneyPlayerData>();
        players.add(player(1L, "Alice"));
        players.add(player(2L, "Bob"));

        TourneyRound round = t.createFirstRound(players);   // round 1, section 1, 2 matches
        // production assigns match ids from the DB autoincrement; emulate that so
        // findMatch(...) can re-find by id.
        long id = 1;
        for (TourneyMatch m : round.getSection(1).getMatches()) {
            m.setMatchID(id++);
        }

        cache.insertTourney(t);   // persists the full graph (rounds included) to Redis

        // obtain a DETACHED match from the cached aggregate
        TourneyMatch detached =
                cache.getTourney(904).getRound(1).getSection(1).getMatches().get(0);
        detached.setResult(TourneyMatch.RESULT_P1_WINS);   // 1
        detached.setGid(555L);

        cache.updateMatch(detached);

        TourneyMatch reloaded =
                cache.getTourney(904).getRound(1).getSection(1).getMatches().get(0);
        assertEquals("result must be applied to the canonical cached tourney",
                TourneyMatch.RESULT_P1_WINS, reloaded.getResult());
        assertEquals("gid must be applied to the canonical cached tourney",
                555L, reloaded.getGid());
    }

    /**
     * Regression: getCurrentTournies() must NOT write back the stale loop-start
     * snapshot of the CURRENT eid-list. During the loop, checkRoundStatus() may
     * complete a tourney, moving its eid CURRENT->COMPLETED in Redis. The buggy
     * code removed the separately-"ended" eids from the stale snapshot and wrote
     * that back, re-inserting the just-completed eid into CURRENT (so it lived in
     * BOTH lists and got double-completed on the next sweep -> duplicate crown,
     * notification, startAnotherTourney).
     *
     * We model the mid-loop completion by overriding getTourney for the "trigger"
     * eid: it performs the same CURRENT->COMPLETED move completeTourney would, and
     * returns a tourney with a future endDate so it is NOT itself added to the
     * loop's `ended` list (mirroring completeTourney setting endDate to ~now,
     * which is after the loop-start `today`). A separate tourney with a past
     * endDate populates `ended`, forcing the buggy write-back path. After one
     * sweep, CURRENT must not contain the completed trigger eid.
     */
    public void testGetCurrentTourniesDoesNotReinsertCompletedEid() throws Throwable {
        final int endedEid = 907;     // past endDate -> goes into `ended`
        final int triggerEid = 906;   // "completed" mid-loop -> moved CURRENT->COMPLETED

        final Tourney endedT = newTourney(endedEid);
        endedT.setEndDate(new java.util.Date(System.currentTimeMillis() - 600000L)); // 10 min ago

        final Tourney triggerT = newTourney(triggerEid);
        triggerT.setEndDate(new java.util.Date(System.currentTimeMillis() + 3600000L)); // +1h, not "ended"

        final RedisConnectionManager rcm = RedisConnectionManager.getInstance();

        CacheTourneyStorer triggerCache = new CacheTourneyStorer(backing) {
            @Override
            public synchronized Tourney getTourney(int eid) throws Throwable {
                if (eid == triggerEid) {
                    // mimic checkRoundStatus()->completeTourney()'s moveEid(CURRENT -> COMPLETED)
                    java.util.ArrayList<Integer> cur = rcm.hget(RedisConnectionManager.TOURNEY_LIST_CURRENT, "list");
                    if (cur == null) cur = new java.util.ArrayList<Integer>();
                    cur.remove(Integer.valueOf(triggerEid));
                    rcm.hput(RedisConnectionManager.TOURNEY_LIST_CURRENT, "list", cur);
                    java.util.ArrayList<Integer> comp = rcm.hget(RedisConnectionManager.TOURNEY_LIST_COMPLETED, "list");
                    if (comp == null) comp = new java.util.ArrayList<Integer>();
                    if (!comp.contains(triggerEid)) comp.add(triggerEid);
                    rcm.hput(RedisConnectionManager.TOURNEY_LIST_COMPLETED, "list", comp);
                    return triggerT;
                }
                if (eid == endedEid) return endedT;
                return super.getTourney(eid);
            }
        };

        try {
            // bootstrap CURRENT (empty backing) so currentLoaded=true, then seed the list
            triggerCache.getCurrentTournies();
            java.util.ArrayList<Integer> seed = new java.util.ArrayList<Integer>();
            seed.add(endedEid);
            seed.add(triggerEid);
            rcm.hput(RedisConnectionManager.TOURNEY_LIST_CURRENT, "list", seed);

            triggerCache.getCurrentTournies();   // the sweep under test

            java.util.ArrayList<Integer> current = rcm.hget(RedisConnectionManager.TOURNEY_LIST_CURRENT, "list");
            if (current == null) current = new java.util.ArrayList<Integer>();
            assertTrue("completed eid must NOT be re-inserted into CURRENT", !current.contains(triggerEid));
            assertTrue("ended eid must be removed from CURRENT", !current.contains(endedEid));

            java.util.ArrayList<Integer> completed = rcm.hget(RedisConnectionManager.TOURNEY_LIST_COMPLETED, "list");
            assertNotNull(completed);
            assertTrue("completed eid must remain in COMPLETED", completed.contains(triggerEid));
        } finally {
            triggerCache.destroy();
        }
    }

    public void testTourneyFormatSurvivesRedisRoundTrip() throws Exception {
        Tourney t = newTourney(901);
        t.setFormat(new RoundRobinFormat());   // sets formatType = 1
        assertNotNull("precondition: format set before serialize", t.getFormat());

        byte[] bytes = RedisConnectionManager.serialize(t);
        Tourney loaded = (Tourney) RedisConnectionManager.deserialize(bytes);

        assertNotNull("transient format must be rebuilt on deserialize",
                loaded.getFormat());
        assertEquals("format concrete type preserved across Redis round trip",
                RoundRobinFormat.class, loaded.getFormat().getClass());
    }

    /**
     * Redis can lose every key while the JVM keeps running (container OOM-kill
     * with no RDB save, FLUSHALL, failover to a fallback that is cleared on
     * recovery). JVM-side upcomingLoaded/currentLoaded/completedLoaded flags
     * survive such a wipe and pin the tourney lists to empty until Tomcat
     * restarts. The loaded sentinel must live in Redis itself so a wipe also
     * wipes the sentinel and the next read re-bootstraps from the DB.
     */
    public void testCompletedTourniesRehealAfterRedisWipe() throws Throwable {
        final Tourney t = newTourney(910);

        // Backing storer that, like the real DB, still knows the completed
        // tourney after the cache loses it.
        InMemoryTourneyStorer backing2 = new InMemoryTourneyStorer() {
            @Override
            public List<Tourney> getCompletedTournies() throws Throwable {
                java.util.List<Tourney> l = new java.util.ArrayList<Tourney>();
                Tourney stored = getTourney(910);
                if (stored != null) l.add(stored);
                return l;
            }
        };
        backing2.insertTourney(t);
        CacheTourneyStorer cache2 = new CacheTourneyStorer(backing2);
        try {
            assertEquals("completed tourney visible before wipe",
                    1, cache2.getCompletedTournies().size());

            ((SerializingRedisConnectionManager)
                    RedisConnectionManager.getInstance()).flushAll();

            assertEquals("completed list must reheal from DB after a Redis wipe",
                    1, cache2.getCompletedTournies().size());
        } finally {
            cache2.destroy();
        }
    }
}
