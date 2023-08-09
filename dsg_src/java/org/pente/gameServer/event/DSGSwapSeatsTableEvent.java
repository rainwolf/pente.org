package org.pente.gameServer.event;

public class DSGSwapSeatsTableEvent extends AbstractDSGTableEvent {

    boolean swap = false;
    boolean silent = false;

    public DSGSwapSeatsTableEvent() {
    }

    public DSGSwapSeatsTableEvent(String player, int table, boolean swap,
                                  boolean silent) {
        super(player, table);

        this.swap = swap;
        this.silent = silent;
    }

    public boolean wantsToSwap() {
        return swap;
    }

    public boolean isSilent() {
        return silent;
    }

    public String toString() {
        return (swap ? "swap seats " : "don't swap seats ") +
                super.toString();
    }
}

