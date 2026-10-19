package org.pente.gameServer.kids.test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.kids.KidCopyEmails;
import org.pente.gameServer.kids.KidCopyMailer;
import org.pente.gameServer.kids.KidLiveSessions;
import org.pente.gameServer.kids.KidSettings;
import org.pente.gameServer.kids.LiveKidState;

public class KidLiveSessionsTest extends TestCase {

    private static final long SERVER = 7;
    private static final long PARENT = 1;
    private static final long KID = 100;
    private static final long KID2 = 101;
    private static final long BOB = 200;
    private static final long AMY = 300;

    private MapPlayerStorer players;
    private InMemoryKidStorer kids;
    private InMemoryChatCopyStore store;
    private RecordingTextMailer recording;
    private SimpleDSGPlayerData kid;
    private SimpleDSGPlayerData kid2;
    private SimpleDSGPlayerData bob;
    private SimpleDSGPlayerData amy;
    private long[] now;
    private KidLiveSessions sessions;

    public KidLiveSessionsTest(String name) {
        super(name);
    }

    private static SimpleDSGPlayerData player(long pid, String name, Long parentPid) {
        SimpleDSGPlayerData d = new SimpleDSGPlayerData();
        d.setPlayerID(pid);
        d.setName(name);
        d.setParentPid(parentPid);
        d.setStatus(DSGPlayerData.ACTIVE);
        return d;
    }

    protected void setUp() {
        players = new MapPlayerStorer();
        SimpleDSGPlayerData parent = player(PARENT, "parent", null);
        parent.setEmail("parent@example.invalid");
        parent.setEmailValid(true);
        players.put(parent);
        kid = player(KID, "kid", PARENT);
        kid2 = player(KID2, "kid2", PARENT);
        bob = player(BOB, "bob", null);
        amy = player(AMY, "amy", null);
        players.put(kid);
        players.put(kid2);
        players.put(bob);
        players.put(amy);
        kids = new InMemoryKidStorer();
        kids.addToWhitelist(KID, AMY);
        kids.storeSettings(new KidSettings(KID, false, true));
        kids.storeSettings(new KidSettings(KID2, false, true));
        store = new InMemoryChatCopyStore();
        recording = new RecordingTextMailer();
        now = new long[]{1000};
        KidCopyMailer mailer = new KidCopyMailer(true, recording, players, kids);
        sessions = new KidLiveSessions(SERVER, players, kids, store, mailer, () -> now[0]);
    }

    private void join(SimpleDSGPlayerData d) {
        sessions.join(d.getName(), sessions.load(d));
    }

    private void say(SimpleDSGPlayerData to, String text) {
        sessions.delivered(to, "main room", "amy", text, new Date());
    }

    private String key(long pid) {
        return "kids:livechat:" + SERVER + ":" + pid;
    }

    public void testNonKidLoadsNotKid() {
        LiveKidState s = sessions.load(bob);
        assertTrue(!s.isKid());
        assertEquals(BOB, s.getPid());
        assertTrue(s.getWhitelist().isEmpty());
        assertTrue(!s.isCopyLiveChat());
    }

    public void testKidLoadsWhitelistAndCopyFlag() {
        LiveKidState s = sessions.load(kid);
        assertTrue(s.isKid());
        assertEquals(Long.valueOf(PARENT), s.getParentPid());
        assertEquals(1, s.getWhitelist().size());
        assertTrue(s.getWhitelist().contains(AMY));
        assertTrue(s.isCopyLiveChat());
    }

    public void testLoadFailsClosedOnStorerError() {
        kids.failReads = true;
        LiveKidState s = sessions.load(kid);
        assertTrue(s.isKid());
        assertTrue(s.getWhitelist().isEmpty());
        assertTrue(!s.isCopyLiveChat());
    }

    public void testAllowedUsesJoinedState() {
        join(kid);
        assertTrue(!sessions.allowed(kid, bob));
        assertTrue(sessions.allowed(kid, amy));
        assertTrue(sessions.allowed(bob, amy));
    }

    public void testKidDataChangedRefreshesJoinedState() {
        join(kid);
        kids.addToWhitelist(KID, BOB);
        sessions.kidDataChanged(KID);
        assertTrue(sessions.allowed(kid, bob));
    }

    public void testConvertWhileConnectedBecomesKid() {
        sessions.join("bob", LiveKidState.notKid(BOB));
        players.put(player(BOB, "bob", PARENT));
        sessions.kidDataChanged(BOB);
        assertTrue(sessions.get("bob").isKid());
    }

    public void testKidDataChangedIgnoresPlayersNotJoined() {
        sessions.kidDataChanged(KID);
        assertNull(sessions.get("kid"));
    }

    public void testDeliveredBuffersOnlyWhenCopyOn() {
        kids.storeSettings(new KidSettings(KID, false, false));
        join(kid);
        say(kid, "hi");
        assertTrue(store.lines(key(KID)).isEmpty());
        sessions.leave("kid");

        kids.storeSettings(new KidSettings(KID, false, true));
        join(kid);
        Date when = new Date();
        sessions.delivered(kid, "main room", "amy", "hello", when);
        assertEquals(Arrays.asList(KidCopyEmails.liveChatLine(when, "main room", "amy", "hello")),
                store.lines(key(KID)));
    }

    public void testLeaveMailsBufferOnceAndClears() {
        join(kid);
        say(kid, "one");
        say(kid, "two");
        sessions.leave("kid");
        assertEquals(1, recording.sent.size());
        sessions.leave("kid");
        assertEquals(1, recording.sent.size());
        assertTrue(store.lines(key(KID)).isEmpty());
    }

    public void testEmptySessionSendsNoEmail() {
        join(kid);
        sessions.leave("kid");
        assertEquals(0, recording.sent.size());
    }

    public void testCopyStoreFailureDoesNotBreakDelivery() {
        join(kid);
        store.fail = true;
        say(kid, "hi");
        assertTrue(sessions.allowed(kid, amy));
        assertTrue(!sessions.allowed(kid, bob));
        sessions.leave("kid");
        assertEquals(0, recording.sent.size());
    }

    public void testRejoinAfterLeaveIsKidAgain() {
        join(kid);
        sessions.leave("kid");
        assertNull(sessions.get("kid"));
        join(kid);
        assertTrue(sessions.get("kid").isKid());
        assertTrue(!sessions.allowed(kid, bob));
    }

    public void testChangeBetweenLoadAndJoinIsApplied() {
        LiveKidState s = sessions.load(kid);
        kids.addToWhitelist(KID, BOB);
        sessions.kidDataChanged(KID);
        sessions.join("kid", s);
        assertTrue(sessions.allowed(kid, bob));
    }

    public void testConvertBetweenLoadAndJoinBecomesKid() {
        LiveKidState s = sessions.load(bob);
        assertTrue(!s.isKid());
        players.put(player(BOB, "bob", PARENT));
        sessions.kidDataChanged(BOB);
        sessions.join("bob", s);
        assertTrue(sessions.get("bob").isKid());
    }

    public void testReleaseWhileConnectedMailsBuffer() {
        join(kid);
        say(kid, "hi");
        players.put(player(KID, "kid", null));
        sessions.kidDataChanged(KID);
        assertEquals(1, recording.sent.size());
        assertTrue(store.lines(key(KID)).isEmpty());
        assertTrue(!sessions.get("kid").isKid());
    }

    public void testBufferFromEarlierSessionMailedAtNextLeave() {
        store.append(key(KID), "earlier line", KidLiveSessions.BUFFER_TTL_SECONDS);
        join(kid);
        sessions.leave("kid");
        assertEquals(1, recording.sent.size());
        assertTrue(recording.sent.get(0)[3].contains("earlier line"));
    }

    public void testShutdownFlushMailsSynchronouslyAndDeletesKeys() {
        join(kid);
        join(kid2);
        say(kid, "to kid");
        say(kid2, "to kid2");
        assertEquals(2, sessions.flushForShutdown(now[0] + 5000));
        assertEquals(2, recording.sentNow.size());
        assertEquals(0, recording.sent.size());
        assertTrue(store.lines(key(KID)).isEmpty());
        assertTrue(store.lines(key(KID2)).isEmpty());
        for (Integer t : recording.nowTimeouts) {
            assertTrue(t <= KidLiveSessions.SHUTDOWN_SEND_TIMEOUT_MILLIS);
        }
    }

    public void testShutdownFlushStopsAtBudgetAndKeepsRemainingKeys() {
        join(kid);
        join(kid2);
        say(kid, "to kid");
        say(kid2, "to kid2");
        recording.onSendNow = () -> now[0] += 6000;
        assertEquals(1, sessions.flushForShutdown(now[0] + 5000));
        assertEquals(1, recording.sentNow.size());
        int a = store.lines(key(KID)).size();
        int b = store.lines(key(KID2)).size();
        assertTrue((a == 0 && b == 1) || (a == 1 && b == 0));
    }

    private List<String> failingFlush() {
        join(kid);
        say(kid, "first");
        say(kid, "second");
        List<String> before = store.lines(key(KID));
        recording.failNow = true;
        assertEquals(0, sessions.flushForShutdown(now[0] + 5000));
        return before;
    }

    public void testShutdownFlushSendFailureKeepsKey() {
        List<String> before = failingFlush();
        assertEquals(2, before.size());
        assertEquals(before, store.lines(key(KID)));
    }

    public void testLeaveAfterShutdownFlushKeepsBuffer() {
        List<String> before = failingFlush();
        recording.failNow = false;
        sessions.leave("kid");
        assertEquals(0, recording.sent.size());
        assertEquals(0, recording.sentNow.size());
        assertEquals(before, store.lines(key(KID)));
    }

    public void testShutdownFlushSkipsKidsWithCopyOff() {
        kids.storeSettings(new KidSettings(KID, false, false));
        join(kid);
        store.append(key(KID), "old line", KidLiveSessions.BUFFER_TTL_SECONDS);
        assertEquals(0, sessions.flushForShutdown(now[0] + 5000));
        assertEquals(0, recording.sent.size());
        assertEquals(0, recording.sentNow.size());
        assertEquals(Arrays.asList("old line"), store.lines(key(KID)));
    }
}
