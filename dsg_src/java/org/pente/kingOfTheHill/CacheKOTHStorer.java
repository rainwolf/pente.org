package org.pente.kingOfTheHill;

import org.apache.log4j.*;
import org.pente.game.GridStateFactory;
import org.pente.gameServer.core.*;
import org.pente.turnBased.CacheTBStorer;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBSet;
import org.pente.turnBased.TBStoreException;

import java.util.*;
import java.util.Date;

/**
 * Created by waliedothman on 25/06/16.
 */
public class CacheKOTHStorer implements KOTHStorer {
    private Category log4j = Category.getInstance(
            CacheKOTHStorer.class.getName());

    private MySQLKOTHStorer baseStorer;
    private CacheDSGPlayerStorer dsgPlayerStorer;

    private CacheTBStorer tbStorer;

    private Map<Integer, Hill> hills;
    private Map<Integer, Integer> eidMap = new HashMap<>();

    public static final int liveGames[] = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24};
    public static final int tbGames[] = {51, 53, 55, 57, 59, 61, 63, 65, 67, 69, 71, 73};

    private Timer removeStalePlayersTimer;

    final private Object cacheKotHLock = new Object();


    public CacheKOTHStorer(MySQLKOTHStorer baseStorer, CacheDSGPlayerStorer dsgPlayerStorer) {
        this.baseStorer = baseStorer;
        this.dsgPlayerStorer = dsgPlayerStorer;

        loadHills();

//        for (int g: liveGames) {
//            adjustCrown(g);
//        }
//        for (int g: tbGames) {
//            adjustCrown(g);
//        }

        removeStalePlayersTimer = new Timer();
        removeStalePlayersTimer.scheduleAtFixedRate(
                new RemoveStalePlayersRunnable(), 10000, 24L * 3600 * 1000);
    }
    public void setTbStorer(CacheTBStorer tbStorer) {
        this.tbStorer = tbStorer;
    }



    public int getEventId(int game) {
        log4j.debug("CacheKOTHStorer.getEventId(" + game + ")");

        try {
            Integer e = eidMap.get(game);
            if (e == null) {
                int eid = baseStorer.getEventId(game);
                eidMap.put(game, eid);
                return eid;
            }
            return e.intValue();
        } catch (KOTHException e) {
            log4j.error("Error: CacheKOTHStorer.getEventId " + e);
        }
        return 0;
    }

    public Hill getHill(int game) {
        int hill_id = getEventId(game);
        if (hill_id != 0) {
            Hill hill = hills.get(hill_id);
            if (hill == null && game > 50) {
                hill = new Hill();
                hill.setHillID(hill_id);
                hills.put(hill_id, hill);
            }
            return hill;
        } 
        return null;
    }

    public int myStep(int game, long pid) {
        int hill_id = getEventId(game);
        if (hill_id == 0) {
            return -1;
        }
        Hill hill = hills.get(hill_id);
        if (hill != null) {
            return hill.myStep(pid);
        }
        return  -1;
    }

    private void storeHill(int hillId) {
        Hill hill = hills.get(hillId);
        if (hill != null) {
            try {
                baseStorer.storeHill(hill);
            } catch (KOTHException e) {
                log4j.error("Error storing hill: CacheKOTHStorer.storeHill(" + hillId + ") " + e);
            }
        }
    }

    private void loadHills() {
        try {
            synchronized (cacheKotHLock) {
                hills = baseStorer.loadHills();
            }
        } catch (KOTHException e) {
            log4j.error("Error loading hills: CacheKOTHStorer.loadHills " + e);
        }
    }

    public void addPlayer(int game, long pid) {
        int hill_id = getEventId(game);
        if (hill_id == 0) {
            return;
        }
        synchronized (cacheKotHLock) {
            try {
                if (!dsgPlayerStorer.loadPlayer(pid).hasPlayerDonated()) {
                    for (Hill hill : hills.values()) {
                        if (hill.getHillID() == hill_id) {
                            continue;
                        }
                        long oldKingPid = hill.getKing();
                        if (hill.removePlayer(pid)) {
                            long newKingPid = hill.getKing();
                            baseStorer.removePlayerFromHill(hill.getHillID(), pid);
                            storeHill(hill.getHillID());
                            if (oldKingPid != newKingPid) {
                                for (int liveGame : liveGames) {
                                    if (hill.getHillID() == getEventId(liveGame)) {
                                        adjustCrown(liveGame);
                                        break;
                                    }
                                }
                                for (int tbGame : tbGames) {
                                    if (hill.getHillID() == getEventId(tbGame)) {
                                        adjustCrown(tbGame);
                                        break;
                                    }
                                }
                            }
                            if (game > 50) {
                                fixTBinvitations(game, pid);
                            }
                        }
                    }
                }
            } catch (DSGPlayerStoreException e) {
                log4j.error("Error : CacheKOTHStorer.addPlayer " + e);
            }
            Hill hill = hills.get(hill_id);
            if (hill == null) {
                hill = new Hill();
                hill.setHillID(hill_id);
                hills.put(hill_id, hill);
            }
            hill.addPlayer(pid);
            storeHill(hill_id);
            adjustCrown(game);
        }
    }

    public void removePlayer(int game, long pid) {
        int hill_id = getEventId(game);
        if (hill_id == 0) {
            return;
        }
        synchronized (cacheKotHLock) {
            Hill hill = hills.get(hill_id);
            if (hill != null) {
                long oldKingPid = hill.getKing();
                if (hill.removePlayer(pid)) {
                    baseStorer.removePlayerFromHill(hill_id, pid);
                    storeHill(hill_id);
                    long newKingPid = hill.getKing();
                    if (oldKingPid != newKingPid) {
                        adjustCrown(game);
                    }
                    if (game > 50) {
                        fixTBinvitations(game, pid);
                    }
                }
            }
        }
    }

    public void movePlayersUpDown(int game, long winner, long loser) {
        int hill_id = getEventId(game);
        if (hill_id == 0) {
            return;
        }
        synchronized (cacheKotHLock) {
            Hill hill = hills.get(hill_id);
            if (hill != null) {
                long oldKingPid = hill.getKing();
                hill.movePlayersUpDown(winner, loser);
                storeHill(hill_id);
                long newKingPid = hill.getKing();
                if (oldKingPid != newKingPid) {
                    if (newKingPid == 0 && loser != oldKingPid) {
                        return;
                    }
                    adjustCrown(game);
                }
            }
        }
    }

    public int stepsBetween(int game, long pid1, long pid2) {
        int hill_id = getEventId(game);
        if (hill_id == 0) {
            return -42;
        }
        Hill hill = hills.get(hill_id);
        if (hill == null) {
            return 0;
        }
        return hill.stepsBetween(pid1, pid2);
    }

    public void adjustCrown(int game) {
        int hill_id = getEventId(game);
        if (hill_id == 0) {
            return;
        }
        Hill hill = hills.get(hill_id);
        if (hill != null) {
            try {
                long kingPid = hill.getKing();
                long oldKingPid = baseStorer.getCrownPid(game);

                baseStorer.adjustCrown(game, kingPid);
                if (kingPid != 0) {
                    dsgPlayerStorer.refreshPlayer(dsgPlayerStorer.loadPlayer(kingPid).getName());
                }
                if (oldKingPid != 0) {
                    dsgPlayerStorer.refreshPlayer(dsgPlayerStorer.loadPlayer(oldKingPid).getName());
                }
            } catch (DSGPlayerStoreException e) {
                log4j.error("Error: CacheKOTHStorer.adjustCrown " + e);
            }
        }
    }

    private class RemoveStalePlayersRunnable extends TimerTask {

        private static final int DELAY = 60;

        public String getName() {
            return "RemoveStalePlayersRunnable";
        }

        public void run() {
            Date lastMonth = new Date();
            lastMonth.setTime(lastMonth.getTime() - (31L*3600*24*1000));
            Date hundredDays = new Date();
            hundredDays.setTime(hundredDays.getTime() - (100L*3600*24*1000));
//            boolean altered = false;
            synchronized (cacheKotHLock) {
                for (int tbGame : tbGames) {
                    Hill hill = getHill(tbGame);
                    if (hill != null) {
                        List<Player> players = hill.getMembers();
                        for (Player player : players) {
                            boolean subscriber = false;
                            try {
                                DSGPlayerData playerData = dsgPlayerStorer.loadPlayer(player.getPid());
                                subscriber = playerData.hasPlayerDonated() && playerData.getStatus() == DSGPlayerData.ACTIVE;
                            } catch (DSGPlayerStoreException | NullPointerException e) {
                                e.printStackTrace();
                            }
                            if (hasOngoingKotHTBgames(tbGame, player.getPid())) {
                                continue;
                            }
                            Date lastDate = player.getLastGame();
                            if (lastDate != null && (
                                    (!subscriber && lastDate.before(lastMonth)) ||
                                    (subscriber && lastDate.before(hundredDays)))) {
                                removePlayer(tbGame, player.getPid());
//                                baseStorer.removePlayerFromHill(hill.getHillID(), player.getPid());
//                                hill.removePlayer(player.getPid());
//                                fixTBinvitations(tbGame, player.getPid());
//                                altered = true;
                            }
                        }
//                        if (altered) {
//                            altered = false;
//                            storeHill(hill.getHillID());
//                            adjustCrown(tbGame);
//                        }
                    }
                }

                lastMonth.setTime(lastMonth.getTime() - (31L * 3600 * 24 * 1000));
//                altered = false;
                for (int liveGame : liveGames) {
                    Hill hill = getHill(liveGame);
                    if (hill != null) {
                        List<Player> players = hill.getMembers();
                        for (Player player : players) {
                            boolean subscriber = false;
                            try {
                                DSGPlayerData playerData = dsgPlayerStorer.loadPlayer(player.getPid());
                                subscriber = playerData.hasPlayerDonated() && playerData.getStatus() == DSGPlayerData.ACTIVE;
                            } catch (DSGPlayerStoreException | NullPointerException e) {
                                e.printStackTrace();
                            }
                            Date lastDate = player.getLastGame();
                            //                        Date lastDate = baseStorer.getLastGameDate(hill.getHillID(), pid);
                            if (lastDate != null && (
                                    (!subscriber && lastDate.before(lastMonth)) ||
                                    (subscriber && lastDate.before(hundredDays)))) {
                                removePlayer(liveGame, player.getPid());
//                                baseStorer.removePlayerFromHill(hill.getHillID(), player.getPid());
//                                hill.removePlayer(player.getPid());
//                                altered = true;
                            }
                        }
//                        if (altered) {
//                            altered = false;
//                            storeHill(hill.getHillID());
//                            adjustCrown(liveGame);
//                        }
                    }
                }
            }
        }
    }

    public void updatePlayerLastGameDate(int game, long pid) {
        int hill_id = getEventId(game);
        if (hill_id == 0) {
            return;
        }
        Hill hill = getHill(game);
        if (hill == null) {
            return;
        }
        synchronized (cacheKotHLock) {
            for (Step step : hill.getSteps()) {
                for (Player player : step.getPlayers()) {
                    if (player.getPid() == pid) {
                        player.setLastGame(new Date());
                        baseStorer.updatePlayerLastGameDate(hill_id, pid);
                        return;
                    }
                }
            }
        }
    }


    public boolean canPlayerBeChallenged(int game, long pid) {
        int hill_id = getEventId(game);
        if (hill_id == 0) {
            return false;
        }
        try {
            int limit = 1;
            DSGPlayerData playerData = dsgPlayerStorer.loadPlayer(pid);
            if (playerData == null) {
                return false;
            }
            boolean subscriber = playerData.hasPlayerDonated(); 
            if (subscriber) {
                limit = 4;
            }
            int ongoingGames = 0;
            
            List<TBSet> setsPlaying = tbStorer.loadSets(pid);
            for (TBSet set : setsPlaying) {
                if (subscriber && set.getGame1().getEventId() == hill_id && (set.getState() == TBSet.STATE_ACTIVE || set.getState() == TBSet.STATE_NOT_STARTED)) {
                    ongoingGames += 1;
                } else if (!subscriber && getEventId(set.getGame1().getGame()) == set.getGame1().getEventId() && (set.getState() == TBSet.STATE_ACTIVE || set.getState() == TBSet.STATE_NOT_STARTED)) {
                    ongoingGames += 1;
                }
                if (ongoingGames > limit) {
                    break;
                }
            }
            if (ongoingGames > limit) {
                return false;
            } else {
                return true;
            }
        } catch (TBStoreException e) {
            log4j.error("Error: CacheKOTHStorer.canPlayerBeChallenged TBStoreException (" + game + ", " + pid + ")" + e);
        } catch (DSGPlayerStoreException e) {
            log4j.error("Error: CacheKOTHStorer.canPlayerBeChallenged DSGPlayerStoreException (" + game + ", " + pid + ")" + e);
        }
        return false;
    }

    private void fixTBinvitations(int game, long pid) {
        try {
            List<TBSet> sets = tbStorer.loadSets(pid);
            int kothEventId = getEventId(game);
            for (TBSet set : sets) {
                if (set.getState() == TBSet.STATE_NOT_STARTED && set.getGame1().getGame() == game && set.getGame1().getEventId() == kothEventId) {
                    TBSet loadedSet = tbStorer.loadSet(set.getSetId());
                    for (int i = 0; i < 2; i++) {
                        TBGame g = loadedSet.getGames()[i];
                        if (g == null) {
                            continue;
                        }
                        int eventID = tbStorer.getEventId(game);
                        g.setEventId(eventID);
                        tbStorer.setGameEventId(g.getGid(), eventID);
                    }
                }
            }
        } catch (TBStoreException e) {
            e.printStackTrace();
        }
    }

    private boolean hasOngoingKotHTBgames(int game, long pid) {
        try {
            List<TBSet> sets = tbStorer.loadSets(pid);
            int kothEventId = getEventId(game);
            for (TBSet set : sets) {
                if (set.getState() == TBSet.STATE_ACTIVE && set.getGame1().getGame() == game && set.getGame1().getEventId() == kothEventId) {
                    return true;
                }
            }
        } catch (TBStoreException e) {
            e.printStackTrace();
        }
        return false;
    }


}
