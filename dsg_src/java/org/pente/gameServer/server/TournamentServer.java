/** Server.java
 *  Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, you can find it online at
 *  http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.server;

import java.util.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.*;

import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.*;
import org.pente.gameServer.tourney.*;

import org.pente.kingOfTheHill.*;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import javax.websocket.Session;

import static java.lang.Thread.sleep;


/** A simple class to contain the necessary components that make up the server
 */
public class TournamentServer extends Server {

    protected int ROUND_PAUSE = 3; 
    protected Map<Long, Integer> pid2tables;
    protected Map<Integer, TourneyMatch> table2matches;
    protected HashSet<String> tournamentPlayers;
    protected Tourney tournament;
    protected List<TourneyMatch> matches;
    protected Timer timeoutBeforeNextRoundTimer;
    protected boolean noNeedForBreak = false;

    public TournamentServer(Resources resources,
                  ServerData serverData) throws Throwable {

        super(resources, serverData);
        tournament = getTourney();
        if (tournament.getNumRounds() > 0) {
            initNewRound();
        }
    }
    
    @Override
    public void routeEventToTable(DSGEvent event, int tableNum) {
        if (event instanceof DSGJoinTableEvent &&
                tableNum == DSGJoinTableEvent.CREATE_NEW_TABLE) {
            return;
        }
        super.routeEventToTable(event, tableNum);
    }


    public synchronized void initNewRound() {
        if (timeoutBeforeNextRoundTimer != null) {
            timeoutBeforeNextRoundTimer.cancel();
        }
        if (tournament.isComplete()) {
            return;
        }
        if (noNeedForBreak) {
            noNeedForBreak = false;
            startNewRoundNow();
        } else {
            mainRoom.eventOccurred(new DSGSystemMessageTableEvent(0, "BREAK: round complete, new round starts "+ROUND_PAUSE+" minutes."));
            timeoutBeforeNextRoundTimer = new Timer();
            timeoutBeforeNextRoundTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    startNewRoundNow();
                    timeoutBeforeNextRoundTimer = null;
                }
            }, 1000L * 60 * ROUND_PAUSE);
        }
    }
    
    protected void startNewRoundNow() {
        pid2tables = new ConcurrentHashMap<>();
        table2matches = new ConcurrentHashMap<>();
        tournamentPlayers = new HashSet<>();
        for (TourneyPlayerData p: tournament.getLastRound().getPlayers()) {
            tournamentPlayers.add(p.getName());
        }
        matches = new ArrayList<>();
        for (TourneySection section: tournament.getLastRound().getSections()) {
            for (TourneyMatch match: section.getMatches()) {
                if (!match.hasBeenPlayed()) {
                    matches.add(match);
                }
            }
        }
        attemptMatchStart(null);
    }
    
    

    public synchronized void removeTable(int tableNum) {
        TourneyMatch match = table2matches.get(tableNum);
        if (match != null) {
            long pid1 = match.getPlayer1().getPlayerID();
            long pid2 = match.getPlayer2().getPlayerID();
            pid2tables.remove(pid1);
            pid2tables.remove(pid2);
            table2matches.remove(tableNum);
            attemptMatchStart(null);
        }
        super.removeTable(tableNum);
    }
    
    public void matchOnJoin(DSGPlayerData playerData) {
        if (tournament.getNumRounds() == 0 || !tournamentPlayers.contains(playerData.getName()) || pid2tables.get(playerData.getPlayerID()) != null) {
            return;
        }
        attemptMatchStart(playerData.getPlayerID());
    }
    
    private synchronized void attemptMatchStart(Long pid) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (TourneyMatch match: matches) {
                    if (match.hasBeenPlayed()) {
                        continue;
                    }
                    if (pid != null && match.getPlayer1().getPlayerID() != pid && match.getPlayer2().getPlayerID() != pid) {
                        continue;
                    }
                    // make sure they're not playing
                    long pid1 = match.getPlayer1().getPlayerID();
                    if (pid2tables.get(pid1) != null) {
                        continue;
                    }
                    long pid2 = match.getPlayer2().getPlayerID();
                    if (pid2tables.get(pid2) != null) {
                        continue;
                    }
                    String player1 = match.getPlayer1().getName();
                    String player2 = match.getPlayer2().getName();
                    // make sure they're logged on
                    if (!mainRoom.isPlayerInMainRoom(player1) || !mainRoom.isPlayerInMainRoom(player2)) {
                        continue;
                    }
                    try {
                        if (timeoutBeforeNextRoundTimer != null) {
                            timeoutBeforeNextRoundTimer.cancel();
                            timeoutBeforeNextRoundTimer = null;
                        }
                        int tableNum = createNewTable(new DSGJoinTableEvent());
                        // remove them if they're spectating
                        removePlayerFromTables(player1);
                        removePlayerFromTables(player2);
                        SynchronizedServerTable syncedTable = (SynchronizedServerTable) tables.get(tableNum);
                        ServerTable table = syncedTable.getServerTable();
                        table.setTourneyMatch(match);
                        // join only, table will sit them
                        syncedTable.eventOccurred(new DSGJoinTableEvent(player1, tableNum));
                        syncedTable.eventOccurred(new DSGJoinTableEvent(player2, tableNum));
                        // housekeeping
                        pid2tables.put(pid1, tableNum);
                        pid2tables.put(pid2, tableNum);
                        table2matches.put(tableNum, match);
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                }
                if (pid2tables.isEmpty()) {
                    startWait();
                } else if (timeoutBeforeNextRoundTimer != null) {
                    timeoutBeforeNextRoundTimer.cancel();
                    timeoutBeforeNextRoundTimer = null;
                }
            }
        });
        thread.start();
    } 
    
    protected void removePlayerFromTables(String player) {
        for (Integer tableNum: table2matches.keySet()) {
            SynchronizedServerTable syncedTable = (SynchronizedServerTable) this.tables.get(tableNum);
            ServerTable table = syncedTable.getServerTable();
            Vector<DSGPlayerData> playersInTable = table.playersInTable;
            for (DSGPlayerData p: playersInTable) {
                if (p != null && p.getName().equals(player)) {
                    syncedTable.eventOccurred(new DSGExitTableEvent(player, tableNum, false, false));
                }
            }
        }
    }
    
    public synchronized void startWait() {
        if (timeoutBeforeNextRoundTimer == null) {
            mainRoom.eventOccurred(new DSGSystemMessageTableEvent(0, "No more possible matches with the present players. In "+ROUND_PAUSE+" minutes, the next round will start, unless new matches become possible."));
            timeoutBeforeNextRoundTimer = new Timer();
            timeoutBeforeNextRoundTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    forfeitRemainingMatches();
                    timeoutBeforeNextRoundTimer = null;
                }
            }, 1000L * 60 * ROUND_PAUSE);
        }
        
    }
    
    public synchronized void forfeitRemainingMatches() {
        for (TourneyMatch match: matches) {
            if (match.hasBeenPlayed()) {
                continue;
            }
            noNeedForBreak = true;
            String player1 = match.getPlayer1().getName();
            String player2 = match.getPlayer2().getName();
            boolean p1inRoom = mainRoom.isPlayerInMainRoom(player1), p2inRoom = mainRoom.isPlayerInMainRoom(player2);
            int result = TourneyMatch.RESULT_UNFINISHED;
            if (!p1inRoom && !p2inRoom) {
                result = TourneyMatch.RESULT_DBL_FORFEIT;
            } else if (!p1inRoom && p2inRoom) {
                result = TourneyMatch.RESULT_P2_WINS;
            } else if (!p2inRoom && p1inRoom) {
                result = TourneyMatch.RESULT_P1_WINS;
            }

            match.setForfeit(true);
            match.setResult(result);
            try {
                resources.getTourneyStorer().updateMatch(match);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }
    
    
    public void destroy() {
        if (timeoutBeforeNextRoundTimer != null) {
            timeoutBeforeNextRoundTimer.cancel();
        }
        super.destroy(); 
    }
}