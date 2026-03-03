package org.pente.gameServer.mobile;

import org.pente.turnBased.TBGame;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializes the AI index response (aiIndex.jsp).
 * Lists the game IDs where it is a specific player's turn.
 */
public class AiIndexResponse {

    public final List<Long> myTurnGames;

    private AiIndexResponse(List<Long> myTurnGames) {
        this.myTurnGames = myTurnGames;
    }

    public static AiIndexResponse build(List<TBGame> myTurn) {
        List<Long> gids = new ArrayList<>();
        for (TBGame g : myTurn) {
            gids.add(g.getGid());
        }
        return new AiIndexResponse(gids);
    }

    public static AiIndexResponse error() {
        return new AiIndexResponse(null);
    }
}