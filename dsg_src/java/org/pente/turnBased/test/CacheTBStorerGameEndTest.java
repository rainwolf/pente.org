package org.pente.turnBased.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.server.RedisConnectionManager;
import org.pente.turnBased.CacheTBStorer;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBGameEndListener;
import org.pente.turnBased.TBSet;
import org.pente.turnBased.TBStoreException;

/** The single game-end hook: ended games and agreed-cancel games. */
public class CacheTBStorerGameEndTest extends TestCase {

    private RecordingBase base;
    private CacheTBStorer cache;
    private SerializingRedisConnectionManager redis;
    private final List<Long> notified = Collections.synchronizedList(new ArrayList<Long>());
    private CountDownLatch listenerLatch;

    public CacheTBStorerGameEndTest(String name) {
        super(name);
    }

    /** Counts endGame calls and releases a latch when one arrives. */
    private static class RecordingBase extends InMemoryTBGameStorer {
        volatile int endGameCount;
        final CountDownLatch endLatch = new CountDownLatch(1);
        volatile TBSet cancelledSet;

        public void cancelSet(TBSet set) throws TBStoreException {
            cancelledSet = set;
        }

        public void endGame(TBGame game) throws TBStoreException {
            endGameCount++;
            endLatch.countDown();
        }
    }

    protected void setUp() throws Exception {
        super.setUp();
        redis = new SerializingRedisConnectionManager();
        RedisConnectionManager.setInstance(redis);
        RedisConnectionManager.getInstance().invalidate(
                RedisConnectionManager.SID_TO_TB_SET);

        base = new RecordingBase();
        cache = new CacheTBStorer(
                base, new InMemoryDSGPlayerStorer(), null, null, null);
        listenerLatch = new CountDownLatch(1);
    }

    protected void tearDown() throws Exception {
        if (cache != null) {
            cache.destroy();
        }
        RedisConnectionManager.resetInstance();
        super.tearDown();
    }

    private void recordingListener() {
        cache.setGameEndListener(new TBGameEndListener() {
            public void gameEnded(TBGame game) {
                notified.add(game.getGid());
                listenerLatch.countDown();
            }
        });
    }

    private TBGame newGame(char state) {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setState(state);
        g.setPlayer1Pid(1001L);
        g.setPlayer2Pid(1002L);
        g.setDaysPerMove(3);
        g.setLastMoveDate(new Date());
        g.addMove(180);
        g.addMove(181);
        return g;
    }

    private TBSet createSet(long sid, TBGame g1, TBGame g2, char setState) throws Exception {
        TBSet set = new TBSet(sid, g1, g2);
        set.setPlayer1Pid(1001L);
        set.setPlayer2Pid(1002L);
        set.setState(setState);
        base.createSet(set);
        cache.loadSet(sid);
        return set;
    }

    public void testResignNotifiesOnce() throws Exception {
        TBSet set = createSet(101L, newGame(TBGame.STATE_ACTIVE), null, TBSet.STATE_ACTIVE);
        long gid = set.getGame1().getGid();
        recordingListener();

        cache.resignGame(cache.loadGame(gid), 1001L);

        assertTrue(listenerLatch.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);
        assertEquals(1, notified.size());
        assertEquals(gid, notified.get(0).longValue());
    }

    public void testFixGameNotifies() throws Throwable {
        TBSet set = createSet(102L, newGame(TBGame.STATE_ACTIVE), null, TBSet.STATE_ACTIVE);
        long gid = set.getGame1().getGid();
        recordingListener();

        cache.fixGame(gid);

        assertTrue(listenerLatch.await(5, TimeUnit.SECONDS));
        assertEquals(1, notified.size());
        assertEquals(gid, notified.get(0).longValue());
    }

    public void testActiveSetCancelNotifiesEachActiveGame() throws Exception {
        TBSet set = createSet(103L, newGame(TBGame.STATE_ACTIVE),
                newGame(TBGame.STATE_ACTIVE), TBSet.STATE_ACTIVE);
        recordingListener();

        cache.cancelSet(set);

        assertEquals(2, notified.size());
        assertEquals(set.getGame1().getGid(), notified.get(0).longValue());
        assertEquals(set.getGame2().getGid(), notified.get(1).longValue());
    }

    public void testActiveSetCancelNotifiesOnlyActiveGames() throws Exception {
        TBSet set = createSet(104L, newGame(TBGame.STATE_COMPLETED),
                newGame(TBGame.STATE_ACTIVE), TBSet.STATE_ACTIVE);
        recordingListener();

        cache.cancelSet(set);

        assertEquals(1, notified.size());
        assertEquals(set.getGame2().getGid(), notified.get(0).longValue());
    }

    public void testNotStartedCancelDoesNotNotify() throws Exception {
        TBSet set = createSet(105L, newGame(TBGame.STATE_ACTIVE), null, TBSet.STATE_NOT_STARTED);
        recordingListener();

        cache.cancelSet(set);

        assertEquals(0, notified.size());
    }

    public void testThrowingListenerDoesNotStopGameEnd() throws Exception {
        // Cancel half only: EndGameRunnable.run already catches Throwable, so a
        // resign case would pass without notifyGameEnded's own try/catch.
        TBSet set = createSet(106L, newGame(TBGame.STATE_ACTIVE), null, TBSet.STATE_ACTIVE);
        cache.setGameEndListener(new TBGameEndListener() {
            public void gameEnded(TBGame game) {
                throw new RuntimeException("listener boom");
            }
        });

        cache.cancelSet(set);

        assertEquals(TBSet.STATE_CANCEL, base.cancelledSet.getState());
    }

    public void testNoListenerIsFine() throws Exception {
        TBSet set = createSet(107L, newGame(TBGame.STATE_ACTIVE), null, TBSet.STATE_ACTIVE);

        cache.resignGame(cache.loadGame(set.getGame1().getGid()), 1001L);

        assertTrue(base.endLatch.await(5, TimeUnit.SECONDS));
        assertEquals(1, base.endGameCount);
    }
}
