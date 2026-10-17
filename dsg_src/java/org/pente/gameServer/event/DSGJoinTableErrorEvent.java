package org.pente.gameServer.event;

public class DSGJoinTableErrorEvent extends AbstractDSGTableErrorEvent {
    private static final long serialVersionUID = 3308945975514938675L;

    public DSGJoinTableErrorEvent() {
    }

    public DSGJoinTableErrorEvent(String player, int table, int error) {
        super(player, table, error);
    }

    public String toString() {
        return "join table " + super.toString();
    }
}

