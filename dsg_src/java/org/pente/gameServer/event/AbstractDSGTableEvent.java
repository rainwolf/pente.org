package org.pente.gameServer.event;

public abstract class AbstractDSGTableEvent extends AbstractDSGEvent
        implements DSGTableEvent {

    private String player;
    private int table;

    public AbstractDSGTableEvent() {
    }

    public AbstractDSGTableEvent(String player, int table) {
        setPlayer(player);
        setTable(table);
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public String getPlayer() {
        return player;
    }

    public void setTable(int table) {
        this.table = table;
    }

    public int getTable() {
        return table;
    }

    public String toString() {
        return getPlayer() + " table " + getTable();
    }
}

