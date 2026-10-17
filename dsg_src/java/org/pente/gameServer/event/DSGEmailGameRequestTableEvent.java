package org.pente.gameServer.event;

public class DSGEmailGameRequestTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = -7360683917731180365L;

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
