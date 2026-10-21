package org.pente.gameServer.kids.test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.kids.KidCopyEmails;
import org.pente.gameServer.kids.KidCopyMailer;
import org.pente.gameServer.kids.KidSettings;
import org.pente.message.DSGMessage;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBMessage;

public class KidCopyMailerTest extends TestCase {

    private static final long PARENT = 1;
    private static final long KID = 100;
    private static final long KID2 = 101;
    private static final long STRANGER = 200;
    private static final long PARENT2 = 2;
    private static final long KID3 = 300;
    private static final String PARENT_EMAIL = "parent@example.invalid";

    private MapPlayerStorer players;
    private InMemoryKidStorer kids;
    private RecordingTextMailer mailer;
    private SimpleDSGPlayerData parent;
    private SimpleDSGPlayerData parent2;
    private SimpleDSGPlayerData kidData;

    public KidCopyMailerTest(String name) {
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
        parent = player(PARENT, "parent", null);
        parent.setEmail(PARENT_EMAIL);
        parent.setEmailValid(true);
        players.put(parent);
        kidData = player(KID, "kid", PARENT);
        players.put(kidData);
        players.put(player(KID2, "kid2", PARENT));
        players.put(player(STRANGER, "stranger", null));
        parent2 = player(PARENT2, "parent2", null);
        parent2.setEmail("parent2@example.invalid");
        parent2.setEmailValid(true);
        players.put(parent2);
        players.put(player(KID3, "kid3", PARENT2));
        kids = new InMemoryKidStorer();
        kids.storeSettings(new KidSettings(KID, true, true));
        mailer = new RecordingTextMailer();
    }

    private KidCopyMailer mailer(boolean emailEnabled) {
        return new KidCopyMailer(emailEnabled, mailer, players, kids);
    }

    private static DSGMessage msg(long from, long to) {
        DSGMessage m = new DSGMessage();
        m.setFromPid(from);
        m.setToPid(to);
        m.setSubject("hi");
        m.setBody("hello");
        m.setCreationDate(new Date());
        return m;
    }

    private static final List<String> LINES = Arrays.asList("a", "b");

    public void testNoCopyWhenToggleOff() {
        kids.storeSettings(KidSettings.off(KID));
        mailer(true).copyPrivateMessage(msg(KID, STRANGER));
        assertEquals(0, mailer.sent.size());
    }

    public void testCopyWhenKidSends() {
        mailer(true).copyPrivateMessage(msg(KID, STRANGER));
        assertEquals(1, mailer.sent.size());
        assertEquals(PARENT_EMAIL, mailer.sent.get(0)[1]);
        assertEquals(KidCopyEmails.pmSubject("kid", "stranger", "hi"), mailer.sent.get(0)[2]);
    }

    public void testCopyWhenKidReceives() {
        mailer(true).copyPrivateMessage(msg(STRANGER, KID));
        assertEquals(1, mailer.sent.size());
        assertEquals(PARENT_EMAIL, mailer.sent.get(0)[1]);
        assertEquals(KidCopyEmails.pmSubject("stranger", "kid", "hi"), mailer.sent.get(0)[2]);
    }

    public void testSiblingsShareParentGetOneCopy() {
        kids.storeSettings(new KidSettings(KID2, true, true));
        mailer(true).copyPrivateMessage(msg(KID, KID2));
        assertEquals(1, mailer.sent.size());
    }

    public void testNoCopyWhenParentEmailInvalid() {
        parent.setEmailValid(false);
        mailer(true).copyPrivateMessage(msg(KID, STRANGER));
        assertEquals(0, mailer.sent.size());
    }

    public void testNoCopyWhenEmailDisabled() {
        mailer(false).copyPrivateMessage(msg(KID, STRANGER));
        mailer(false).copyLiveChat(KID, PARENT, LINES);
        assertEquals(0, mailer.sent.size());
    }

    public void testLiveChatCopySent() {
        mailer(true).copyLiveChat(KID, PARENT, LINES);
        assertEquals(1, mailer.sent.size());
        assertEquals(PARENT_EMAIL, mailer.sent.get(0)[1]);
        assertEquals(KidCopyEmails.liveChatSubject("kid"), mailer.sent.get(0)[2]);
        assertEquals(KidCopyEmails.liveChatBody("kid", LINES), mailer.sent.get(0)[3]);
    }

    public void testEmptyLiveChatSendsNothing() {
        mailer(true).copyLiveChat(KID, PARENT, Arrays.<String>asList());
        assertEquals(0, mailer.sent.size());
    }

    public void testStorerErrorDoesNotThrow() {
        players.failLoads = true;
        mailer(true).copyPrivateMessage(msg(KID, STRANGER));
        mailer(true).copyLiveChat(KID, PARENT, LINES);
        kids.failReads = true;
        players.failLoads = false;
        mailer(true).copyPrivateMessage(msg(KID, STRANGER));
        assertEquals(0, mailer.sent.size());
    }

    public void testDestroyIgnoresNonBackgroundMailer() {
        mailer(true).destroy();
    }

    public void testLiveChatNowSendsOnCallerThread() {
        assertTrue(mailer(true).copyLiveChatNow(KID, PARENT, LINES, 1234));
        assertEquals(1, mailer.sentNow.size());
        assertEquals(PARENT_EMAIL, mailer.sentNow.get(0)[1]);
        assertEquals(KidCopyEmails.liveChatSubject("kid"), mailer.sentNow.get(0)[2]);
        assertEquals(0, mailer.sent.size());
        assertEquals(1234, mailer.nowTimeouts.get(0).intValue());
    }

    public void testLiveChatNowSendFailureReturnsFalse() {
        mailer.failNow = true;
        assertTrue(!mailer(true).copyLiveChatNow(KID, PARENT, LINES, 1234));
        assertEquals(0, mailer.sentNow.size());
    }

    public void testLiveChatNowSkippedByRulesReturnsTrue() {
        parent.setEmailValid(false);
        assertTrue(mailer(true).copyLiveChatNow(KID, PARENT, LINES, 1234));
        assertEquals(0, mailer.sent.size());
        assertEquals(0, mailer.sentNow.size());
    }

    private static TBMessage tbMsg(int moveNum, long pid, String text) {
        TBMessage m = new TBMessage();
        m.setMoveNum(moveNum);
        m.setSeqNbr(1);
        m.setPid(pid);
        m.setMessage(text);
        m.setDate(new Date(1792418580000L + moveNum * 60000L));
        return m;
    }

    private TBGame tbGame(long p1, long p2, int moves, boolean withMessages) {
        kids.storeSettings(new KidSettings(KID, false, false, true));
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setGid(50000000001234L);
        g.setPlayer1Pid(p1);
        g.setPlayer2Pid(p2);
        for (int i = 0; i < moves; i++) {
            g.addMove(180 + i);
        }
        if (withMessages) {
            g.addMessage(tbMsg(0, STRANGER, "hello from stranger"));
            g.addMessage(tbMsg(1, p1, "hi"));
            g.addMessage(tbMsg(2, STRANGER, "gg"));
        }
        return g;
    }

    public void testTbTranscriptSent() {
        mailer(true).copyTbTranscript(tbGame(KID, STRANGER, 2, true));
        assertEquals(1, mailer.sent.size());
        assertEquals(PARENT_EMAIL, mailer.sent.get(0)[1]);
        assertEquals(KidCopyEmails.tbSubject("kid", "stranger",
                GridStateFactory.getGameName(GridStateFactory.TB_PENTE)), mailer.sent.get(0)[2]);
        String body = mailer.sent.get(0)[3];
        int a = body.indexOf("stranger: hello from stranger");
        int b = body.indexOf("kid: hi");
        int c = body.indexOf("stranger: gg");
        assertTrue(a >= 0 && b > a && c > b);
        assertTrue(body.contains("gid=50000000001234&command=load"));
    }

    public void testTbTranscriptToggleOffNoMail() {
        TBGame g = tbGame(KID, STRANGER, 2, true);
        kids.storeSettings(new KidSettings(KID, true, true, false));
        mailer(true).copyTbTranscript(g);
        assertEquals(0, mailer.sent.size());
    }

    public void testTbTranscriptUnstartedNoMail() {
        mailer(true).copyTbTranscript(tbGame(KID, STRANGER, 1, true));
        assertEquals(0, mailer.sent.size());
    }

    public void testTbTranscriptNoMessagesNoMail() {
        mailer(true).copyTbTranscript(tbGame(KID, STRANGER, 2, false));
        assertEquals(0, mailer.sent.size());
    }

    public void testTbTranscriptReleasedKidNoMail() {
        kidData.setParentPid(null);
        mailer(true).copyTbTranscript(tbGame(KID, STRANGER, 2, true));
        assertEquals(0, mailer.sent.size());
    }

    public void testTbTranscriptBothKidsEachParent() {
        kids.storeSettings(new KidSettings(KID3, false, false, true));
        mailer(true).copyTbTranscript(tbGame(KID, KID3, 2, true));
        assertEquals(2, mailer.sent.size());
        assertEquals(PARENT_EMAIL, mailer.sent.get(0)[1]);
        assertEquals("parent2@example.invalid", mailer.sent.get(1)[1]);
        assertTrue(mailer.sent.get(0)[2].contains("kid vs kid3"));
        assertTrue(mailer.sent.get(1)[2].contains("kid3 vs kid"));
    }

    public void testTbTranscriptSiblingsOneMail() {
        kids.storeSettings(new KidSettings(KID2, false, false, true));
        mailer(true).copyTbTranscript(tbGame(KID, KID2, 2, true));
        assertEquals(1, mailer.sent.size());
    }

    public void testTbTranscriptEmailDisabledNoMail() {
        mailer(false).copyTbTranscript(tbGame(KID, STRANGER, 2, true));
        assertEquals(0, mailer.sent.size());
    }

    public void testTbTranscriptNeverThrows() {
        players.failLoads = true;
        mailer(true).copyTbTranscript(tbGame(KID, STRANGER, 2, true));
        mailer(true).copyTbTranscript(null);
        assertEquals(0, mailer.sent.size());
    }
}
