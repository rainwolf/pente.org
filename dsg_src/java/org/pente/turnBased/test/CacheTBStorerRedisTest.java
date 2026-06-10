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

    public void testResignPersistsWinner() throws Exception {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setState(TBGame.STATE_ACTIVE);
        g.setPlayer1Pid(1001L);
        g.setPlayer2Pid(1002L);
        g.setDaysPerMove(3);
        g.setLastMoveDate(new Date());

        TBSet set = new TBSet(102L, g, null);
        base.createSet(set);

        // Warm the cache.
        cache.loadSet(102L);

        long gid = set.getGame1().getGid();

        // Player 1 (seat winner 2) resigns. resignGame persists the set with the
        // winner set synchronously (before queuing the background endGame work),
        // so the canonical Redis aggregate must reflect the winner immediately.
        cache.resignGame(cache.loadGame(gid), 1001L);

        TBGame fromGame = cache.loadGame(gid);
        assertTrue(fromGame.getWinner() != 0);
        assertEquals(fromGame.getWinner(),
                cache.loadSet(102L).getGame(gid).getWinner());
    }

    public void testWaitingSetAppearsThenClearsOnAccept() throws Exception {
        // A waiting set has an open seat (player2Pid == 0). State must be
        // NOT_STARTED so the set is a genuine outstanding invite.
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setState(TBGame.STATE_NOT_STARTED);
        g.setPlayer1Pid(1001L);
        g.setPlayer2Pid(0L);
        g.setDaysPerMove(3);
        g.setCreationDate(new Date());
        g.setLastMoveDate(new Date());

        TBSet set = new TBSet(103L, g, null);
        set.setState(TBSet.STATE_NOT_STARTED);
        set.setPlayer1Pid(1001L);
        set.setPlayer2Pid(0L);
        set.setInviterPid(1001L);
        assertTrue("set must be a waiting set", set.isWaitingSet());

        cache.createSet(set);

        // The waiting set must be visible in the Redis-backed waiting list.
        assertTrue("waiting set should appear after createSet",
                containsSid(cache.getWaitingSets(), 103L));

        // Accepting the invite must remove it from the waiting list.
        cache.acceptInvite(set, 1002L);
        assertTrue("waiting set should clear after acceptInvite",
                !containsSid(cache.getWaitingSets(), 103L));
    }

    /**
     * Regression for the accept-public-invitation NPE: callers such as
     * ReplyInvitationServlet pass their own TBSet to acceptInvite and then read
     * that same object afterwards (for move notifications). The storer must
     * update the passed object's seats, not just a detached Redis copy. Before
     * the fix the caller's set kept player2Pid == 0, so
     * getOpponent(getCurrentPlayer()) returned 0 and loadPlayer(0) NPE'd.
     */
    public void testAcceptInviteUpdatesCallerSet() throws Exception {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setState(TBGame.STATE_NOT_STARTED);
        g.setPlayer1Pid(1001L);
        g.setPlayer2Pid(0L);
        g.setDaysPerMove(3);
        g.setCreationDate(new Date());
        g.setLastMoveDate(new Date());

        TBSet set = new TBSet(104L, g, null);
        set.setState(TBSet.STATE_NOT_STARTED);
        set.setPlayer1Pid(1001L);
        set.setPlayer2Pid(0L);
        set.setInviterPid(1001L);

        cache.createSet(set);
        cache.acceptInvite(set, 1002L);

        // The caller's own object must reflect the accept (pre-migration contract).
        assertEquals("caller's set seat must be filled", 1002L, set.getPlayer2Pid());
        assertEquals("caller's game seat must be filled",
                1002L, set.getGame1().getPlayer2Pid());
        // The exact crash condition: opponent of the current player must be real.
        long cp = set.getGame1().getCurrentPlayer();
        assertTrue("opponent of current player must be a real pid",
                set.getGame1().getOpponent(cp) != 0L);
    }

    /**
     * Regression for the multi-stone move-collision: MoveServlet reads
     * game.getNumMoves() off a TBGame it loaded once and passes it as moveNum for
     * every stone of a turn (connect6/swap2/go submit 2-3 moves). Under the Redis
     * aggregate root that local copy no longer advances, so the same moveNum was
     * passed twice -> tb_move (gid, move_num) PK collision -> earlier stones lost.
     * storeNewMove must derive move_num from the freshly-loaded game instead.
     */
    public void testStoreNewMoveUsesAuthoritativeMoveNum() throws Exception {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setState(TBGame.STATE_ACTIVE);
        g.setPlayer1Pid(1001L);
        g.setPlayer2Pid(1002L);
        g.setDaysPerMove(3);
        g.setLastMoveDate(new Date());

        TBSet set = new TBSet(105L, g, null);
        base.createSet(set);
        cache.loadSet(105L);

        long gid = set.getGame1().getGid();
        // Simulate MoveServlet's stale moveNum: pass 0 for BOTH stones of a turn.
        cache.storeNewMove(gid, 0, 180);
        cache.storeNewMove(gid, 0, 181);

        java.util.List<Integer> nums = base.getStoredMoveNums(gid);
        assertEquals("both moves must reach the base storer", 2, nums.size());
        assertTrue("move numbers must be distinct (no tb_move PK collision)",
                !nums.get(0).equals(nums.get(1)));
    }

    /**
     * setGameEventId must update the cached game, not just the DB. Callers
     * (ReplyInvitationServlet, CacheKOTHStorer) rely on the new event id being
     * visible on the next loadGame; a DB-only write left the cache stale.
     */
    public void testSetGameEventIdPersistsToCache() throws Exception {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setState(TBGame.STATE_ACTIVE);
        g.setPlayer1Pid(1001L);
        g.setPlayer2Pid(1002L);
        g.setDaysPerMove(3);
        g.setLastMoveDate(new Date());
        g.setEventId(5);

        TBSet set = new TBSet(106L, g, null);
        base.createSet(set);
        cache.loadSet(106L);

        long gid = set.getGame1().getGid();
        cache.setGameEventId(gid, 99L);

        assertEquals("cached game event id must reflect setGameEventId",
                99, cache.loadGame(gid).getEventId());
    }

    /**
     * Redis can lose every key while the JVM keeps running (container OOM-kill
     * with no RDB save, FLUSHALL, failover to a fallback that is cleared on
     * recovery). A JVM-side load-once flag survives such a wipe and pins the
     * waiting list to empty until Tomcat restarts. The loaded sentinel must
     * live in Redis itself so a wipe also wipes the sentinel and the next read
     * re-bootstraps from the DB.
     */
    public void testWaitingSetsRehealAfterRedisWipe() throws Exception {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setState(TBGame.STATE_NOT_STARTED);
        g.setPlayer1Pid(1001L);
        g.setPlayer2Pid(0L);
        g.setDaysPerMove(3);
        g.setCreationDate(new Date());
        g.setLastMoveDate(new Date());

        final TBSet set = new TBSet(104L, g, null);
        set.setState(TBSet.STATE_NOT_STARTED);
        set.setPlayer1Pid(1001L);
        set.setPlayer2Pid(0L);
        set.setInviterPid(1001L);

        // Base storer that, like the real DB, still knows the waiting set
        // after the cache loses it.
        InMemoryTBGameStorer base2 = new InMemoryTBGameStorer() {
            @Override
            public java.util.List<TBSet> loadWaitingSets() {
                java.util.List<TBSet> l = new java.util.ArrayList<TBSet>();
                l.add(set);
                return l;
            }
        };
        CacheTBStorer cache2 = new CacheTBStorer(
                base2, new InMemoryDSGPlayerStorer(), null, null, null);
        try {
            base2.createSet(set);
            assertTrue("waiting set visible before wipe",
                    containsSid(cache2.getWaitingSets(), 104L));

            ((SerializingRedisConnectionManager)
                    RedisConnectionManager.getInstance()).flushAll();

            assertTrue("waiting sets must reheal from DB after a Redis wipe",
                    containsSid(cache2.getWaitingSets(), 104L));
        } finally {
            cache2.destroy();
        }
    }

    private static boolean containsSid(java.util.List<TBSet> sets, long sid) {
        for (TBSet s : sets) {
            if (s.getSetId() == sid) {
                return true;
            }
        }
        return false;
    }
}
