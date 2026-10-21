package org.pente.turnBased;

/**
 * Called once for every turn-based game that ended: finished by any rule,
 * resign, timeout or admin fix, or cancelled by agreement while active.
 * Not called for invitations cancelled before the game started.
 */
public interface TBGameEndListener {
    void gameEnded(TBGame game);
}
