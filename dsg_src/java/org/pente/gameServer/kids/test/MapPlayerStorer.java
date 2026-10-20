package org.pente.gameServer.kids.test;

import java.util.HashMap;
import java.util.Map;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.turnBased.test.InMemoryDSGPlayerStorer;

/** Test fake: players kept in a map, with a load-failure switch. */
public class MapPlayerStorer extends InMemoryDSGPlayerStorer {

    private final Map<Long, DSGPlayerData> players = new HashMap<Long, DSGPlayerData>();
    public boolean failLoads;

    public void put(DSGPlayerData d) {
        players.put(d.getPlayerID(), d);
    }

    private void checkLoads() throws DSGPlayerStoreException {
        if (failLoads) {
            throw new DSGPlayerStoreException("load failed (test)");
        }
    }

    public DSGPlayerData loadPlayer(long playerID) throws DSGPlayerStoreException {
        checkLoads();
        return players.get(playerID);
    }

    public DSGPlayerData loadPlayer(String name) throws DSGPlayerStoreException {
        checkLoads();
        for (DSGPlayerData d : players.values()) {
            if (d.getName() != null && d.getName().equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }

    public void insertPlayer(DSGPlayerData d) {
        put(d);
    }

    public void updatePlayer(DSGPlayerData d) {
        put(d);
    }
}
