package org.pente.gameServer.event;

public class DSGClientErrorEvent extends AbstractDSGEvent {
    private static final long serialVersionUID = 5667776016548959253L;

    private Throwable throwable;

    public DSGClientErrorEvent(Throwable t) {
        this.throwable = t;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public String toString() {
        return "client error";
    }
}
