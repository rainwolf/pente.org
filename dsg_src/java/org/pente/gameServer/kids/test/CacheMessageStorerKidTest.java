package org.pente.gameServer.kids.test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.kids.KidChatPolicy;
import org.pente.gameServer.kids.KidCopyMailer;
import org.pente.gameServer.kids.KidSettings;
import org.pente.message.CacheMessageStorer;
import org.pente.message.DSGMessage;
import org.pente.message.DSGMessageStorer;
import org.pente.message.KidMessageRefusedException;
import org.pente.turnBased.CacheTBStorer;

public class CacheMessageStorerKidTest extends TestCase {

    private static final long PARENT = 1;
    private static final long KID = 100;
    private static final long STRANGER = 200;
    private static final long FRIEND = 300;

    /** Records createMessage calls; everything else is unsupported. */
    private static class RecordingBase implements DSGMessageStorer {
        final List<DSGMessage> created = new ArrayList<DSGMessage>();

        public void createMessage(DSGMessage message) {
            created.add(message);
        }

        public void createMessage(DSGMessage message, boolean ccSender) {
            created.add(message);
        }

        public void readMessage(DSGMessage message) {
            throw new UnsupportedOperationException();
        }

        public void deleteMessage(int mid) {
            throw new UnsupportedOperationException();
        }

        public DSGMessage getMessage(int mid) {
            throw new UnsupportedOperationException();
        }

        public DSGMessage getMessage(int mid, boolean includeDeleted) {
            throw new UnsupportedOperationException();
        }

        public List<DSGMessage> getMessages(long pid) {
            throw new UnsupportedOperationException();
        }

        public List<DSGMessage> getNextMessages(long pid, long start) {
            throw new UnsupportedOperationException();
        }

        public int getNumNewMessages(long pid) {
            throw new UnsupportedOperationException();
        }
    }

    private RecordingBase base;
    private MapPlayerStorer players;
    private InMemoryKidStorer kids;
    private RecordingTextMailer recording;
    private CacheMessageStorer storer;

    public CacheMessageStorerKidTest(String name) {
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
        base = new RecordingBase();
        players = new MapPlayerStorer();
        SimpleDSGPlayerData parent = player(PARENT, "parent", null);
        parent.setEmail("parent@example.invalid");
        parent.setEmailValid(true);
        players.put(parent);
        players.put(player(KID, "kid", PARENT));
        players.put(player(STRANGER, "stranger", null));
        players.put(player(FRIEND, "friend", null));
        kids = new InMemoryKidStorer();
        kids.storeSettings(new KidSettings(KID, true, false));
        recording = new RecordingTextMailer();
        storer = new CacheMessageStorer(base, false, "localhost", 25, "u", "p", players);
    }

    protected void tearDown() {
        storer.destroy();
    }

    private void withKidSupport() {
        KidChatPolicy refusing = (a, b) ->
            !((a == KID && b == STRANGER) || (a == STRANGER && b == KID));
        storer.setKidSupport(refusing,
            new KidCopyMailer(true, recording, players, kids));
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

    public void testRefusedMessageThrowsAndIsNotStored() throws Exception {
        withKidSupport();
        try {
            storer.createMessage(msg(STRANGER, KID), false);
            fail("expected KidMessageRefusedException");
        } catch (KidMessageRefusedException expected) {
        }
        assertEquals(0, base.created.size());
        assertTrue(recording.sent.isEmpty());
    }

    public void testSystemNoticeBypassesPolicy() throws Exception {
        withKidSupport();
        DSGMessage m = msg(STRANGER, KID);
        m.setSystemNotice(true);
        storer.createMessage(m, false);
        assertEquals(1, base.created.size());
    }

    public void testAllowedKidMessageStoredAndCopied() throws Exception {
        withKidSupport();
        storer.createMessage(msg(KID, FRIEND), false);
        assertEquals(1, base.created.size());
        assertEquals(1, recording.sent.size());
    }

    public void testNoKidSupportIsUnchanged() throws Exception {
        storer.createMessage(msg(STRANGER, KID), false);
        assertEquals(1, base.created.size());
    }

    public void testTbNoticeMessagesBypassPolicy() throws Exception {
        withKidSupport();
        DSGMessage lost = CacheTBStorer.noticeMessage(STRANGER, KID, "You lost", "x");
        DSGMessage won = CacheTBStorer.noticeMessage(KID, STRANGER,
            "Congratulations, you won!", "y");
        assertTrue(lost.isSystemNotice());
        assertTrue(won.isSystemNotice());
        storer.createMessage(lost, false);
        storer.createMessage(won, false);
        assertEquals(2, base.created.size());
    }
}
