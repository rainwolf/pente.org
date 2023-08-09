package org.pente.turnBased;

import java.text.*;

public class CacheStats {

    private static final NumberFormat nf =
            NumberFormat.getPercentInstance();

    private int setsCreated;
    private int setsCompleted;

    private int gamesCreated;
    private int gamesCompleted;
    private int movesMade;

    private int setsCached;
    private int setsUncached;

    private int gamesCached;
    private int gamesUncached;

    private int setLoads;
    private int setLoadsCached;

    private int gameLoads;
    private int gameLoadsCached;


    public synchronized void incrementSetsCreated() {
        setsCreated++;
    }

    public synchronized void incrementSetsCompleted() {
        setsCompleted++;
    }

    public synchronized void incrementGamesCreated() {
        gamesCreated++;
    }

    public synchronized void incrementGamesCompleted() {
        gamesCompleted++;
    }

    public synchronized void incrementMovesMade() {
        movesMade++;
    }

    public synchronized void incrementSetsCached() {
        setsCached++;
    }

    public synchronized void incrementSetsUncached() {
        setsUncached++;
    }

    public synchronized void incrementGameCached() {
        gamesCached++;
    }

    public synchronized void incrementGameUncached() {
        gamesUncached++;
    }

    public synchronized void incrementSetLoads() {
        setLoads++;
    }

    public synchronized void incrementSetLoads(int loads) {
        setLoads += loads;
    }

    public synchronized void incrementSetLoadsCached() {
        setLoadsCached++;
    }

    public synchronized void incrementSetLoadsCached(int loads) {
        setLoadsCached += loads;
    }


    public synchronized void incrementGameLoads() {
        gameLoads++;
    }

    public synchronized void incrementGameLoads(int loads) {
        gameLoads += loads;
    }

    public synchronized void incrementGameLoadsCached() {
        gameLoadsCached++;
    }

    public synchronized void incrementGameLoadsCached(int loads) {
        gameLoadsCached += loads;
    }

    public synchronized int getGamesCreated() {
        return gamesCreated;
    }

    public synchronized int getGamesCompleted() {
        return gamesCompleted;
    }

    public synchronized int getMovesMade() {
        return movesMade;
    }

    public synchronized int getGamesCached() {
        return gamesCached;
    }

    public synchronized int getGamesUncached() {
        return gamesUncached;
    }

    public synchronized int getGameLoads() {
        return gameLoads;
    }

    public synchronized int getGameLoadsCached() {
        return gameLoadsCached;
    }

    public synchronized String getGameLoadHitRate() {
        if (gameLoads == 0) return "";

        double l = gameLoads;
        double lc = gameLoadsCached;

        double p = lc / l;
        return nf.format(p);
    }

    public synchronized int getSetLoads() {
        return setLoads;
    }

    public synchronized int getSetLoadsCached() {
        return setLoadsCached;
    }

    public synchronized int getSetsCached() {
        return setsCached;
    }

    public synchronized int getSetsCompleted() {
        return setsCompleted;
    }

    public synchronized int getSetsCreated() {
        return setsCreated;
    }

    public synchronized int getSetsUncached() {
        return setsUncached;
    }

    public synchronized String getSetLoadHitRate() {
        if (setLoads == 0) return "";

        double l = setLoads;
        double lc = setLoadsCached;

        double p = lc / l;
        return nf.format(p);
    }

}
