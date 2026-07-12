package org.pente.kingOfTheHill;

import java.util.function.Consumer;

import org.apache.log4j.Category;

import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.gameServer.core.DSGPlayerStoreException;

/**
 * King of the Hill ranking update, applied after each finished all-human game
 * on a KotH server. Concentrates the hill mutation, king detection, and table
 * announcement that previously lived inline in ServerTable's end-of-game
 * persistence.
 */
public class KotHRanking {

    private static final Category log4j =
            Category.getInstance(KotHRanking.class.getName());

    private final CacheKOTHStorer kothStorer;
    private final DSGPlayerStorer dsgPlayerStorer;

    public KotHRanking(CacheKOTHStorer kothStorer, DSGPlayerStorer dsgPlayerStorer) {
        this.kothStorer = kothStorer;
        this.dsgPlayerStorer = dsgPlayerStorer;
    }

    /**
     * Record a finished game against the hill for {@code game}.
     *
     * @param decisive whether the game had a winner; a drawn game only updates
     *                 the last-played dates, matching the original guard.
     * @param announce sink for the table system message (king change / update).
     */
    public void recordResult(int game, long winnerPid, long loserPid,
                             boolean decisive, Consumer<String> announce) {

        if (decisive) {
            Hill hill = kothStorer.loadHill(game);
            long oldKingPid = (hill != null) ? hill.getKing() : 0;

            kothStorer.addPlayer(game, winnerPid);
            kothStorer.addPlayer(game, loserPid);
            kothStorer.movePlayersUpDown(game, winnerPid, loserPid);

            if (hill == null) {
                hill = kothStorer.loadHill(game);
            }
            long kingPid = (hill != null) ? hill.getKing() : 0;
            if (kingPid != oldKingPid && kingPid != 0) {
                try {
                    announce.accept("KotH has been updated, all hail King " +
                            dsgPlayerStorer.loadPlayer(kingPid).getName());
                } catch (DSGPlayerStoreException e) {
                    log4j.error("KotHRanking: error getting King: " + e);
                }
            } else {
                announce.accept("KotH has been updated");
            }
        }

        kothStorer.updatePlayerLastGameDate(game, winnerPid);
        kothStorer.updatePlayerLastGameDate(game, loserPid);
    }
}
