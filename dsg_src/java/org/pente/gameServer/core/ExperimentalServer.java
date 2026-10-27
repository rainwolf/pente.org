package org.pente.gameServer.core;

import org.pente.game.Game;
import org.pente.game.GameEventData;
import org.pente.game.GridStateFactory;

/**
 * Single source of truth for the Experimental server: its identity and the
 * rule for which games a table on any server may play.
 *
 * Only Experimental offers Tombstone and Experimental offers only Tombstone,
 * so one predicate over a server's in-memory game events enforces both
 * directions. It reads only {@link ServerData#getGameEvents()}, never a DB
 * lookup by server id (the Arena, id 0, holds an in-memory copy of server 1's
 * events).
 */
public final class ExperimentalServer {

    public static final int ID = 47;
    public static final String NAME = "Experimental";
    public static final int PORT = 16004;
    public static final int[] GAMES = {GridStateFactory.TOMBSTONE, GridStateFactory.SPEED_TOMBSTONE};

    private ExperimentalServer() {
    }

    public static boolean isExperimental(ServerData sd) {
        return sd != null && sd.getServerId() == ID && NAME.equals(sd.getName());
    }

    public static boolean isExperimentalRoom(String roomName) {
        return NAME.equals(roomName);
    }

    public static boolean acceptsNativeSocket(ServerData sd) {
        return !isExperimental(sd);
    }

    private static boolean isTombstone(int gameId) {
        for (int g : GAMES) {
            if (g == gameId) {
                return true;
            }
        }
        return false;
    }

    public static boolean gameAllowed(ServerData sd, int gameId) {
        if (sd == null || sd.isTournament() || sd.getGameEvents().isEmpty()) {
            return !isTombstone(gameId);
        }
        for (GameEventData e : sd.getGameEvents()) {
            if (e.getGame() == gameId) {
                return true;
            }
        }
        return false;
    }

    public static Game defaultGame(ServerData sd) {
        int best = -1;
        if (sd != null) {
            for (GameEventData e : sd.getGameEvents()) {
                int id = e.getGame();
                if (!GridStateFactory.getGame(id).isSpeed() && (best < 0 || id < best)) {
                    best = id;
                }
            }
        }
        return best < 0 ? GridStateFactory.PENTE_GAME : GridStateFactory.getGame(best);
    }
}
