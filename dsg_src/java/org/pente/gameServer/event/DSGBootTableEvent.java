package org.pente.gameServer.event;

public class DSGBootTableEvent extends AbstractDSGTableEvent {

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
