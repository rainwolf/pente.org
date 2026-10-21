package org.pente.gameServer.kids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBSet;

/** The active turn-based games of a kid, for the parent's list (GET /gameServer/kids?action=activeGames). */
public final class KidActiveGames {

    private KidActiveGames() {
    }

    /** Field names are the JSON keys of the API contract. */
    public static final class Entry {
        public final long gid;
        public final int game;
        public final String gameName;
        public final long opponentPid;
        public final String opponentName;
        public final boolean kidsTurn;
        public final long lastMoveDate;
        public final boolean privateGame;

        Entry(long gid, int game, String gameName, long opponentPid, String opponentName,
              boolean kidsTurn, long lastMoveDate, boolean privateGame) {
            this.gid = gid;
            this.game = game;
            this.gameName = gameName;
            this.opponentPid = opponentPid;
            this.opponentName = opponentName;
            this.kidsTurn = kidsTurn;
            this.lastMoveDate = lastMoveDate;
            this.privateGame = privateGame;
        }
    }

    /** Active games of the kid, newest last move first, ties by gid ascending. */
    public static List<Entry> build(long kidPid, List<TBSet> sets, DSGPlayerStorer players)
            throws DSGPlayerStoreException {
        List<Entry> list = new ArrayList<Entry>();
        for (TBSet set : sets) {
            for (TBGame g : set.getGames()) {
                if (g == null || g.getState() != TBGame.STATE_ACTIVE
                        || (g.getPlayer1Pid() != kidPid && g.getPlayer2Pid() != kidPid)) {
                    continue;
                }
                long oppPid = g.getOpponent(kidPid);
                DSGPlayerData opp = players.loadPlayer(oppPid);
                if (opp == null) {
                    continue;
                }
                list.add(new Entry(g.getGid(), g.getGame(), GridStateFactory.getGameName(g.getGame()),
                        oppPid, opp.getName(), g.getCurrentPlayer() == kidPid,
                        g.getLastMoveDate() == null ? 0 : g.getLastMoveDate().getTime(),
                        set.isPrivateGame()));
            }
        }
        Collections.sort(list, new Comparator<Entry>() {
            public int compare(Entry a, Entry b) {
                if (a.lastMoveDate != b.lastMoveDate) {
                    return a.lastMoveDate > b.lastMoveDate ? -1 : 1;
                }
                return a.gid < b.gid ? -1 : (a.gid > b.gid ? 1 : 0);
            }
        });
        return list;
    }
}
