package org.pente.gameServer.event;

public class DSGExitTableErrorEvent extends AbstractDSGTableErrorEvent {

    public DSGExitTableErrorEvent() {
        super();
    }

    public DSGExitTableErrorEvent(String player, int table, int error) {
        super(player, table, error);
    }

    public String toString() {
        return "exit " + super.toString();
    }
}

