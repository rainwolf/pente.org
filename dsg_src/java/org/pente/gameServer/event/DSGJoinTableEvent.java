package org.pente.gameServer.event;

public class DSGJoinTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = 4698479184545521570L;

    public static final int CREATE_NEW_TABLE = -1;

    public DSGJoinTableEvent() {
    }

    public DSGJoinTableEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        if (getTable() == CREATE_NEW_TABLE) {
            return "create new table";
        } else {
            return "join " + super.toString();
        }
    }
}

