package org.pente.gameServer.kids.test;

import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.kids.KidTbTranscript;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBMessage;

public class KidTbTranscriptTest extends TestCase {

    public KidTbTranscriptTest(String name) {
        super(name);
    }

    private static TBGame game(int type, int moves) {
        TBGame g = new TBGame();
        g.setGame(type);
        for (int i = 0; i < moves; i++) {
            g.addMove(180 + i);
        }
        return g;
    }

    private static TBMessage msg(int moveNum, int seq, long time) {
        TBMessage m = new TBMessage();
        m.setMoveNum(moveNum);
        m.setSeqNbr(seq);
        m.setDate(new Date(time));
        return m;
    }

    public void testPenteStartsAfterCentre() {
        assertTrue(!KidTbTranscript.started(game(GridStateFactory.TB_PENTE, 1)));
        assertTrue(KidTbTranscript.started(game(GridStateFactory.TB_PENTE, 2)));
    }

    public void testOpeningGamesStartOnFirstMove() {
        int[] ids = {GridStateFactory.TB_DPENTE, GridStateFactory.TB_DKERYO,
                GridStateFactory.TB_GO, GridStateFactory.TB_GO9, GridStateFactory.TB_GO13,
                GridStateFactory.TB_SWAP2PENTE, GridStateFactory.TB_SWAP2KERYO};
        for (int id : ids) {
            assertTrue(!KidTbTranscript.started(game(id, 0)));
            assertTrue(KidTbTranscript.started(game(id, 1)));
        }
    }

    public void testOrderedByMoveSeqThenDate() {
        TBGame g = game(GridStateFactory.TB_PENTE, 2);
        g.addMessage(msg(3, 1, 1));
        g.addMessage(msg(0, 1, 1));
        g.addMessage(msg(3, 2, 1));
        g.addMessage(msg(1, 1, 1));
        List<TBMessage> o = KidTbTranscript.ordered(g);
        int[][] want = {{0, 1}, {1, 1}, {3, 1}, {3, 2}};
        for (int i = 0; i < want.length; i++) {
            assertEquals(want[i][0], o.get(i).getMoveNum());
            assertEquals(want[i][1], o.get(i).getSeqNbr());
        }
        TBGame g2 = game(GridStateFactory.TB_PENTE, 2);
        g2.addMessage(msg(2, 1, 10 * 3600000L));
        g2.addMessage(msg(2, 1, 9 * 3600000L));
        List<TBMessage> o2 = KidTbTranscript.ordered(g2);
        assertEquals(9 * 3600000L, o2.get(0).getDate().getTime());
    }
}
