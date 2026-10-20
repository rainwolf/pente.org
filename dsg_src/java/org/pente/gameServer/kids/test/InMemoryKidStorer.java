package org.pente.gameServer.kids.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.kids.KidLinkRequest;
import org.pente.gameServer.kids.KidSettings;
import org.pente.gameServer.kids.KidStorer;

/** Test fake: everything in maps, with read counters and a read-failure switch. */
public class InMemoryKidStorer implements KidStorer {

    public final Map<Long, Set<Long>> whitelists = new HashMap<Long, Set<Long>>();
    public final Map<Long, KidSettings> settings = new HashMap<Long, KidSettings>();
    public final Map<Long, Long> parentPids = new HashMap<Long, Long>();
    public final Map<String, KidLinkRequest> linkRequests = new HashMap<String, KidLinkRequest>();
    public int whitelistReads;
    public int settingsReads;
    public boolean failReads;

    private void checkReads() throws DSGPlayerStoreException {
        if (failReads) {
            throw new DSGPlayerStoreException("read failed (test)");
        }
    }

    public Set<Long> getWhitelist(long kidPid) throws DSGPlayerStoreException {
        checkReads();
        whitelistReads++;
        Set<Long> s = whitelists.get(kidPid);
        return s == null ? new HashSet<Long>() : new HashSet<Long>(s);
    }

    public void addToWhitelist(long kidPid, long allowedPid) {
        Set<Long> s = whitelists.get(kidPid);
        if (s == null) {
            s = new HashSet<Long>();
            whitelists.put(kidPid, s);
        }
        s.add(allowedPid);
    }

    public void removeFromWhitelist(long kidPid, long allowedPid) {
        Set<Long> s = whitelists.get(kidPid);
        if (s != null) {
            s.remove(allowedPid);
        }
    }

    public KidSettings getSettings(long kidPid) throws DSGPlayerStoreException {
        checkReads();
        settingsReads++;
        KidSettings s = settings.get(kidPid);
        return s == null ? KidSettings.off(kidPid) : s;
    }

    public void storeSettings(KidSettings s) {
        settings.put(s.getKidPid(), s);
    }

    public void deleteKidData(long kidPid) {
        whitelists.remove(kidPid);
        settings.remove(kidPid);
    }

    public void setParentPid(long kidPid, Long parentPid) {
        if (parentPid == null) {
            parentPids.remove(kidPid);
        } else {
            parentPids.put(kidPid, parentPid);
        }
    }

    public List<Long> getKidPids(long parentPid) {
        List<Long> kids = new ArrayList<Long>();
        for (Map.Entry<Long, Long> e : parentPids.entrySet()) {
            if (e.getValue() == parentPid) {
                kids.add(e.getKey());
            }
        }
        return kids;
    }

    public void storeLinkRequest(KidLinkRequest r) {
        linkRequests.put(r.getCode(), r);
    }

    public KidLinkRequest getLinkRequest(String code) {
        return linkRequests.get(code);
    }

    public void deleteLinkRequest(String code) {
        linkRequests.remove(code);
    }
}
