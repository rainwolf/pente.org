package org.pente.gameServer.tourney.test;

import java.util.List;

import junit.framework.TestCase;

import org.pente.gameServer.server.RedisConnectionManager;
import org.pente.gameServer.tourney.CacheTourneyStorer;
import org.pente.gameServer.tourney.RoundRobinFormat;
import org.pente.gameServer.tourney.Tourney;
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
}
