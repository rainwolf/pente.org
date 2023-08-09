package org.pente.gameServer.event;

import org.pente.gameServer.core.*;

public class DSGJoinMainRoomEvent extends AbstractDSGEvent implements DSGMainRoomEvent {

    private String player;
    private SimpleDSGPlayerData dsgPlayerData;

    public DSGJoinMainRoomEvent() {
    }

    public DSGJoinMainRoomEvent(String player, DSGPlayerData dsgPlayerData) {
        setPlayer(player);
        DSGPlayerData dataCopy = (DSGPlayerData) dsgPlayerData.clone();
        dataCopy.setPassword("");
        dataCopy.setEmail("");
        setDSGPlayerData(dataCopy);
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public String getPlayer() {
        return player;
    }

    public DSGPlayerData getDSGPlayerData() {
        return dsgPlayerData;
    }

    public void setDSGPlayerData(DSGPlayerData dsgPlayerData) {
        this.dsgPlayerData = (SimpleDSGPlayerData) dsgPlayerData;
    }

    public String toString() {
        return "join " + getPlayer() + " main room";
    }
}

