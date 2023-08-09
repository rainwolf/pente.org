package org.pente.gameServer.tourney;

import org.pente.gameServer.event.*;

public class TourneyEvent extends AbstractDSGEvent {

    public static final int NEW_ROUND = 1;
    public static final int COMPLETE = 2;
    public static final int PLAYER_REGISTER = 3;
    public static final int PLAYER_DROP = 4;
    private int type;
    private int eid;

    private Object data;

    public TourneyEvent(int eid, int type) {
        this.eid = eid;
        this.type = type;
    }

    public TourneyEvent(int eid, int type, Object data) {
        this.eid = eid;
        this.type = type;
        this.data = data;
    }

    public int getEid() {
        return eid;
    }

    public int getType() {
        return type;
    }

    public Object getData() {
        return data;
    }

    public String toString() {
        if (data != null) {
            return "tourney: " + eid + " " + type + " " + data;
        } else {
            return "tourney: " + eid + " " + type;
        }
    }
}
