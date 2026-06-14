package org.pente.gameServer.mobile;

import org.pente.game.*;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBMessage;
import org.pente.turnBased.TBSet;

/**
 * Serializes a single turn-based game view (game.jsp).
 *
 * <p>Covers both active {@link TBGame} instances and completed historic games
 * loaded from the {@link GameStorer}.
 */
public class GameResponse {

    // ── fields present for both TBGame and historic game ──────────────────────
    public final String gid;
    public final String privateGame;   // "private" | "non-private"
    public final String rated;         // "Rated" | "Not Rated"
    public final String gameName;
    public final String moves;
    public final PlayerRef player1;
    public final PlayerRef player2;
    public final String messages;
    public final String messageNums;

    // ── TBGame-only fields (null for historic games) ───────────────────────────
    public final Long sid;
    public final String currentPlayer;
    public final String seqNums;
    public final String dates;
    public final String players;       // "1" or "2" per message
    public final String state;         // "active" | "inactive"
    public final String goState;       // null | "MARK_DEAD_STONES" | "EVALUATE_DEAD_STONES"
    public final Boolean undoRequested;
    public final Boolean canHide;
    public final Boolean canUnHide;
    public final CancelInfo cancel;
    public final String dPenteState;   // non-null for dPente/swap2 variants
    public final Boolean swap2pass;
    public final String renjuPhase;    // TB_RENJU only: SWAP|BRANCH|OFFERS|SELECTION|MOVE|COMPLETE, else null
    public final String renjuOffers;   // TB_RENJU Branch B: comma-separated offered moves, else null
    public final Integer renjuSwaps;   // TB_RENJU: packed opening word, else null

    public static class PlayerRef {
        public final String name;
        public final int rating;

        PlayerRef(String name, int rating) {
            this.name = name;
            this.rating = rating;
        }
    }

    public static class CancelInfo {
        public final String name;
        public final String message;

        CancelInfo(String name, String message) {
            this.name = name;
            this.message = message;
        }
    }

    // Private constructor used by both factory methods
    private GameResponse(String gid, String privateGame, String rated, String gameName,
                         String moves, PlayerRef player1, PlayerRef player2,
                         String messages, String messageNums,
                         Long sid, String currentPlayer, String seqNums,
                         String dates, String players, String state, String goState,
                         Boolean undoRequested, Boolean canHide, Boolean canUnHide,
                         CancelInfo cancel, String dPenteState, Boolean swap2pass,
                         String renjuPhase, String renjuOffers, Integer renjuSwaps) {
        this.gid = gid;
        this.privateGame = privateGame;
        this.rated = rated;
        this.gameName = gameName;
        this.moves = moves;
        this.player1 = player1;
        this.player2 = player2;
        this.messages = messages;
        this.messageNums = messageNums;
        this.sid = sid;
        this.currentPlayer = currentPlayer;
        this.seqNums = seqNums;
        this.dates = dates;
        this.players = players;
        this.state = state;
        this.goState = goState;
        this.undoRequested = undoRequested;
        this.canHide = canHide;
        this.canUnHide = canUnHide;
        this.cancel = cancel;
        this.dPenteState = dPenteState;
        this.swap2pass = swap2pass;
        this.renjuPhase = renjuPhase;
        this.renjuOffers = renjuOffers;
        this.renjuSwaps = renjuSwaps;
    }

    /**
     * Builds a response for an active or completed turn-based game.
     *
     * @param tbGame      the game to serialize
     * @param visitor     the requesting player (controls message visibility)
     * @param storer      player storer for loading player names / ratings
     * @param encodedMsgs pre-encoded message strings (apply filters before calling)
     */
    public static GameResponse build(TBGame tbGame, DSGPlayerData visitor,
                                     DSGPlayerStorer storer, EncodedMessages encodedMsgs) throws DSGPlayerStoreException {
        TBSet set = tbGame.getTbSet();
        DSGPlayerData player1 = storer.loadPlayer(tbGame.getPlayer1Pid());
        DSGPlayerData player2 = storer.loadPlayer(tbGame.getPlayer2Pid());
        DSGPlayerGameData p1Data = player1.getPlayerGameData(tbGame.getGame());
        DSGPlayerGameData p2Data = player2.getPlayerGameData(tbGame.getGame());

        // Moves
        StringBuilder movesBuilder = new StringBuilder();
        for (int i = 0; i < tbGame.getNumMoves(); i++) {
            if (i > 0) movesBuilder.append(',');
            movesBuilder.append(tbGame.getMove(i));
        }

        // Current player name
        String currentPlayerName = tbGame.getCurrentPlayer() == player1.getPlayerID()
                ? player1.getName() : player2.getName();

        // Go state
        String goState = null;
        if (tbGame.getGoState() == TBGame.GO_MARK_DEAD_STONES) {
            goState = "MARK_DEAD_STONES";
        } else if (tbGame.getGoState() == TBGame.GO_EVALUATE_DEAD_STONES) {
            goState = "EVALUATE_DEAD_STONES";
        }

        // Cancel info
        CancelInfo cancelInfo = null;
        if (set.getCancelPid() != 0) {
            DSGPlayerData cancelPlayer = storer.loadPlayer(set.getCancelPid());
            cancelInfo = new CancelInfo(
                    cancelPlayer.getName(),
                    set.getCancelMsg().replace("\\2", "'")
            );
        }

        // dPente / swap2 variants
        boolean isDPente = !tbGame.isCompleted()
                && (tbGame.getGame() == GridStateFactory.TB_DPENTE
                || tbGame.getGame() == GridStateFactory.TB_DKERYO
                || tbGame.getGame() == GridStateFactory.TB_SWAP2PENTE
                || tbGame.getGame() == GridStateFactory.TB_SWAP2KERYO);

        boolean isRenju = !tbGame.isCompleted()
                && tbGame.getGame() == GridStateFactory.TB_RENJU;
        String renjuPhase = isRenju ? tbGame.getRenjuPhase() : null;
        String renjuOffersStr = null;
        if (isRenju && tbGame.getRenjuOffers() != null) {
            StringBuilder ro = new StringBuilder();
            int[] offers = tbGame.getRenjuOffers();
            for (int i = 0; i < offers.length; i++) {
                if (i > 0) ro.append(',');
                ro.append(offers[i]);
            }
            renjuOffersStr = ro.toString();
        }
        Integer renjuSwaps = isRenju ? Integer.valueOf(tbGame.getRenjuSwaps()) : null;

        return new GameResponse(
                String.valueOf(tbGame.getGid()),
                (set.isPrivateGame() ? "" : "non-") + "private",
                (tbGame.isRated() ? "" : "Not ") + "Rated",
                GridStateFactory.getGameName(tbGame.getGame()),
                movesBuilder.toString(),
                new PlayerRef(player1.getName(), p1Data != null ? (int) p1Data.getRating() : 1600),
                new PlayerRef(player2.getName(), p2Data != null ? (int) p2Data.getRating() : 1600),
                encodedMsgs != null ? encodedMsgs.messages : "",
                encodedMsgs != null ? encodedMsgs.moveNums : "",
                set.getSetId(),
                currentPlayerName,
                encodedMsgs != null ? encodedMsgs.seqNums : "",
                encodedMsgs != null ? encodedMsgs.dates : "",
                encodedMsgs != null ? encodedMsgs.players : "",
                tbGame.getState() == TBGame.STATE_ACTIVE ? "active" : "inactive",
                goState,
                tbGame.isUndoRequested(),
                tbGame.canHide(visitor.getPlayerID()),
                tbGame.canUnHide(visitor.getPlayerID()),
                cancelInfo,
                isDPente ? String.valueOf(tbGame.getDPenteState()) : null,
                isDPente ? tbGame.didSwap2Pass() : null,
                renjuPhase, renjuOffersStr, renjuSwaps
        );
    }

    /**
     * Builds a response for a completed historic game loaded from the game storer.
     *
     * @param gid        game ID string from the request
     * @param gameStorer storer to load the historic game from
     */
    public static GameResponse buildHistoric(String gid, GameStorer gameStorer) throws Exception {
        GameData game = new DefaultGameData();
        gameStorer.loadGame(Long.parseLong(gid), game);
        PlayerData p1 = game.getPlayer1Data(), p2 = game.getPlayer2Data();

        StringBuilder movesBuilder = new StringBuilder();
        int[] gameMoves = game.getMoves();
        for (int i = 0; i < gameMoves.length; i++) {
            if (i > 0) movesBuilder.append(',');
            movesBuilder.append(gameMoves[i]);
        }

        return new GameResponse(
                gid,
                (game.isPrivateGame() ? "" : "non-") + "private",
                (game.getRated() ? "" : "Not ") + "Rated",
                String.valueOf(game.getGame()),
                movesBuilder.toString(),
                new PlayerRef(p1.getUserIDName(), (int) p1.getRating()),
                new PlayerRef(p2.getUserIDName(), (int) p2.getRating()),
                "", "",
                null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null
        );
    }

    /**
     * Pre-encoded game messages produced by a caller who has applied the JSP filter chain.
     * Build one of these by iterating {@link TBGame#getMessages()} and encoding each message.
     */
    public static class EncodedMessages {
        public final String messages;   // comma-separated encoded message texts
        public final String moveNums;   // comma-separated move numbers
        public final String seqNums;    // comma-separated sequence numbers
        public final String dates;      // comma-separated epoch millis
        public final String players;    // comma-separated "1" or "2"

        public EncodedMessages(String messages, String moveNums, String seqNums,
                               String dates, String players) {
            this.messages = messages;
            this.moveNums = moveNums;
            this.seqNums = seqNums;
            this.dates = dates;
            this.players = players;
        }

        /**
         * Convenience builder from raw {@link TBMessage} list.
         * The caller is responsible for encoding message text (applying the JSP filter chain).
         */
        public static EncodedMessages from(TBGame tbGame,
                                           java.util.function.Function<TBMessage, String> encoder) {
            StringBuilder msgs = new StringBuilder();
            StringBuilder nums = new StringBuilder();
            StringBuilder seqs = new StringBuilder();
            StringBuilder dts = new StringBuilder();
            StringBuilder plrs = new StringBuilder();

            boolean first = true;
            for (TBMessage m : tbGame.getMessages()) {
                if (!first) {
                    msgs.append(',');
                    nums.append(',');
                    seqs.append(',');
                    dts.append(',');
                    plrs.append(',');
                }
                first = false;
                msgs.append(encoder.apply(m));
                nums.append(m.getMoveNum()
                        + (tbGame.getGame() == GridStateFactory.TB_CONNECT6 ? 2 : 0));
                seqs.append(m.getSeqNbr());
                dts.append(m.getDate().getTime());
                plrs.append(tbGame.getPlayer1Pid() == m.getPid() ? "1" : "2");
            }

            return new EncodedMessages(
                    msgs.toString().replace("\\2", "'"),
                    nums.toString(),
                    seqs.toString(),
                    dts.toString(),
                    plrs.toString()
            );
        }
    }
}