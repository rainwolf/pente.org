package org.pente.turnBased.test;

import junit.framework.*;
import org.pente.game.GridStateFactory;
import org.pente.turnBased.*;

import java.util.*;

public class TBMessageThreadTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{TBMessageThreadTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(TBMessageThreadTest.class);
    }

    public TBMessageThreadTest(String name) {
        super(name);
    }

    private static final long A = 11L, B = 22L;

    private TBGame game(int type) {
        TBGame g = new TBGame();
        g.setGame(type);
        g.setPlayer1Pid(A);
        g.setPlayer2Pid(B);
        return g;
    }

    private TBMessage msg(int moveNum, int seq, long pid, String text, long date) {
        TBMessage m = new TBMessage();
        m.setMoveNum(moveNum);
        m.setSeqNbr(seq);
        m.setPid(pid);
        m.setMessage(text);
        m.setDate(new Date(date));
        return m;
    }

    private List<TBMessageThread.Entry> entries(TBGame g) {
        return TBMessageThread.entries(g, TBMessage::getMessage, "alice", "bob", "%s: ", "\n");
    }

    public void testNextSeqNbrEmptyIsOne() {
        assertEquals(1, TBMessageThread.nextSeqNbr(new ArrayList<>(), 3));
    }

    public void testNextSeqNbrFollowsHighestAtThatMove() {
        List<TBMessage> ms = Arrays.asList(msg(3, 1, A, "x", 1), msg(3, 2, B, "y", 2), msg(4, 7, A, "z", 3));
        assertEquals(3, TBMessageThread.nextSeqNbr(ms, 3));
        assertEquals(8, TBMessageThread.nextSeqNbr(ms, 4));
        assertEquals(1, TBMessageThread.nextSeqNbr(ms, 5));
    }

    public void testPlainGameSingleMessagesUnchanged() {
        TBGame g = game(GridStateFactory.TB_PENTE);
        g.addMessage(msg(1, 1, A, "hi", 10));
        g.addMessage(msg(2, 1, B, "hey", 20));
        List<TBMessageThread.Entry> es = entries(g);
        assertEquals(2, es.size());
        assertEquals("hi", es.get(0).text);
        assertEquals(1, es.get(0).authorSeat);
        assertEquals("hey", es.get(1).text);
        assertEquals(2, es.get(1).authorSeat);
    }

    public void testPlainGameOffParityMessageNotNamed() {
        // outside the opening variants parity is never checked
        TBGame g = game(GridStateFactory.TB_PENTE);
        g.addMessage(msg(2, 1, A, "odd", 10));
        TBMessageThread.Entry e = entries(g).get(0);
        assertEquals("odd", e.text);
        assertEquals(1, e.authorSeat);
    }

    public void testCollisionMergedInSeqOrder() {
        TBGame g = game(GridStateFactory.TB_PENTE);
        g.addMessage(msg(3, 2, B, "second", 20));
        g.addMessage(msg(3, 1, A, "first", 30));
        List<TBMessageThread.Entry> es = entries(g);
        assertEquals(1, es.size());
        assertEquals(0, es.get(0).authorSeat);
        assertEquals("alice: first\nbob: second", es.get(0).text);
        assertEquals(1, es.get(0).seqNbr);
        assertEquals(30L, es.get(0).date);
    }

    public void testOpeningCollisionMergedInSeqOrder() {
        // swap2: A's start message and B's swap decision both at stone 3
        TBGame g = game(GridStateFactory.TB_SWAP2PENTE);
        g.addMessage(msg(3, 1, A, "good luck", 10));
        g.addMessage(msg(3, 2, B, "I'll take black", 20));
        TBMessageThread.Entry e = entries(g).get(0);
        assertEquals(0, e.authorSeat);
        assertEquals("alice: good luck\nbob: I'll take black", e.text);
    }

    public void testOpeningOffParitySingleMessageNamed() {
        // safety net: any lone opening message whose author does not hold the
        // parity seat of its stone is named (e.g. one written before a later swap)
        TBGame g = game(GridStateFactory.TB_DPENTE);
        g.addMessage(msg(3, 1, B, "before the swap", 10));
        TBMessageThread.Entry e = entries(g).get(0);
        assertEquals(0, e.authorSeat);
        assertEquals("bob: before the swap", e.text);
    }

    public void testRenjuTakeOverMessageKeepsTakerSeat() {
        // TBGame.renjuSwap swaps the pids: after B takes over at stone 3, B is seat 1
        TBGame g = game(GridStateFactory.TB_RENJU);
        g.setPlayer1Pid(B);
        g.setPlayer2Pid(A);
        g.addMessage(msg(3, 1, B, "mine now", 10));
        TBMessageThread.Entry e = entries(g).get(0);
        assertEquals(1, e.authorSeat);
        assertEquals("mine now", e.text);
    }

    public void testInviteMessagesAtMoveZeroMerged() {
        // inviter (seq 1, NewGameServlet) and invitee (seq 2, ReplyInvitationServlet)
        TBGame g = game(GridStateFactory.TB_PENTE);
        g.addMessage(msg(0, 1, A, "want a game?", 10));
        g.addMessage(msg(0, 2, B, "sure", 20));
        TBMessageThread.Entry e = entries(g).get(0);
        assertEquals(0, e.moveNum);
        assertEquals(0, e.authorSeat);
        assertEquals("alice: want a game?\nbob: sure", e.text);
    }

    public void testOpeningStoneMessageNotNamed() {
        TBGame g = game(GridStateFactory.TB_DPENTE);
        g.addMessage(msg(5, 1, A, "stone five", 10));
        TBMessageThread.Entry e = entries(g).get(0);
        assertEquals(1, e.authorSeat);
        assertEquals("stone five", e.text);
    }

    public void testSortedByMoveNum() {
        TBGame g = game(GridStateFactory.TB_PENTE);
        g.addMessage(msg(5, 1, A, "five", 10));
        g.addMessage(msg(2, 1, B, "two", 20));
        List<TBMessageThread.Entry> es = entries(g);
        assertEquals(2, es.get(0).moveNum);
        assertEquals(5, es.get(1).moveNum);
    }

    public void testEncoderAppliedPerPartNotToNames() {
        TBGame g = game(GridStateFactory.TB_PENTE);
        g.addMessage(msg(3, 1, A, "a", 10));
        g.addMessage(msg(3, 2, B, "b", 20));
        String text = TBMessageThread.entries(g, m -> m.getMessage().toUpperCase(),
                "alice", "bob", "<b>%s</b>: ", "<br>").get(0).text;
        assertEquals("<b>alice</b>: A<br><b>bob</b>: B", text);
    }

    public void testNoMessagesNoEntries() {
        assertTrue(entries(game(GridStateFactory.TB_PENTE)).isEmpty());
    }
}
