package org.pente.gameServer.kids;

/** Notified when a kid's whitelist or settings change. */
public interface KidDataChangeListener {
    void kidDataChanged(long kidPid);
}
