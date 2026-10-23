package org.pente.gameServer.event;

import org.pente.gameServer.core.*;
import org.pente.gameServer.kids.LiveKidState;

public class DSGJoinMainRoomEvent extends AbstractDSGEvent implements DSGMainRoomEvent {
    private static final long serialVersionUID = -3695510933898348282L;

    private String player;
    private SimpleDSGPlayerData dsgPlayerData;
    /** Server-side only: transient keeps it off the wire and out of Java serialization. */
    private transient LiveKidState kidState;

    public DSGJoinMainRoomEvent() {
    }

    public DSGJoinMainRoomEvent(String player, DSGPlayerData dsgPlayerData) {
        setPlayer(player);
        DSGPlayerData dataCopy = (DSGPlayerData) dsgPlayerData.clone();
        dataCopy.setPassword("");
        dataCopy.setEmail("");
        dataCopy.setParentPid(null);
        dataCopy.setMobileAgeBand(null);
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

    public LiveKidState getKidState() {
        return kidState;
    }

    public void setKidState(LiveKidState kidState) {
        this.kidState = kidState;
    }

    public String toString() {
        return "join " + getPlayer() + " main room";
    }
}

