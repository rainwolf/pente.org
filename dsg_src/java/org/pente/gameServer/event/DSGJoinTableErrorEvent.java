package org.pente.gameServer.event;

public class DSGJoinTableErrorEvent extends AbstractDSGTableErrorEvent {

    public DSGJoinTableErrorEvent() {
    }

    public DSGJoinTableErrorEvent(String player, int table, int error) {
        super(player, table, error);
    }

    public String toString() {
        return "join table " + super.toString();
    }
}

