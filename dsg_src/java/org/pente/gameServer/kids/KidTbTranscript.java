package org.pente.gameServer.kids;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.pente.game.GridStateFactory;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBMessage;

/** Helpers for the end-of-game turn-based message transcript. */
public final class KidTbTranscript {

    private KidTbTranscript() {
    }

    /**
     * True once the game has real moves. Games that get an automatic centre stone on
     * accept start after move 1; the games listed here do not (same list as the accept
     * path in CacheTBStorer), so they start on the first move.
     */
    public static boolean started(TBGame g) {
        int opening = 1;
        switch (g.getGame()) {
            case GridStateFactory.TB_DPENTE:
            case GridStateFactory.TB_DKERYO:
            case GridStateFactory.TB_GO:
            case GridStateFactory.TB_GO9:
            case GridStateFactory.TB_GO13:
            case GridStateFactory.TB_SWAP2PENTE:
            case GridStateFactory.TB_SWAP2KERYO:
                opening = 0;
                break;
            default:
                break;
        }
        return g.getNumMoves() > opening;
    }

    /** Messages by moveNum, then seqNbr, then date (null date first). */
    public static List<TBMessage> ordered(TBGame g) {
        List<TBMessage> sorted = new ArrayList<TBMessage>(g.getMessages());
        sorted.sort(Comparator.comparingInt(TBMessage::getMoveNum)
                .thenComparingInt(TBMessage::getSeqNbr)
                .thenComparingLong(m -> m.getDate() == null ? 0L : m.getDate().getTime()));
        return sorted;
    }
}
