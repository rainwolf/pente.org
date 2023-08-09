package org.pente.gameServer.event;

public class DSGEmailGameRequestTableEvent extends AbstractDSGTableEvent {

    public DSGEmailGameRequestTableEvent() {
        super();
    }

    public DSGEmailGameRequestTableEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "email game request " + super.toString();
    }
}
