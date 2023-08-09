package org.pente.gameDatabase.swing;

public interface MoveChangeListener {

    public void changeMoves(int moves[], PlunkNode current);

    public void nodeChanged();
}
