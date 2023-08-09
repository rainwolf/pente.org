package org.pente.gameServer.event;

import java.util.*;

public class DSGServerStatsEvent extends AbstractDSGEvent {

    private int logins;
    private int games;
    private int maxPlayers;
    private int events;
    private long startMillis;

    public DSGServerStatsEvent() {
    }

    public DSGServerStatsEvent(
            int logins, int games, int maxPlayers,
            int events, Date startDate) {

        this.logins = logins;
        this.games = games;
        this.maxPlayers = maxPlayers;
        this.events = events;
        this.startMillis = startDate.getTime();
    }

    public int getLogins() {
        return logins;
    }

    public int getGames() {
        return games;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getEvents() {
        return events;
    }

    public Date getStartDate() {
        return new Date(startMillis);
    }

    public String getUpTime() {

        if (startMillis == 0) {
            return "";
        }

        int diffMillis = (int) (System.currentTimeMillis() - startMillis);
        int days = diffMillis / (1000 * 60 * 60 * 24);
        int hours = diffMillis % (1000 * 60 * 60 * 24) / (1000 * 60 * 60);

        return days + " day(s) " + hours + " hour(s)";
    }

    public String toString() {
        return "server stats: logins " + logins + ", games " + games +
                ", max players on " + maxPlayers + ", events " + events +
                ", up time " + getUpTime();
    }
}
