package org.pente.gameServer.kids;

import java.util.List;
import java.util.Set;

import org.pente.gameServer.core.DSGPlayerStoreException;

/** Parent-owned data for kid accounts: whitelist, copy settings, link requests. */
public interface KidStorer {

    /** Never null. */
    Set<Long> getWhitelist(long kidPid) throws DSGPlayerStoreException;

    /** Idempotent. */
    void addToWhitelist(long kidPid, long allowedPid) throws DSGPlayerStoreException;

    void removeFromWhitelist(long kidPid, long allowedPid) throws DSGPlayerStoreException;

    /** {@link KidSettings#off} when no row. */
    KidSettings getSettings(long kidPid) throws DSGPlayerStoreException;

    /** Upsert. */
    void storeSettings(KidSettings s) throws DSGPlayerStoreException;

    /** Deletes whitelist and settings rows. */
    void deleteKidData(long kidPid) throws DSGPlayerStoreException;

    /**
     * The only writer of dsg_player.parent_pid after insert. Caller refreshes
     * the player cache afterwards, then calls deleteKidData.
     */
    void setParentPid(long kidPid, Long parentPid) throws DSGPlayerStoreException;

    List<Long> getKidPids(long parentPid) throws DSGPlayerStoreException;

    /** Deletes rows for the same kid and rows older than 7 days, then inserts. */
    void storeLinkRequest(KidLinkRequest r) throws DSGPlayerStoreException;

    /** Null if absent. */
    KidLinkRequest getLinkRequest(String code) throws DSGPlayerStoreException;

    void deleteLinkRequest(String code) throws DSGPlayerStoreException;
}
