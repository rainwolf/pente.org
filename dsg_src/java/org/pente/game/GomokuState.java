package org.pente.game;

public interface GomokuState extends GridState {

    public void allowOverlines(boolean allow);

    public boolean areOverlinesAllowed();
}
