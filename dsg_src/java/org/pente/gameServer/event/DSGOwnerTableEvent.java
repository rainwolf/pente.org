package org.pente.gameServer.event;

public class DSGOwnerTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = -3960105100252710133L;

    public DSGOwnerTableEvent() {
        super();
    }

    public DSGOwnerTableEvent(String player, int table) {
        super(player, table);
        if (player == null) {
            this.setPlayer("");
        }
    }

    public String toString() {
        return "set owner " + super.toString();
    }
}

