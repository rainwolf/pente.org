package org.pente.gameServer.event;

public class AbstractDSGEvent implements DSGEvent {
    private static final long serialVersionUID = -4315917548046106948L;

    private long time;

    public void setCurrentTime() {
        time = System.currentTimeMillis();
    }

    public long getTime() {
        return time;
    }

}
