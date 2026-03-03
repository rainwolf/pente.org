package org.pente.gameServer.mobile;

import org.pente.game.GridStateFactory;
import org.pente.turnBased.TBGame;

/**
 * Serializes a single AI game (aiGame.jsp).
 */
public class AiGameResponse {

    public final String gid;
    public final String moves;
    public final int difficulty;
    public final String gameName;

    private AiGameResponse(String gid, String moves, int difficulty, String gameName) {
        this.gid = gid;
        this.moves = moves;
        this.difficulty = difficulty;
        this.gameName = gameName;
    }

    public static AiGameResponse build(TBGame tbGame) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tbGame.getNumMoves(); i++) {
            if (i > 0) sb.append(',');
            sb.append(tbGame.getMove(i));
        }
        String gameName = GridStateFactory.getGameName(tbGame.getGame())
                + (tbGame.isRated() ? "-Rated" : "");
        return new AiGameResponse(
                String.valueOf(tbGame.getGid()),
                sb.toString(),
                tbGame.getRound(),
                gameName
        );
    }
}