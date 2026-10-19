package org.pente.gameServer.kids;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.Category;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.server.RedisConnectionManager;

/**
 * Redis write-through cache over a KidStorer. Whitelist and settings writes
 * (and deleteKidData) hit the base, drop the kid's cache entries and notify
 * listeners; everything else passes through uncached.
 */
public class CacheKidStorer implements KidStorer {

    private static final Category log4j = Category.getInstance(CacheKidStorer.class.getName());

    private final KidStorer base;
    private final List<KidDataChangeListener> listeners =
        new CopyOnWriteArrayList<KidDataChangeListener>();

    public CacheKidStorer(KidStorer base) {
        this.base = base;
    }

    private RedisConnectionManager cache() {
        return RedisConnectionManager.getInstance();
    }

    public void addKidDataChangeListener(KidDataChangeListener l) {
        listeners.add(l);
    }

    public void removeKidDataChangeListener(KidDataChangeListener l) {
        listeners.remove(l);
    }

    public void notifyKidListeners(long kidPid) {
        for (KidDataChangeListener l : listeners) {
            try {
                l.kidDataChanged(kidPid);
            } catch (Throwable t) {
                log4j.error("KidDataChangeListener failed for kid " + kidPid, t);
            }
        }
    }

    private void invalidate(long kidPid) {
        cache().hremove(RedisConnectionManager.PID_TO_KID_WHITELIST, kidPid);
        cache().hremove(RedisConnectionManager.PID_TO_KID_SETTINGS, kidPid);
    }

    public Set<Long> getWhitelist(long kidPid) throws DSGPlayerStoreException {
        HashSet<Long> cached = cache().hget(RedisConnectionManager.PID_TO_KID_WHITELIST, kidPid);
        if (cached != null) {
            return cached;
        }
        HashSet<Long> fresh = new HashSet<Long>(base.getWhitelist(kidPid));
        cache().hput(RedisConnectionManager.PID_TO_KID_WHITELIST, kidPid, fresh);
        return fresh;
    }

    public void addToWhitelist(long kidPid, long allowedPid) throws DSGPlayerStoreException {
        base.addToWhitelist(kidPid, allowedPid);
        invalidate(kidPid);
        notifyKidListeners(kidPid);
    }

    public void removeFromWhitelist(long kidPid, long allowedPid) throws DSGPlayerStoreException {
        base.removeFromWhitelist(kidPid, allowedPid);
        invalidate(kidPid);
        notifyKidListeners(kidPid);
    }

    public KidSettings getSettings(long kidPid) throws DSGPlayerStoreException {
        KidSettings cached = cache().hget(RedisConnectionManager.PID_TO_KID_SETTINGS, kidPid);
        if (cached != null) {
            return cached;
        }
        KidSettings fresh = base.getSettings(kidPid);
        cache().hput(RedisConnectionManager.PID_TO_KID_SETTINGS, kidPid, fresh);
        return fresh;
    }

    public void storeSettings(KidSettings s) throws DSGPlayerStoreException {
        base.storeSettings(s);
        invalidate(s.getKidPid());
        notifyKidListeners(s.getKidPid());
    }

    public void deleteKidData(long kidPid) throws DSGPlayerStoreException {
        base.deleteKidData(kidPid);
        invalidate(kidPid);
        notifyKidListeners(kidPid);
    }

    public void setParentPid(long kidPid, Long parentPid) throws DSGPlayerStoreException {
        base.setParentPid(kidPid, parentPid);
    }

    public List<Long> getKidPids(long parentPid) throws DSGPlayerStoreException {
        return base.getKidPids(parentPid);
    }

    public void storeLinkRequest(KidLinkRequest r) throws DSGPlayerStoreException {
        base.storeLinkRequest(r);
    }

    public KidLinkRequest getLinkRequest(String code) throws DSGPlayerStoreException {
        return base.getLinkRequest(code);
    }

    public void deleteLinkRequest(String code) throws DSGPlayerStoreException {
        base.deleteLinkRequest(code);
    }
}
