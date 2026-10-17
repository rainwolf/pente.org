package org.pente.gameServer.event;

public class DSGStartSetTimerEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = 8443660944401336459L;

    private long timeLeft;

    public DSGStartSetTimerEvent() {
    }

    public DSGStartSetTimerEvent(String player, int table, long timeLeft) {
        super(player, table);
        this.timeLeft = timeLeft;
    }

    public String toString() {
        return "start set timeout " + timeLeft + " " + super.toString();
    }

    public long getTimeLeft() {
        return timeLeft;
    }

    public void setTimeLeft(long timeLeft) {
        this.timeLeft = timeLeft;
    }
}

