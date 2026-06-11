/**
 * Server.java
 * Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, you can find it online at
 * http://www.gnu.org/copyleft/gpl.txt
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
import jakarta.websocket.Session;

import static java.lang.Thread.sleep;


/** A simple class to contain the necessary components that make up the server
 */
public class TournamentServer extends Server {

    protected int ROUND_PAUSE = 3;
    protected int FIRST_ROUND_WAIT = 5;
    protected Map<Long, Integer> pid2tables;
    protected Map<Integer, TourneyMatch> table2matches;
    // bumped whenever startNewRoundNow() replaces the round state. An
    // attemptMatchStart thread captures it before sleeping and re-checks after
    // waking, so a thread that slept through a round transition aborts instead
    // of seating players against the previous round's matches.
    protected int matchStartGeneration;
    protected HashSet<String> tournamentPlayers;
    protected Tourney tournament;
    protected List<TourneyMatch> matches;
    protected Timer timeoutBeforeNextRoundTimer;
    protected List<Timer> waitAnnouncementTimers;
    protected boolean noNeedForBreak = false;
    protected boolean startNewTimers = true;


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
        stopWait();
        if (tournament.isComplete()) {
            return;
        }
        if (noNeedForBreak || tournament.getNumRounds() == 1) {
            noNeedForBreak = false;
            startNewRoundNow();
        } else {
            mainRoom.eventOccurred(new DSGSystemMessageTableEvent(0, "BREAK: New round starts in " + ROUND_PAUSE + " minutes."));
            if (ROUND_PAUSE > 1) {
                for (int i = ROUND_PAUSE - 1; i > 0; i--) {
                    Timer t = new Timer();
                    int finalI = i;
                    t.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            mainRoom.eventOccurred(new DSGSystemMessageTableEvent(0, finalI + " minutes left before round " + (tournament.getNumRounds() + 1) + " starts."));
                            t.cancel();
                            t.purge();
                        }
                    }, 1000L * 60 * (ROUND_PAUSE - finalI));
                }
            }
            final Timer roundTimer = new Timer();
            timeoutBeforeNextRoundTimer = roundTimer;
            roundTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    startNewRoundNow();
                    synchronized (TournamentServer.this) {
                        roundTimer.cancel();
                        roundTimer.purge();
                        // only clear the field under the monitor, and only if it
                        // still refers to this timer — stopWait() or a newer
                        // schedule may have already replaced it.
                        if (timeoutBeforeNextRoundTimer == roundTimer) {
                            timeoutBeforeNextRoundTimer = null;
                        }
                    }
                }
            }, 1000L * 60 * ROUND_PAUSE);
        }
    }

    // synchronized: rebuilds pid2tables/table2matches/matches/tournamentPlayers, so
    // it must hold the same monitor as removeTable/initNewRound. Callers that bypass
    // initNewRound (the director /start command, the break timer) are now safe too.
    protected synchronized void startNewRoundNow() {
        matchStartGeneration++;
        pid2tables = new ConcurrentHashMap<>();
        table2matches = new ConcurrentHashMap<>();
        tournamentPlayers = new HashSet<>();
        for (TourneyPlayerData p : tournament.getLastRound().getPlayers()) {
            tournamentPlayers.add(p.getName());
        }
        matches = new ArrayList<>();
        for (TourneySection section : tournament.getLastRound().getSections()) {
            for (TourneyMatch match : section.getMatches()) {
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

    public synchronized void matchOnJoin(DSGPlayerData playerData) {
        // synchronized: reads tournamentPlayers/pid2tables, which startNewRoundNow
        // rebuilds under the same monitor.
        if (tournament.getNumRounds() == 0 || !tournamentPlayers.contains(playerData.getName()) || pid2tables.get(playerData.getPlayerID()) != null) {
            return;
        }
        attemptMatchStart(playerData.getPlayerID());
    }

    private synchronized void attemptMatchStart(Long pid) {
        // capture the round we're seating for; the thread re-checks it after waking
        final int gen = matchStartGeneration;
        Thread thread = new Thread(() -> {
            try {
                sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // The seating loop runs under the server monitor so it reads/writes
            // matches/pid2tables/table2matches with the same lock startNewRoundNow
            // and removeTable use. This serializes concurrent runs (no double
            // seating) and gives a consistent view. Holding the monitor here is
            // deadlock-free: createNewTable/removeTable lock on `tables`, and the
            // lock order is always this -> tables. The 200ms sleep is OUTSIDE the
            // lock so we never block other work while idling.
            synchronized (TournamentServer.this) {
                // a new round started while we slept; its matches/maps replaced
                // ours, so anything we'd seat now would be against a stale round
                if (gen != matchStartGeneration) {
                    return;
                }
                for (TourneyMatch match : matches) {
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
                        stopWait();
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
                if (pid2tables.isEmpty() && startNewTimers) {
                    startWait();
                } else {
                    stopWait();
                }
            }
        });
        thread.start();
    }

    protected void removePlayerFromTables(String player) {
        for (Integer tableNum : table2matches.keySet()) {
            SynchronizedServerTable syncedTable = (SynchronizedServerTable) this.tables.get(tableNum);
            ServerTable table = syncedTable.getServerTable();
            Vector<DSGPlayerData> playersInTable = table.playersInTable;
            for (DSGPlayerData p : playersInTable) {
                if (p != null && p.getName().equals(player)) {
                    syncedTable.eventOccurred(new DSGExitTableEvent(player, tableNum, false, false));
                }
            }
        }
    }

    public synchronized void startWait() {
        if (tournament.isComplete()) {
            return;
        }
        if (timeoutBeforeNextRoundTimer == null) {
            int pause = ROUND_PAUSE;
            if (tournament.getNumRounds() == 1) {
                pause = FIRST_ROUND_WAIT;
            }
            mainRoom.eventOccurred(new DSGSystemMessageTableEvent(0, "No more possible matches with the present players. In " + pause + " minutes, the next round will start, unless new matches become possible."));
            if (pause > 1) {
                if (waitAnnouncementTimers == null) {
                    waitAnnouncementTimers = new ArrayList<>();
                }
                if (!waitAnnouncementTimers.isEmpty()) {
                    for (Iterator<Timer> iterator = waitAnnouncementTimers.iterator(); iterator.hasNext(); ) {
                        Timer t = iterator.next();
                        t.cancel();
                        t.purge();
                        iterator.remove();
                    }
                }
                for (int i = pause - 1; i > 0; i--) {
                    Timer t = new Timer();
                    int finalI = i;
                    t.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            mainRoom.eventOccurred(new DSGSystemMessageTableEvent(0, finalI + " minutes left before round " + (tournament.getNumRounds() + 1) + " starts."));
                            t.cancel();
                            t.purge();
                        }
                    }, 1000L * 60 * (pause - finalI));
                    waitAnnouncementTimers.add(t);
                }
            }
            final Timer forfeitTimer = new Timer();
            timeoutBeforeNextRoundTimer = forfeitTimer;
            forfeitTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    forfeitRemainingMatches();
                    synchronized (TournamentServer.this) {
                        forfeitTimer.cancel();
                        forfeitTimer.purge();
                        // only clear the field under the monitor, and only if it
                        // still refers to this timer — stopWait() or a newer
                        // schedule may have already replaced it.
                        if (timeoutBeforeNextRoundTimer == forfeitTimer) {
                            timeoutBeforeNextRoundTimer = null;
                        }
                    }
                }
            // Forfeit after the same delay announced to players (`pause`), not
            // ROUND_PAUSE. Round 1 sets pause = FIRST_ROUND_WAIT, so using
            // ROUND_PAUSE here forfeited first-round players minutes early.
            }, 1000L * 60 * pause);
        }
    }

    public synchronized void stopWait() {
        if (timeoutBeforeNextRoundTimer != null) {
            timeoutBeforeNextRoundTimer.cancel();
            timeoutBeforeNextRoundTimer = null;
        }
        if (waitAnnouncementTimers != null) {
            if (!waitAnnouncementTimers.isEmpty()) {
                for (Iterator<Timer> iterator = waitAnnouncementTimers.iterator(); iterator.hasNext(); ) {
                    Timer t = iterator.next();
                    t.cancel();
                    iterator.remove();
                }
            }
        }
    }

    public synchronized void forfeitRemainingMatches() {
        for (TourneyMatch match : matches) {
            if (match.hasBeenPlayed()) {
                continue;
            }
            noNeedForBreak = true;
            String player1 = match.getPlayer1().getName();
            String player2 = match.getPlayer2().getName();
            boolean p1inRoom = mainRoom.isPlayerInMainRoom(player1), p2inRoom = mainRoom.isPlayerInMainRoom(player2);
            int result;
            if (!p1inRoom && !p2inRoom) {
                result = TourneyMatch.RESULT_DBL_FORFEIT;
            } else if (!p1inRoom && p2inRoom) {
                result = TourneyMatch.RESULT_P2_WINS;
            } else if (!p2inRoom && p1inRoom) {
                result = TourneyMatch.RESULT_P1_WINS;
            } else {
                // both present but never completed their game before the round
                // cutoff (a late rejoin that missed re-pairing) — enforce a double
                // forfeit so the round can complete; never persist RESULT_UNFINISHED
                // with setForfeit(true), which is a contradictory state.
                result = TourneyMatch.RESULT_DBL_FORFEIT;
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
        stopWait();
        super.destroy();
    }
}