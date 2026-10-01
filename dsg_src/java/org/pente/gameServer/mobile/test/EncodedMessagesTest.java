package org.pente.gameServer.mobile.test;

import junit.framework.*;
import org.pente.game.GridStateFactory;
import org.pente.gameServer.mobile.GameResponse;
import org.pente.turnBased.*;

import java.util.Date;

public class EncodedMessagesTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{EncodedMessagesTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(EncodedMessagesTest.class);
    }

    public EncodedMessagesTest(String name) {
        super(name);
    }

    private TBGame game(int type) {
        TBGame g = new TBGame();
        g.setGame(type);
        g.setPlayer1Pid(11L);
        g.setPlayer2Pid(22L);
        return g;
    }

    private void add(TBGame g, int moveNum, int seq, long pid, String text) {
        TBMessage m = new TBMessage();
        m.setMoveNum(moveNum);
        m.setSeqNbr(seq);
        m.setPid(pid);
        m.setMessage(text);
        m.setDate(new Date(1000L * moveNum + seq));
        g.addMessage(m);
    }

    // stands in for MessageEncoder: escapes commas the way the apps expect
    private static String enc(TBMessage m) {
        return m.getMessage().replace(",", "\\1");
    }

    public void testPlainGameUnchanged() {
        TBGame g = game(GridStateFactory.TB_PENTE);
        add(g, 1, 1, 11L, "hi");
        add(g, 2, 1, 22L, "yo");
        GameResponse.EncodedMessages e = GameResponse.EncodedMessages.from(g, EncodedMessagesTest::enc, "alice", "bob");
        assertEquals("hi,yo", e.messages);
        assertEquals("1,2", e.moveNums);
        assertEquals("1,2", e.players);
        assertEquals("1,2", e.authors);
    }

    public void testMergedEntryStaysOneCommaField() {
        TBGame g = game(GridStateFactory.TB_SWAP2PENTE);
        add(g, 3, 1, 11L, "a, b");
        add(g, 3, 2, 22L, "it's c");
        GameResponse.EncodedMessages e = GameResponse.EncodedMessages.from(g, EncodedMessagesTest::enc, "alice", "bob");
        assertEquals("alice: a\\1 b\nbob: it's c", e.messages);
        assertEquals(1, e.messages.split(",").length);
        assertEquals("3", e.moveNums);
        assertEquals("0", e.authors);
        assertEquals("1", e.players);
    }

    public void testTwoArgOverloadUnchanged() {
        TBGame g = game(GridStateFactory.TB_SWAP2PENTE);
        add(g, 3, 1, 11L, "x");
        add(g, 3, 2, 22L, "y");
        GameResponse.EncodedMessages e = GameResponse.EncodedMessages.from(g, EncodedMessagesTest::enc);
        assertEquals("x,y", e.messages);
        assertEquals("3,3", e.moveNums);
        assertEquals("1,2", e.authors);
    }

    public void testConnect6OffsetKept() {
        TBGame g = game(GridStateFactory.TB_CONNECT6);
        add(g, 1, 1, 11L, "c6");
        GameResponse.EncodedMessages e = GameResponse.EncodedMessages.from(g, EncodedMessagesTest::enc, "alice", "bob");
        assertEquals("3", e.moveNums);
    }
}
