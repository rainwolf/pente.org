package org.pente.gameServer.event;

/** Requester takes back their pending request to a table (spec R3). */
public class DSGArenaWithdrawJoinRequestEvent extends AbstractDSGTableEvent {

    public DSGArenaWithdrawJoinRequestEvent() {
        super();
    }

    public DSGArenaWithdrawJoinRequestEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "Arena withdraw join request " + super.toString();
    }
}
