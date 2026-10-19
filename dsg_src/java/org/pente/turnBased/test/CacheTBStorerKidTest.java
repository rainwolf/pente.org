package org.pente.turnBased.test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.kids.KidChatPolicy;
import org.pente.gameServer.server.RedisConnectionManager;
import org.pente.turnBased.CacheTBStorer;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBMessage;
import org.pente.turnBased.TBSet;
import org.pente.turnBased.TBStoreException;

/** Kid chat rule on turn-based move messages and cancel-request text. */
public class CacheTBStorerKidTest extends TestCase {

    private RecordingBase base;
    private CacheTBStorer cache;
    private SerializingRedisConnectionManager redis;

    public CacheTBStorerKidTest(String name) {
        super(name);
    }

    /** Records what reaches the base storer's requestCancel. */
    private static class RecordingBase extends InMemoryTBGameStorer {
        boolean cancelCalled;
        String cancelMsg = "unset";

        public void requestCancel(TBSet set, long requestorPid, String message)
                throws TBStoreException {
            cancelCalled = true;
            cancelMsg = message;
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
    }

    protected void tearDown() throws Exception {
        if (cache != null) {
            cache.destroy();
        }
        RedisConnectionManager.resetInstance();
        super.tearDown();
    }

    private static KidChatPolicy refusing(final long x, final long y) {
        return (a, b) -> !((a == x && b == y) || (a == y && b == x));
    }

    private long createGame(long p1, long p2) throws Exception {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setState(TBGame.STATE_ACTIVE);
        g.setPlayer1Pid(p1);
        g.setPlayer2Pid(p2);
        g.setDaysPerMove(3);
        g.setLastMoveDate(new Date());

        TBSet set = new TBSet(101L, g, null);
        set.setPlayer1Pid(p1);
        set.setPlayer2Pid(p2);
        base.createSet(set);
        cache.loadSet(101L);
        return set.getGame1().getGid();
    }

    private static TBMessage message(long pid, String text) {
        TBMessage msg = new TBMessage();
        msg.setMoveNum(0);
        msg.setSeqNbr(0);
        msg.setPid(pid);
        msg.setMessage(text);
        msg.setDate(new Date());
        return msg;
    }

    public void testRefusedMoveMessageNotStored() throws Exception {
        long gid = createGame(1001L, 1002L);
        cache.setKidChatPolicy(refusing(1001L, 1002L));

        cache.storeNewMessage(gid, message(1001L, "hello"));

        assertEquals(0, cache.loadGame(gid).getMessages().size());
    }

    public void testAllowedMoveMessageStored() throws Exception {
        long gid = createGame(1001L, 1002L);
        cache.setKidChatPolicy(refusing(1001L, 9999L));

        cache.storeNewMessage(gid, message(1001L, "hello"));

        TBGame fromGame = cache.loadGame(gid);
        assertEquals(1, fromGame.getMessages().size());
        assertEquals("hello", fromGame.getMessages().get(0).getMessage());
    }

    public void testStoreNewMessagePassesOpponentPid() throws Exception {
        final List<long[]> seen = new ArrayList<>();
        KidChatPolicy recording = (a, b) -> {
            seen.add(new long[]{a, b});
            return true;
        };
        cache.setKidChatPolicy(recording);

        long gid = createGame(1001L, 1002L);
        cache.storeNewMessage(gid, message(1001L, "hi"));
        assertEquals(1, seen.size());
        assertEquals(1001L, seen.get(0)[0]);
        assertEquals(1002L, seen.get(0)[1]);

        seen.clear();
        cache.destroy();
        RedisConnectionManager.getInstance().invalidate(
                RedisConnectionManager.SID_TO_TB_SET);
        base = new RecordingBase();
        cache = new CacheTBStorer(
                base, new InMemoryDSGPlayerStorer(), null, null, null);
        cache.setKidChatPolicy(recording);

        long openGid = createGame(1001L, 0L);
        cache.storeNewMessage(openGid, message(1001L, "anyone?"));
        assertEquals(1, seen.size());
        assertEquals(1001L, seen.get(0)[0]);
        assertEquals(0L, seen.get(0)[1]);
    }

    public void testCancelMessageStrippedWhenRefused() throws Exception {
        createGame(1001L, 1002L);
        cache.setKidChatPolicy(refusing(1001L, 1002L));

        cache.requestCancel(cache.loadSet(101L), 1001L, "please");

        TBSet set = cache.loadSet(101L);
        assertNull(set.getCancelMsg());
        assertEquals(1001L, set.getCancelPid());
        assertTrue(base.cancelCalled);
        assertNull(base.cancelMsg);
    }

    public void testCancelMessageKeptWhenAllowed() throws Exception {
        createGame(1001L, 1002L);
        cache.setKidChatPolicy(refusing(1001L, 9999L));

        cache.requestCancel(cache.loadSet(101L), 1002L, "please");

        TBSet set = cache.loadSet(101L);
        assertEquals("please", set.getCancelMsg());
        assertEquals(1002L, set.getCancelPid());
        assertEquals("please", base.cancelMsg);
    }
}
