package org.pente.turnBased.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.pente.game.GridStateFactory;
import org.pente.game.TombstoneCodes;
import org.pente.gameServer.server.RedisConnectionManager;
import org.pente.turnBased.CacheTBStorer;
import org.pente.turnBased.InvalidMoveException;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBMessage;
import org.pente.turnBased.TBSet;
import org.pente.turnBased.TBStoreException;

/** CacheTBStorer.storeTombstoneTurn: atomic whole-turn storage. */
public class CacheTBStorerTombstoneTest extends TestCase {

    /** Records base-storer calls in order: "msg:<moveNum>", "move:<moveNum>:<code>" and "update". */
    private static class RecordingBase extends InMemoryTBGameStorer {
        final List<String> log = Collections.synchronizedList(new ArrayList<String>());
        final CountDownLatch endLatch = new CountDownLatch(1);
        volatile TBGame endedGame;
        public void storeNewMessage(long gid, TBMessage m) throws TBStoreException {
            log.add("msg:" + m.getMoveNum());
            super.storeNewMessage(gid, m);
        }
        public synchronized void storeNewMove(long gid, int moveNum, int move) throws TBStoreException {
            log.add("move:" + moveNum + ":" + move);
            super.storeNewMove(gid, moveNum, move);
        }
        public void updateGameAfterMove(TBGame game) throws TBStoreException {
            log.add("update");
            super.updateGameAfterMove(game);
        }
        public void endGame(TBGame game) throws TBStoreException { endedGame = game; endLatch.countDown(); }
        final CountDownLatch setLatch = new CountDownLatch(1);
        public void endSet(TBSet set) throws TBStoreException { super.endSet(set); setLatch.countDown(); }
    }

    /** Counts set writes (persistSet does exactly one hput into SID_TO_TB_SET). */
    private static class CountingRedis extends SerializingRedisConnectionManager {
        volatile int setWrites;
        public <T extends java.io.Serializable> void hput(String ns, String field, T value) {
            if (RedisConnectionManager.SID_TO_TB_SET.equals(ns)) setWrites++;
            super.hput(ns, field, value);
        }
    }

    private RecordingBase base;
    private CacheTBStorer cache;
    private CountingRedis redis;
    private long nextSid = 500L;

    public CacheTBStorerTombstoneTest(String name) { super(name); }

    protected void setUp() throws Exception {
        super.setUp();
        redis = new CountingRedis();
        RedisConnectionManager.setInstance(redis);
        RedisConnectionManager.getInstance().invalidate(RedisConnectionManager.SID_TO_TB_SET);
        base = new RecordingBase();
        cache = new CacheTBStorer(base, new InMemoryDSGPlayerStorer(), null, null, null);
    }

    protected void tearDown() throws Exception {
        if (cache != null) cache.destroy();
        RedisConnectionManager.resetInstance();
        super.tearDown();
    }

    private static TBMessage msg(long pid, int numMovesBefore, String text) {
        TBMessage m = new TBMessage();
        m.setMessage(text); m.setDate(new Date()); m.setPid(pid);
        m.setMoveNum(numMovesBefore + 1); m.setSeqNbr(1);
        return m;
    }

    private long active(boolean undoRequested, int... history) throws Exception {
        long sid = nextSid++;
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_TOMBSTONE);
        g.setState(TBGame.STATE_ACTIVE);
        g.setPlayer1Pid(1001L); g.setPlayer2Pid(1002L);
        g.setDaysPerMove(3); g.setLastMoveDate(new Date());
        g.setUndoRequested(undoRequested);
        for (int c : history) g.addMove(c);
        TBSet set = new TBSet(sid, g, null);
        set.setPlayer1Pid(1001L); set.setPlayer2Pid(1002L); set.setState(TBSet.STATE_ACTIVE);
        base.createSet(set);
        cache.loadSet(sid);
        base.log.clear();
        redis.setWrites = 0;
        return set.getGame1().getGid();
    }

    private long active(int... history) throws Exception { return active(false, history); }

    private int[] stored(long gid) throws Exception { return cache.loadGame(gid).getMoves(); }

    private void assertStored(long gid, int... expected) throws Exception {
        assertEquals(Arrays.toString(expected), Arrays.toString(stored(gid)));
    }

    private void assertLog(String... expected) {
        assertEquals(Arrays.asList(expected).toString(), new ArrayList<String>(base.log).toString());
    }

    private void assertRejected(long gid, int[] turn, TBMessage m, int... unchanged) throws Exception {
        int writes = redis.setWrites;
        try {
            cache.storeTombstoneTurn(gid, turn, m);
            fail("expected InvalidMoveException");
        } catch (InvalidMoveException e) {
            // expected
        }
        assertTrue(base.log.isEmpty());
        assertStored(gid, unchanged);
        assertEquals(writes, redis.setWrites);
    }

    public void testOwnPlusDead() throws Exception {
        long gid = active(180);
        cache.storeTombstoneTurn(gid, new int[] {181, 561}, null);
        assertStored(gid, 180, 181, 561);
        assertLog("move:1:181", "move:2:561", "update");
        assertEquals(1001L, cache.loadGame(gid).getCurrentPlayer());
    }

    public void testOwnOnly() throws Exception {
        long gid = active(180);
        cache.storeTombstoneTurn(gid, new int[] {181}, null);
        assertStored(gid, 180, 181);
        assertLog("move:1:181", "update");
    }

    public void testPassPlusDead() throws Exception {
        long gid = active(180);
        cache.storeTombstoneTurn(gid, new int[] {722, 361}, null);
        assertStored(gid, 180, 722, 361);
        assertLog("move:1:722", "move:2:361", "update");
    }

    public void testPassOnly() throws Exception {
        long gid = active(180);
        cache.storeTombstoneTurn(gid, new int[] {722}, null);
        assertStored(gid, 180, 722);
        assertEquals(TBGame.STATE_ACTIVE, cache.loadGame(gid).getState());
    }

    public void testDeadDiscardedAfterWinningStone() throws Exception {
        long gid = active(180, 0, 181, 2, 182, 4, 183, 6);
        cache.storeTombstoneTurn(gid, new int[] {184, 461}, null);
        assertStored(gid, 180, 0, 181, 2, 182, 4, 183, 6, 184);
        assertLog("move:8:184", "update");
        assertEquals(1, cache.loadGame(gid).getWinner());
        assertTrue(cache.loadGame(gid).getState() != TBGame.STATE_ACTIVE);
        assertTrue(base.endLatch.await(5, TimeUnit.SECONDS));
    }

    public void testSixthEmptyTurnDraws() throws Exception {
        long gid = active(180, 722, 722, 722, 722, 722);
        cache.storeTombstoneTurn(gid, new int[] {722}, null);
        assertEquals(7, stored(gid).length);
        assertEquals(0, cache.loadGame(gid).getWinner());
        assertTrue(cache.loadGame(gid).getState() != TBGame.STATE_ACTIVE);
        assertTrue(base.endLatch.await(5, TimeUnit.SECONDS));
    }

    public void testDeadOnJustCapturedPointAccepted() throws Exception {
        long gid = active(180, 181, 300, 182);
        cache.storeTombstoneTurn(gid, new int[] {183, 542}, null);
        int[] s = stored(gid);
        assertEquals(183, s[s.length - 2]);
        assertEquals(542, s[s.length - 1]);
    }

    public void testDeadOnOwnPointRejected() throws Exception {
        long gid = active(180);
        assertRejected(gid, new int[] {181, 542}, null, 180);
    }

    public void testOccupiedOwnPointRejected() throws Exception {
        long gid = active(180);
        assertRejected(gid, new int[] {180}, null, 180);
    }

    public void testOwnPassOnTurnZeroRejected() throws Exception {
        long gid = active();
        assertRejected(gid, new int[] {722}, null);
    }

    public void testBadShapesRejectedWithSpecMessage() throws Exception {
        long gid = active(180);
        int[][] shapes = {{}, {181, 561, 562}, {561}, {181, 182}};
        for (int[] t : shapes) {
            try {
                cache.storeTombstoneTurn(gid, t, null);
                fail("expected InvalidMoveException");
            } catch (InvalidMoveException e) {
                assertEquals(TombstoneCodes.validateTurnShape(t), e.getMessage());
            }
        }
        assertStored(gid, 180);
        assertTrue(base.log.isEmpty());
    }

    public void testRejectedSecondEntryWritesNothing() throws Exception {
        long gid = active(180);
        assertRejected(gid, new int[] {181, 361 + 180}, null, 180);
    }

    public void testSinglePersistSetPerTurn() throws Exception {
        long gid = active(180);
        cache.storeTombstoneTurn(gid, new int[] {181, 561}, msg(1002L, 1, "hi"));
        assertEquals(1, redis.setWrites);
    }

    public void testMessageStoredAtPostTurnRowCount() throws Exception {
        long gid = active(180);
        cache.storeTombstoneTurn(gid, new int[] {181, 561}, msg(1002L, 1, "hi"));
        assertEquals("hi", cache.loadGame(gid).getMessage(3).getMessage());
        assertNull(cache.loadGame(gid).getMessage(2));
        assertLog("msg:3", "move:1:181", "move:2:561", "update");
    }

    public void testWinningTurnMessageReachesEndGameAndSurvivesSetEnd() throws Exception {
        long gid = active(180, 0, 181, 2, 182, 4, 183, 6);
        cache.storeTombstoneTurn(gid, new int[] {184, 461}, msg(1001L, 8, "gg"));
        assertTrue(base.endLatch.await(5, TimeUnit.SECONDS));
        assertTrue(base.setLatch.await(5, TimeUnit.SECONDS));
        TBGame ended = base.endedGame;
        assertEquals("gg", ended.getMessage(ended.getNumMoves()).getMessage());
        assertEquals("gg", cache.loadGame(gid).getMessage(9).getMessage());
        assertEquals("msg:9", base.log.get(0));
        assertEquals("move:8:184", base.log.get(1));
    }

    public void testRejectedTurnStoresNoMessage() throws Exception {
        long gid = active(180);
        assertRejected(gid, new int[] {181, 361 + 180}, msg(1002L, 1, "hi"), 180);
        assertTrue(cache.loadGame(gid).getMessages().isEmpty());
    }

    public void testPendingUndoRowOverwrittenAndCleared() throws Exception {
        long gid = active(true, 180, 181);
        cache.storeTombstoneTurn(gid, new int[] {722}, null);
        assertEquals("move:2:722", base.log.get(0));
        assertFalse2(cache.loadGame(gid).isUndoRequested());
    }

    public void testStoreNewMoveRefusesTombstoneAfterCentre() throws Exception {
        long gid = active(180);
        try {
            cache.storeNewMove(gid, 1, 181);
            fail("expected InvalidMoveException");
        } catch (InvalidMoveException e) {
            assertEquals("Tombstone turns must use storeTombstoneTurn", e.getMessage());
        }
    }

    public void testStoreNewMoveStillWritesCentre() throws Exception {
        long gid = active();
        cache.storeNewMove(gid, 0, 180);
        assertStored(gid, 180);
    }

    private static void assertFalse2(boolean b) { assertTrue(!b); }
}
