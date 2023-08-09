package org.pente.gameServer.event;

public class DSGChangeStateTableErrorEvent extends DSGChangeStateTableEvent {

    private int error;

    public DSGChangeStateTableErrorEvent() {
        super();
    }

    public DSGChangeStateTableErrorEvent(String player, int table, int error) {
        super(player, table);

        setError(error);
    }

    public void setError(int error) {
        this.error = error;
    }

    public int getError() {
        return error;
    }

    public String toString() {
        return super.toString() + " error " + getError();
    }
}

