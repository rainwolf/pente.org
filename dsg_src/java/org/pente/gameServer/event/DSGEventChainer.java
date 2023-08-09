package org.pente.gameServer.event;

public class DSGEventChainer implements DSGEventListener {

    private DSGEventListener chainedEventListener;

    public DSGEventChainer(DSGEventListener chainedEventListener) {
        this.chainedEventListener = chainedEventListener;
    }

    public void eventOccurred(DSGEvent dsgEvent) {
        chainedEventListener.eventOccurred(dsgEvent);
    }

}

