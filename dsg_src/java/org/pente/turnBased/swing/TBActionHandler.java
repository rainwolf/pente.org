package org.pente.turnBased.swing;

public interface TBActionHandler {
    public void makeMoves(String moves, String message);

    public void viewProfile(String name);

    public void resignGame(String message);

    public void viewGame(long game);

    public void requestCancel(String message);
}
