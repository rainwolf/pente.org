package org.pente.webdb.dto;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.pente.game.GameData;
import org.pente.game.GridStateFactory;
import org.pente.game.PlayerData;

/**
 * Wire shape for a single game's header — the row rendered EVERYWHERE games are
 * listed (search results, a game's detail header, later "my games" lists). The
 * field names here are a binding frontend contract; do not rename them.
 *
 * <pre>
 * {"gid": 123456, "source": "archive", "game": 1,
 *  "player1": {"name": "alice", "rating": 1810},
 *  "player2": {"name": "bob",   "rating": 1720},
 *  "winner": 1, "site": "Pente.org", "event": "...", "round": "1", "section": "",
 *  "playDate": "2019-05-01T12:00:00Z", "moveCount": 42}
 * </pre>
 *
 * {@code playDate} is ISO 8601 in UTC ({@code yyyy-MM-dd'T'HH:mm:ss'Z'}) and is
 * {@code null} when the game carries no date. {@code winner} follows
 * {@link GameData}'s constants ({@code 0} unknown, {@code 1}/{@code 2} a player,
 * {@code 3} draw).
 */
public class GameHeader {

    public long gid;
    public String source;
    public int game;
    public Player player1;
    public Player player2;
    public int winner;
    public String site;
    public String event;
    public String round;
    public String section;
    public String playDate;
    public int moveCount;

    /** A player as it appears inside a {@link GameHeader}. */
    public static class Player {
        public String name;
        public int rating;

        public Player() {
        }

        public Player(String name, int rating) {
            this.name = name;
            this.rating = rating;
        }
    }

    /**
     * Map a hydrated {@link GameData} (from {@code loadGame}/{@code loadGames})
     * onto the wire header. {@code game} is the variant id in the caller's
     * request; {@code source} is where the game came from (e.g. {@code
     * "archive"}).
     */
    public static GameHeader from(GameData gd, int game, String source) {
        GameHeader h = new GameHeader();
        h.gid = gd.getGameID();
        h.source = source;
        h.game = game;
        h.player1 = player(gd.getPlayer1Data());
        h.player2 = player(gd.getPlayer2Data());
        h.winner = gd.getWinner();
        h.site = gd.getSite();
        h.event = gd.getEvent();
        h.round = gd.getRound();
        h.section = gd.getSection();
        h.playDate = isoUtc(gd.getDate());
        h.moveCount = gd.getNumMoves();
        return h;
    }

    /**
     * Convenience overload: recover the variant id from the loaded game's own
     * name via {@link GridStateFactory#getGameId(String)} (used by the
     * single-game load path, which does not carry the request's variant id).
     */
    public static GameHeader from(GameData gd, String source) {
        return from(gd, GridStateFactory.getGameId(gd.getGame()), source);
    }

    /**
     * Map a personal-collection {@link WebDbGameData} row onto the wire header.
     * The {@code source} is always {@code "mine"} and {@code gid} is the row's
     * {@code wgid}. Personal games store player names as plain strings with no
     * rating (rating {@code 0}). {@code moveCount} reflects the reconstructed
     * move list when present (the single-game GET path) and is {@code 0} on the
     * header-only list path, where {@code WebDbGameData.moves} is left null.
     */
    public static GameHeader fromWebDb(WebDbGameData g) {
        GameHeader h = new GameHeader();
        h.gid = g.wgid;
        h.source = "mine";
        h.game = g.game;
        h.player1 = new Player(g.player1, 0);
        h.player2 = new Player(g.player2, 0);
        h.winner = g.winner;
        h.site = g.site;
        h.event = g.event;
        h.round = g.round;
        h.section = g.section;
        h.playDate = isoUtc(g.playDate);
        h.moveCount = (g.moves == null) ? 0 : g.moves.length;
        return h;
    }

    private static Player player(PlayerData p) {
        if (p == null) {
            return null;
        }
        return new Player(p.getUserIDName(), p.getRating());
    }

    private static String isoUtc(Date d) {
        if (d == null) {
            return null;
        }
        // SimpleDateFormat is not thread-safe; build one per call.
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(d);
    }
}
