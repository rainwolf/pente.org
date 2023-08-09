package org.pente.gameServer.event;

public interface DSGTableEvent extends DSGEvent {

    public void setPlayer(String player);

    public String getPlayer();

    public void setTable(int table);

    public int getTable();
}

