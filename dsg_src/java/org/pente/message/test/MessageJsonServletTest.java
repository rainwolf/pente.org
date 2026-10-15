package org.pente.message.test;

import junit.framework.TestCase;

import org.pente.message.DSGMessage;
import org.pente.message.MessageJsonServlet;

/**
 * Who may read what through /gameServer/messageJson: a message only by its recipient, and the
 * message it replied to only when the recipient wrote that one to the replier.
 *
 * Run: ant test-one -Dtest=org.pente.message.test.MessageJsonServletTest
 */
public class MessageJsonServletTest extends TestCase {

    private static final long ME = 10;
    private static final long FRIEND = 20;
    private static final long STRANGER = 30;

    public MessageJsonServletTest(String name) {
        super(name);
    }

    private static DSGMessage message(int mid, long from, long to, int replyToMid) {
        DSGMessage m = new DSGMessage();
        m.setMid(mid);
        m.setFromPid(from);
        m.setToPid(to);
        m.setReplyToMid(replyToMid);
        return m;
    }

    public void testMidParsing() {
        assertEquals(42, MessageJsonServlet.parseMid("42"));
        assertEquals(42, MessageJsonServlet.parseMid(" 42 "));
        assertEquals(-1, MessageJsonServlet.parseMid(null));
        assertEquals(-1, MessageJsonServlet.parseMid(""));
        assertEquals(-1, MessageJsonServlet.parseMid("abc"));
        assertEquals(-1, MessageJsonServlet.parseMid("0"));
        assertEquals(-1, MessageJsonServlet.parseMid("-5"));
        assertEquals(-1, MessageJsonServlet.parseMid("99999999999"));
    }

    public void testOnlyRecipientCanView() {
        assertTrue(MessageJsonServlet.canView(message(1, FRIEND, ME, 0), ME));
        assertTrue(!MessageJsonServlet.canView(message(1, FRIEND, STRANGER, 0), ME));
        // the sender cannot read it through this endpoint either
        assertTrue(!MessageJsonServlet.canView(message(1, ME, FRIEND, 0), ME));
        assertTrue(!MessageJsonServlet.canView(null, ME));
    }

    public void testReplyToShownWhenViewerWroteItToReplier() {
        DSGMessage parent = message(5, ME, FRIEND, 0);
        DSGMessage reply = message(6, FRIEND, ME, 5);
        assertTrue(MessageJsonServlet.canViewReplyTo(reply, parent, ME));
    }

    public void testReplyToHiddenWhenViewerDidNotWriteIt() {
        // FRIEND claims to reply to a message STRANGER sent FRIEND
        DSGMessage parent = message(5, STRANGER, FRIEND, 0);
        DSGMessage reply = message(6, FRIEND, ME, 5);
        assertTrue(!MessageJsonServlet.canViewReplyTo(reply, parent, ME));
    }

    public void testReplyToHiddenWhenWrittenToSomeoneElse() {
        // viewer wrote it, but to STRANGER, not to the replier
        DSGMessage parent = message(5, ME, STRANGER, 0);
        DSGMessage reply = message(6, FRIEND, ME, 5);
        assertTrue(!MessageJsonServlet.canViewReplyTo(reply, parent, ME));
    }

    public void testReplyToHiddenWhenNotAReplyOrMissing() {
        DSGMessage parent = message(5, ME, FRIEND, 0);
        assertTrue(!MessageJsonServlet.canViewReplyTo(message(6, FRIEND, ME, 0), parent, ME));
        assertTrue(!MessageJsonServlet.canViewReplyTo(message(6, FRIEND, ME, 5), null, ME));
        // loaded message is not the one replied to
        assertTrue(!MessageJsonServlet.canViewReplyTo(message(6, FRIEND, ME, 7), parent, ME));
    }
}
