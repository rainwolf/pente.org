package org.pente.gameServer.event;

import java.util.Arrays;

/** Server to requester: every table they have a pending request at. */
public class DSGArenaMyRequestsEvent extends AbstractDSGEvent {
    private static final long serialVersionUID = -4033538274008844325L;

    private int[] tables;

    public DSGArenaMyRequestsEvent(int[] tables) {
        this.tables = tables.clone();
    }

    public int[] getTables() {
        return tables.clone();
    }

    public String toString() {
        return "Arena my requests " + Arrays.toString(tables);
    }
}
