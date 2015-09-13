/** CacheSGPlayerStorer.java
 *  Copyright (C) 2003 Dweebo's Stone Games (http://www.pente.org/)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, you can find it online at
 *  http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.core;

import java.util.*;

import org.apache.log4j.*;

public class CacheDSGPlayerStorer implements DSGPlayerStorer {

    private static final Category log4j = 
        Category.getInstance(CacheDSGPlayerStorer.class.getName());

    private DSGPlayerStorer basePlayerStorer;
    private Hashtable<Long, DSGPlayerData> cacheByID;
    private Hashtable<String, DSGPlayerData> cacheByName;

    private Map<Long, List<DSGIgnoreData>> ignoreData;
    
    private List<PlayerDataChangeListener> listeners;
    private List<IgnoreDataChangeListener> ignoreListeners;
    private List<DSGDonationData> donors;
    
    public CacheDSGPlayerStorer(DSGPlayerStorer basePlayerStorer) throws Exception {
        this.basePlayerStorer = basePlayerStorer;
        cacheByID = new Hashtable<Long, DSGPlayerData>(250);
        cacheByName = new Hashtable<String, DSGPlayerData>(250);
        listeners = new ArrayList<PlayerDataChangeListener>();
        ignoreData = new HashMap<Long, List<DSGIgnoreData>>();
        ignoreListeners = new ArrayList<IgnoreDataChangeListener>();
    }

    public synchronized void addPlayerDataChangeListener(
        PlayerDataChangeListener l) {
        listeners.add(l);
    }
    public synchronized void removePlayerDataChangeListener(
        PlayerDataChangeListener l) {
        listeners.remove(l);
    }
    public synchronized void addIgnoreDataChangeListener(
        IgnoreDataChangeListener l) {
        ignoreListeners.add(l);
    }
    public synchronized void removeIgnoreDataChangeListener(
        IgnoreDataChangeListener l) {
        ignoreListeners.remove(l);
    }

    public void notifyListeners(DSGPlayerData dsgPlayerData) {
        for (Iterator i = listeners.iterator(); i.hasNext();) {
            PlayerDataChangeListener l = (PlayerDataChangeListener)
                i.next();
            l.playerChanged(dsgPlayerData);
        }
    }
    public void notifyIgnoreListeners(long pid) throws DSGPlayerStoreException {

        for (IgnoreDataChangeListener l : ignoreListeners) {
            l.ignoreDataChanged(pid);
        }
    }

    public synchronized void refreshPlayer(String name) throws DSGPlayerStoreException {
        log4j.debug("flushPlayer(" + name + ")");
        DSGPlayerData newData = basePlayerStorer.loadPlayer(name);
        cacheByID.put(new Long(newData.getPlayerID()), newData);
        cacheByName.put(newData.getName(), newData);
        
        List<DSGIgnoreData> ignore = basePlayerStorer.getIgnoreData(newData.getPlayerID());
        ignoreData.put(newData.getPlayerID(), ignore);
    }
    
    public synchronized void insertPlayer(DSGPlayerData dsgPlayerData) throws DSGPlayerStoreException {
        log4j.debug("insertPlayer(" + dsgPlayerData.getName() + ")");
        basePlayerStorer.insertPlayer(dsgPlayerData);

        // reload from db since can't be certain nothing was added in sql and not in data class
        DSGPlayerData newData = basePlayerStorer.loadPlayer(dsgPlayerData.getPlayerID());
        cacheByID.put(new Long(dsgPlayerData.getPlayerID()), newData);
        cacheByName.put(dsgPlayerData.getName(), newData);
    }


    public synchronized void deleteAvatar(DSGPlayerData dsgPlayerData)
        throws DSGPlayerStoreException {
        dsgPlayerData.setAvatar(null);
        basePlayerStorer.deleteAvatar(dsgPlayerData);
    }
    public void insertAvatar(DSGPlayerData dsgPlayerData)
        throws DSGPlayerStoreException {
        basePlayerStorer.insertAvatar(dsgPlayerData);
    }
    
    public synchronized void updatePlayer(DSGPlayerData dsgPlayerData) throws DSGPlayerStoreException {
        log4j.debug("updatePlayer(" + dsgPlayerData.getName() + ")");
        basePlayerStorer.updatePlayer(dsgPlayerData);

        DSGPlayerData newData = null;
        if (!dsgPlayerData.isActive()) {
            log4j.debug("player not active, remove from cache");
            cacheByID.remove(dsgPlayerData);
            cacheByName.remove(dsgPlayerData);
            newData = dsgPlayerData;
        }
        else {
            log4j.debug("player active, update cache from db");
            // reload from db since can't be certain nothing was added in sql and not in data class
            newData = basePlayerStorer.loadPlayer(dsgPlayerData.getPlayerID());
            cacheByID.put(new Long(dsgPlayerData.getPlayerID()), newData);
            cacheByName.put(dsgPlayerData.getName(), newData);
            
            if (newData.hasPlayerDonated()) {
                checkUpdateDonors(newData);
            }
        }
        notifyListeners(newData);
    }

    public synchronized DSGPlayerData loadPlayer(long playerID) throws DSGPlayerStoreException {

        log4j.debug("loadPlayer(" + playerID + ")");
        DSGPlayerData dsgPlayerData = null;
        dsgPlayerData = (DSGPlayerData) cacheByID.get(new Long(playerID));
        if (dsgPlayerData == null) {
            log4j.debug("not cached");
            dsgPlayerData = basePlayerStorer.loadPlayer(playerID);
            if (dsgPlayerData != null) {
                log4j.debug("caching");
                cacheByID.put(new Long(dsgPlayerData.getPlayerID()), dsgPlayerData);
                cacheByName.put(dsgPlayerData.getName(), dsgPlayerData);
            }
        }
        if (dsgPlayerData == null) {
            return null;
        }
        else {
            return (DSGPlayerData) dsgPlayerData.clone();
        }
    }

    public synchronized DSGPlayerData loadPlayer(String name) throws DSGPlayerStoreException {

        log4j.debug("loadPlayer(" + name + ")");
        DSGPlayerData dsgPlayerData = null;
        dsgPlayerData = (DSGPlayerData) cacheByName.get(name);
        if (dsgPlayerData == null) {
            log4j.debug("not cached");
            dsgPlayerData = basePlayerStorer.loadPlayer(name);
            if (dsgPlayerData != null) {
                log4j.debug("caching");
                cacheByID.put(new Long(dsgPlayerData.getPlayerID()), dsgPlayerData);
                cacheByName.put(dsgPlayerData.getName(), dsgPlayerData);
            }
        }
        if (dsgPlayerData == null) {
            return null;
        }
        else {
            return (DSGPlayerData) dsgPlayerData.clone();
        }
    }


    public void insertDonation(DSGDonationData dsgDonationData, long playerID) throws DSGPlayerStoreException {
        basePlayerStorer.insertDonation(dsgDonationData, playerID);

        DSGPlayerData dsgPlayerData = (DSGPlayerData) cacheByID.get(new Long(
            playerID));
        if (dsgPlayerData == null) {
            log4j.debug("not cached");
            dsgPlayerData = basePlayerStorer.loadPlayer(playerID);
            if (dsgPlayerData != null) {
                log4j.debug("caching");
                cacheByID.put(new Long(dsgPlayerData.getPlayerID()), dsgPlayerData);
                cacheByName.put(dsgPlayerData.getName(), dsgPlayerData);
            }
        }
        notifyListeners(dsgPlayerData);
    }
    public Collection getDonations(long playerID) throws DSGPlayerStoreException {
        return basePlayerStorer.getDonations(playerID);
    }
    public synchronized List<DSGDonationData> getAllPlayersWhoDonated() throws DSGPlayerStoreException {
        if (donors == null) {
            donors = basePlayerStorer.getAllPlayersWhoDonated();
        }
        return donors;
    }
    public synchronized void checkUpdateDonors(DSGPlayerData data) throws DSGPlayerStoreException {
        getAllPlayersWhoDonated();
        for (DSGDonationData d : donors) {
            if (d.getPid() == data.getPlayerID()) return;//player already donated
        }
        donors = basePlayerStorer.getAllPlayersWhoDonated();
    }

    public synchronized void insertGame(DSGPlayerGameData dsgPlayerGameData) throws DSGPlayerStoreException {
        log4j.debug("insertGame(" + dsgPlayerGameData.getPlayerID() + ", " + dsgPlayerGameData.getGame() + ", " + dsgPlayerGameData.isHumanScore() + ")");
        basePlayerStorer.insertGame(dsgPlayerGameData);
        DSGPlayerData dsgPlayerData = (DSGPlayerData) cacheByID.get(new Long(dsgPlayerGameData.getPlayerID()));
        if (dsgPlayerData == null) {
            log4j.debug("not cached");
            dsgPlayerData = basePlayerStorer.loadPlayer(dsgPlayerGameData.getPlayerID());
            if (dsgPlayerData != null) {
                log4j.debug("caching");
                cacheByID.put(new Long(dsgPlayerData.getPlayerID()), dsgPlayerData);
                cacheByName.put(dsgPlayerData.getName(), dsgPlayerData);
            }
        }
        else {
            dsgPlayerData.setPlayerGameData((DSGPlayerGameData) dsgPlayerGameData.clone());
        }
    }
    public synchronized void updateGame(DSGPlayerGameData dsgPlayerGameData) throws DSGPlayerStoreException {
        log4j.debug("updateGame(" + dsgPlayerGameData.getPlayerID() + ", " + dsgPlayerGameData.getGame() + ", " + dsgPlayerGameData.isHumanScore() + ")");
        basePlayerStorer.updateGame(dsgPlayerGameData);
        DSGPlayerData dsgPlayerData = (DSGPlayerData) cacheByID.get(new Long(dsgPlayerGameData.getPlayerID()));
        if (dsgPlayerData == null) {
            log4j.debug("not cached");
            dsgPlayerData = basePlayerStorer.loadPlayer(dsgPlayerGameData.getPlayerID());
            if (dsgPlayerData != null) {
                log4j.debug("caching");
                cacheByID.put(new Long(dsgPlayerData.getPlayerID()), dsgPlayerData);
                cacheByName.put(dsgPlayerData.getName(), dsgPlayerData);
            }
        }
        else {
            dsgPlayerData.setPlayerGameData((DSGPlayerGameData) dsgPlayerGameData.clone());
        }
        notifyListeners(dsgPlayerData);
    }

    public synchronized DSGPlayerGameData loadGame(int game, long playerID, boolean computer)
        throws DSGPlayerStoreException {
    
        log4j.debug("loadGame(" + playerID + ", " + game + ", " + !computer + ")");
        DSGPlayerData dsgPlayerData = (DSGPlayerData) cacheByID.get(new Long(playerID));
        if (dsgPlayerData == null) {
            log4j.debug("not cached");
            dsgPlayerData = basePlayerStorer.loadPlayer(playerID);
            if (dsgPlayerData != null) {
                log4j.debug("caching");
                cacheByID.put(new Long(dsgPlayerData.getPlayerID()), dsgPlayerData);
                cacheByName.put(dsgPlayerData.getName(), dsgPlayerData);
            }
        }
        
        if (dsgPlayerData == null) {
            return null;
        }
        else {
            return (DSGPlayerGameData) dsgPlayerData.getPlayerGameData(game, computer).clone();
        }
    }

    public synchronized Vector loadAllGames(long playerID) throws DSGPlayerStoreException {

        log4j.debug("loadAllGames(" + playerID + ")");
        DSGPlayerData dsgPlayerData = (DSGPlayerData) cacheByID.get(new Long(playerID));
        if (dsgPlayerData == null) {
            log4j.debug("not cached");
            dsgPlayerData = basePlayerStorer.loadPlayer(playerID);
            if (dsgPlayerData != null) {
                log4j.debug("caching");
                cacheByID.put(new Long(dsgPlayerData.getPlayerID()), dsgPlayerData);
                cacheByName.put(dsgPlayerData.getName(), dsgPlayerData);
            }
        }
        if (dsgPlayerData == null) {
            return null;
        }
        else {
            Vector<DSGPlayerGameData> newGames = new Vector<DSGPlayerGameData>(dsgPlayerData.getAllPlayerGameData().size());
            for (int i = 0; i < dsgPlayerData.getAllPlayerGameData().size(); i++) {
                newGames.add((DSGPlayerGameData) ((DSGPlayerGameData) dsgPlayerData.getAllPlayerGameData().get(i)).clone());
            }
            return newGames;
        }
    }

    
    public Vector search(
        int game, int sortField, 
        int startNum, int length, 
        boolean showProvisional, boolean showInactive,
        int playerType) throws DSGPlayerStoreException {
    
        return basePlayerStorer.search(game, sortField, startNum, length, showProvisional, showInactive, playerType);                   
    }

    public int getNumPlayers(
        int game,
        boolean showProvisional,
        boolean showInactive,
        int playerType)
        throws DSGPlayerStoreException {

        return basePlayerStorer.getNumPlayers(game, showProvisional, showInactive, playerType);
    }

    // no great need to cache these i don't think
    public List<DSGPlayerPreference> loadPlayerPreferences(long playerID)
        throws DSGPlayerStoreException {
        
        return basePlayerStorer.loadPlayerPreferences(playerID);
    }
    public void storePlayerPreference(long playerID, DSGPlayerPreference pref)
        throws DSGPlayerStoreException {
        basePlayerStorer.storePlayerPreference(playerID, pref);
    }
    public List<java.util.Date> loadVacationDays(long playerID) throws DSGPlayerStoreException {
        return basePlayerStorer.loadVacationDays(playerID);
    }
    public void storeVacationDays(long playerID, List<Date> vacationDays) throws DSGPlayerStoreException {
        basePlayerStorer.storeVacationDays(playerID, vacationDays);
    }
    public int loadFloatingVacationDays(long playerID) throws DSGPlayerStoreException {
        return basePlayerStorer.loadFloatingVacationDays(playerID);
    } 
    public void pinchFloatingVacationDays(long playerID) throws DSGPlayerStoreException {
        basePlayerStorer.pinchFloatingVacationDays(playerID);
    }
    public void addFloatingVacationDays(long playerID, int extraDays) throws DSGPlayerStoreException {
        basePlayerStorer.addFloatingVacationDays(playerID, extraDays);
    }
    
    public void deleteIgnore(DSGIgnoreData data) throws DSGPlayerStoreException {
        if (!data.isGuest()) {
            basePlayerStorer.deleteIgnore(data);
        }
        
        List<DSGIgnoreData> l = ignoreData.get(data.getPid());
        if (l != null) { //shouldn't be
            l.remove(data);
        }
    }
    
    public synchronized void insertIgnore(DSGIgnoreData data) throws DSGPlayerStoreException {
        if (!data.isGuest()) {
            basePlayerStorer.insertIgnore(data);
        }

        cacheIgnoreData(data);
    }
    
    private void cacheIgnoreData(DSGIgnoreData data) throws DSGPlayerStoreException {
        List<DSGIgnoreData> l = ignoreData.get(data.getPid());
        if (l == null) {
            l = new ArrayList<DSGIgnoreData>();
            ignoreData.put(data.getPid(), l);
        }
        l.add(data);
    }
    public synchronized DSGIgnoreData getIgnoreData(long pid, long ignorePid) throws DSGPlayerStoreException {
        List<DSGIgnoreData> l = ignoreData.get(pid);
        if (l != null) {
            for (DSGIgnoreData d : l) {
                if (d.getIgnorePid() == ignorePid) {
                    return d;
                }
            }
        }

        DSGIgnoreData data = basePlayerStorer.getIgnoreData(pid, ignorePid);
        if (data != null) {
            cacheIgnoreData(data);
        }
        
        return data;
    }
    public synchronized List<DSGIgnoreData> getIgnoreData(long pid) throws DSGPlayerStoreException {
        List<DSGIgnoreData> l = ignoreData.get(pid);
        if (l != null) {
            return new ArrayList<DSGIgnoreData>(l); //wrap it
        }
        
        l = basePlayerStorer.getIgnoreData(pid);
        if (l != null) {
            ignoreData.put(pid, l);
        }

        return new ArrayList<DSGIgnoreData>(l); //wrap it
    }
    public void updateIgnore(DSGIgnoreData data) throws DSGPlayerStoreException {
        if (!data.isGuest()) {
            basePlayerStorer.updateIgnore(data);
        }

        // since we allow direct updates no need to do anything here
    }
    public void insertLiveSet(LiveSet set) throws DSGPlayerStoreException {
        basePlayerStorer.insertLiveSet(set);
    }
    public LiveSet loadLiveSet(long sid) throws DSGPlayerStoreException {
        return basePlayerStorer.loadLiveSet(sid);
    }
    public void updateLiveSet(LiveSet set) throws DSGPlayerStoreException {
        basePlayerStorer.updateLiveSet(set);
    }
}