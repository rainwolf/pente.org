package org.pente.gameServer.event;

public class DSGClientErrorEvent extends AbstractDSGEvent {

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
