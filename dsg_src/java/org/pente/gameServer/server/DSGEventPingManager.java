/**
 * DSGEventPingManager.java
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

import org.apache.log4j.*;

import org.pente.gameServer.event.*;

public class DSGEventPingManager implements PingManager {

    private static Category log4j =
            Category.getInstance(DSGEventPingManager.class.getName());

    private Map<String, PingTimeData> players;
    private DSGEventToPlayerRouter dsgRouter;

    private volatile boolean running;
    private Thread pingThread;

    private static final long SLEEP_TIME = 15000;

    public DSGEventPingManager(final DSGEventToPlayerRouter dsgRouter) {
        this.dsgRouter = dsgRouter;

        players = Collections.synchronizedMap(new HashMap<>());

        Runnable pingRunnable = () -> {

            DSGPingEvent pingEvent = null;
            while (running) {

                Map<String, PingTimeData> playersCopy = null;
                synchronized (players) {
                    playersCopy = new HashMap<>(players);
                }

                Iterator<String> names = playersCopy.keySet().iterator();
                while (names.hasNext()) {
                    String name = (String) names.next();
                    //log4j.info(name + ": " + players.get(name));
                    pingEvent = new DSGPingEvent(name, players.get(name).getAveragePingTime(), players.get(name).getLatestPingTime());
                    dsgRouter.routeEvent(pingEvent, name);
                    pingEvent = null;
                }

                try {
                    Thread.sleep(SLEEP_TIME);
                } catch (InterruptedException e) {
                }
            }
        };
        pingThread = new Thread(pingRunnable, "DSGEventPingManager");
        running = true;
        pingThread.start();
    }

    public void destroy() {
        running = false;
        if (pingThread != null) {
            pingThread.interrupt();
        }
    }

    public void receivePingEvent(DSGPingEvent pingEvent) {

        PingTimeData p = (PingTimeData) players.get(pingEvent.getPlayer());
        if (p != null) {
            p.updatePingTime((int) (System.currentTimeMillis() - pingEvent.getPingStart()));
        }
    }

    public long getPingTime(String player) {

        PingTimeData p = (PingTimeData) players.get(player);
        if (p == null) {
            return 0; //throw exception?
        }

        return p.getAveragePingTime();
    }

    public void addPlayer(String player) {
        synchronized (players) {
            players.put(player, new PingTimeData());
        }
    }

    public void removePlayer(String player) {
        synchronized (players) {
            players.remove(player);
        }
    }

    private class PingTimeData {

        private static final int START_PING_TIME = 10;
        private static final int PINGS_USED_IN_AVG = 5;

        private int averagePingTime;

        private int pingTimes[] = new int[PINGS_USED_IN_AVG];
        private int pingIndex = 0;

        public PingTimeData() {
            averagePingTime = START_PING_TIME;
            for (int i = 0; i < PINGS_USED_IN_AVG; i++) {
                pingTimes[i] = START_PING_TIME;
            }
        }

        public void updatePingTime(int newPingTime) {
            pingTimes[pingIndex] = newPingTime;
            pingIndex = (pingIndex + 1) % PINGS_USED_IN_AVG;

            int newAverage = 0;
            for (int i = 0; i < PINGS_USED_IN_AVG; i++) {
                newAverage += pingTimes[i];
            }

            averagePingTime = newAverage / PINGS_USED_IN_AVG;
        }

        public long getAveragePingTime() {
            return averagePingTime;
        }

        public long getLatestPingTime() {
            int index = (pingIndex - 1 + PINGS_USED_IN_AVG) % PINGS_USED_IN_AVG;
            return pingTimes[index];
        }

        public String toString() {
            return "ping time: " + averagePingTime;
        }
    }
}

