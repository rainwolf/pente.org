package org.pente.gameServer.event;

public class DSGBootTableErrorEvent extends AbstractDSGTableErrorEvent {


    private String toBoot;

    public DSGBootTableErrorEvent() {
        super();
    }

    public DSGBootTableErrorEvent(String player, int table, String toBoot,
                                  int error) {

        super(player, table, error);

        this.toBoot = toBoot;
    }

    public String getPlayerToBoot() {
        return toBoot;
    }

    public String toString() {
        return "boot " + toBoot + " " + super.toString();
    }
}
