package org.pente.turnBased.test;

import java.util.Date;

import junit.framework.TestCase;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.server.RedisConnectionManager;
import org.pente.turnBased.CacheTBStorer;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBMessage;
import org.pente.turnBased.TBSet;

/**
 * Verifies that CacheTBStorer keeps the cached game and the cached set in sync
 * after a move — the core invariant of the Redis aggregate-root migration.
 *
 * Regression-guard semantics: this passes today because the pre-migration code
 * shares one TBGame instance across gamesMap and setsMap, so the two reads
 * cannot diverge. It goes RED once reads are switched to Redis (Task 4) and a
 * mutator fails to persist the whole set, and returns GREEN when every mutator
 * follows the write invariant (Task 5+). Treat a RED here as a missing
 * persistSet() on some mutation path.
 */
public class CacheTBStorerRedisTest extends TestCase {

    private InMemoryTBGameStorer base;
    private CacheTBStorer cache;

    public CacheTBStorerRedisTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        // Serializing RedisConnectionManager (no real Redis required); it
        // serializes on put and deserializes on get so reads return independent
        // copies, faithfully mimicking real Redis (unlike the raw-reference
        // production fallback, which makes divergence tests toothless).
        RedisConnectionManager.setInstance(new SerializingRedisConnectionManager());
        RedisConnectionManager.getInstance().invalidate(
                RedisConnectionManager.SID_TO_TB_SET);

        base = new InMemoryTBGameStorer();
        cache = new CacheTBStorer(
                base, new InMemoryDSGPlayerStorer(), null, null, null);
    }

    protected void tearDown() throws Exception {
        if (cache != null) {
            cache.destroy();
        }
        RedisConnectionManager.resetInstance();
        super.tearDown();
    }

    public void testGameAndSetDoNotDivergeAfterMove() throws Exception {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setState(TBGame.STATE_ACTIVE);
        g.setPlayer1Pid(1001L);
        g.setPlayer2Pid(1002L);
        g.setDaysPerMove(3);
        g.setLastMoveDate(new Date());

        TBSet set = new TBSet(100L, g, null);
        base.createSet(set);

        // Warm the cache.
        cache.loadSet(100L);

        long gid = set.getGame1().getGid();
        // 180 == centre of a 19x19 board (9*19+9), the standard legal first move.
        cache.storeNewMove(gid, 0, 180);

        TBGame fromGame = cache.loadGame(gid);
        TBGame fromSet = cache.loadSet(100L).getGame(gid);
        // With a faithful (serializing) Redis fixture, both reads are
        // independent deserialized copies of the SID_TO_TB_SET aggregate root,
        // so they cannot diverge from each other — the only way to detect a
        // mutator that forgot to persist the set is to assert the move actually
        // survived into the cache. RED until storeNewMove persists the set
        // (Task 5); GREEN once it does.
        assertEquals(1, fromGame.getNumMoves());
        assertEquals(1, fromSet.getNumMoves());
    }

    public void testMessageSurvivesIntoCacheAfterStoreNewMessage() throws Exception {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setState(TBGame.STATE_ACTIVE);
        g.setPlayer1Pid(1001L);
        g.setPlayer2Pid(1002L);
        g.setDaysPerMove(3);
        g.setLastMoveDate(new Date());

        TBSet set = new TBSet(101L, g, null);
        base.createSet(set);

        // Warm the cache.
        cache.loadSet(101L);

        long gid = set.getGame1().getGid();

        TBMessage msg = new TBMessage();
        msg.setMoveNum(0);
        msg.setSeqNbr(0);
        msg.setPid(1001L);
        msg.setMessage("hello");
        msg.setDate(new Date());
        cache.storeNewMessage(gid, msg);

        // Re-read from Redis aggregate root: the message must have been
        // persisted by storeNewMessage (Task 5), not lost.
        TBGame fromGame = cache.loadGame(gid);
        assertEquals(1, fromGame.getMessages().size());
        assertEquals("hello", fromGame.getMessages().get(0).getMessage());
    }
}
