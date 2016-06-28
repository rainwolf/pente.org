package org.pente.kingOfTheHill;

import org.apache.log4j.*;
import org.pente.gameServer.core.*;

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

    private Map<Integer, Hill> hills;
    private Map<Integer, Integer> eidMap = new HashMap<Integer, Integer>();

    public static final int liveGames[] = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    public static final int tbGames[] = {51, 53, 55, 57, 59, 61, 63, 65};

    private Timer removeStalePlayersTimer;


    public CacheKOTHStorer(MySQLKOTHStorer baseStorer, CacheDSGPlayerStorer dsgPlayerStorer) {
        this.baseStorer = baseStorer;
        this.dsgPlayerStorer = dsgPlayerStorer;
        loadHills();

        removeStalePlayersTimer = new Timer();
        removeStalePlayersTimer.scheduleAtFixedRate(
                new RemoveStalePlayersRunnable(), 5000, 3600 * 1000);
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
            return hills.get(hill_id);
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
            synchronized (this) {
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
        synchronized (this) {
            try {
                if (!dsgPlayerStorer.loadPlayer(pid).hasPlayerDonated()) {
                    for (Hill hill : hills.values()) {
                        if (hill_id == hill.getHillID()) {
                            continue;
                        }
                        if (hill.removePlayer(pid)) {
                            storeHill(hill_id);
                            adjustCrown(game);
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
        synchronized (this) {
            Hill hill = hills.get(hill_id);
            if (hill != null) {
                hill.removePlayer(pid);
                storeHill(hill_id);
                adjustCrown(game);
            }
        }
    }

    public void movePlayersUpDown(int game, long winner, long loser) {
        int hill_id = getEventId(game);
        if (hill_id == 0) {
            return;
        }
        synchronized (this) {
            Hill hill = hills.get(hill_id);
            if (hill != null) {
                hill.movePlayersUpDown(winner, loser);
                storeHill(hill_id);
                adjustCrown(game);
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

    private void adjustCrown(int game) {
        int hill_id = getEventId(game);
        if (hill_id == 0) {
            return;
        }
        Hill hill = hills.get(hill_id);
        if (hill != null) {
            try {
                long kingPid = 0;
                long oldKingPid = baseStorer.getCrownPid(game);

                if (hill.getSteps().get(hill.getSteps().size() - 1).getPlayers().size() == 1) {
                    kingPid = hill.getSteps().get(hill.getSteps().size() - 1).getPlayers().get(0);
                }
                baseStorer.adjustCrown(game, kingPid);
                dsgPlayerStorer.refreshPlayer(dsgPlayerStorer.loadPlayer(kingPid).getName());
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
            lastMonth.setTime(lastMonth.getTime() - (31*3600*24));
            boolean altered = false;
            try {
                for ( int i = 0; i < liveGames.length; i++ ) {
                    Hill hill = getHill(liveGames[i]);
                    if (hill != null) {
                        List<Long> pids = hill.getMembers();
                        for (Long pid : pids) {
                            if (dsgPlayerStorer.loadPlayer(pid).getPlayerGameData(liveGames[i]).getLastGameDate().before(lastMonth)) {
                                hill.removePlayer(pid);
                                altered = true;
                            }
                        }
                        if (altered) {
                            altered = false;
                            storeHill(hill.getHillID());
                        }
                    }
                }
//                for ( int i = 0; i < liveGames.length; i++ ) {
//                    Hill hill = getHill(liveGames[i]);
//                    if (hill != null) {
//                        List<Long> pids = hill.getMembers();
//                        for (Long pid : pids) {
//                            if (dsgPlayerStorer.loadPlayer(pid).getPlayerGameData(liveGames[i]).getLastGameDate().before(lastMonth)) {
//                                hill.removePlayer(pid);
//                                altered = true;
//                            }
//                        }
//                        if (altered) {
//                            altered = false;
//                            storeHill(hill.getHillID());
//                        }
//                    }
//                }

            } catch (DSGPlayerStoreException e) {
                log4j.error("Error: CacheKOTHStorer.RemoveStalePlayersRunnable " + e);
            }
        }
    }




}
