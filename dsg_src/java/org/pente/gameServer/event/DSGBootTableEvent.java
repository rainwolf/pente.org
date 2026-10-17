package org.pente.gameServer.event;

public class DSGBootTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = 5988479915236137402L;

    private String toBoot;

    public DSGBootTableEvent() {
        super();
    }

    public DSGBootTableEvent(String player, int table, String toBoot) {
        super(player, table);

        this.toBoot = toBoot;
    }

    public String getPlayerToBoot() {
        return toBoot;
    }

    public String toString() {
        return "boot " + toBoot + " " + super.toString();
    }
}
