package org.pente.gameServer.event;

public class DSGPingEvent extends AbstractDSGEvent {

    private String player;
    private long pingStart;
    private long averagePingTime;
    private long latestPingTime;

    public DSGPingEvent(String player, long averagePingTime, long latestPingTime) {
        this.player = player;

        this.averagePingTime = averagePingTime;
        this.latestPingTime = latestPingTime;

        pingStart = System.currentTimeMillis();
    }

    public String getPlayer() {
        return player;
    }

    public long getPingStart() {
        return pingStart;
    }
}

