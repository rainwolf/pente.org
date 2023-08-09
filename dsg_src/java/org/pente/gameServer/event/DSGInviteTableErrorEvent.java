
package org.pente.gameServer.event;

public class DSGInviteTableErrorEvent extends AbstractDSGTableErrorEvent {

    private String toInvite;

    public DSGInviteTableErrorEvent() {
        super();
    }

    public DSGInviteTableErrorEvent(String player, int table,
                                    String toInvite, int error) {
        super(player, table, error);

        this.toInvite = toInvite;
    }

    public String getPlayerToInvite() {
        return toInvite;
    }

    public String toString() {
        return "invite " + toInvite + " " + super.toString();
    }
}
