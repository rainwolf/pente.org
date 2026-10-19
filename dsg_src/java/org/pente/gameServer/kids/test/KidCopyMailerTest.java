package org.pente.gameServer.kids.test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.kids.KidCopyEmails;
import org.pente.gameServer.kids.KidCopyMailer;
import org.pente.gameServer.kids.KidSettings;
import org.pente.message.DSGMessage;

public class KidCopyMailerTest extends TestCase {

    private static final long PARENT = 1;
    private static final long KID = 100;
    private static final long KID2 = 101;
    private static final long STRANGER = 200;
    private static final String PARENT_EMAIL = "parent@example.invalid";

    private MapPlayerStorer players;
    private InMemoryKidStorer kids;
    private RecordingTextMailer mailer;
    private SimpleDSGPlayerData parent;

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
        players.put(player(KID, "kid", PARENT));
        players.put(player(KID2, "kid2", PARENT));
        players.put(player(STRANGER, "stranger", null));
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
}
