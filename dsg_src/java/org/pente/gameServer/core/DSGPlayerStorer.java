/** DSGPlayerStorer.java
 *  Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
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

public interface DSGPlayerStorer {

    public void insertPlayer(DSGPlayerData dsgPlayerData) throws DSGPlayerStoreException;
    public void updatePlayer(DSGPlayerData dsgPlayerData) throws DSGPlayerStoreException;
    public DSGPlayerData loadPlayer(long playerID) throws DSGPlayerStoreException;
    public DSGPlayerData loadPlayer(String name) throws DSGPlayerStoreException;


    public void deleteAvatar(DSGPlayerData dsgPlayerData)
        throws DSGPlayerStoreException;
    public void insertAvatar(DSGPlayerData dsgPlayerData)
    	throws DSGPlayerStoreException;
	public void insertDonation(DSGDonationData dsgDonationData, long playerID) throws DSGPlayerStoreException;
	public Collection getDonations(long playerID) throws DSGPlayerStoreException;
	public List<DSGDonationData> getAllPlayersWhoDonated() throws DSGPlayerStoreException;

    public void insertGame(DSGPlayerGameData dsgPlayerGameData) throws DSGPlayerStoreException;
    public void updateGame(DSGPlayerGameData dsgPlayerGameData) throws DSGPlayerStoreException;
    public DSGPlayerGameData loadGame(int game, long playerID, boolean computer) throws DSGPlayerStoreException;
    public Vector loadAllGames(long playerID) throws DSGPlayerStoreException;
    
    // needed for server table
    public void insertLiveSet(LiveSet set) throws DSGPlayerStoreException;
    public void updateLiveSet(LiveSet set) throws DSGPlayerStoreException;

    // load the set data and the games too?
    public LiveSet loadLiveSet(long sid) throws DSGPlayerStoreException;
    
    public List loadPlayerPreferences(long playerID) throws DSGPlayerStoreException;
    public void storePlayerPreference(long playerID, DSGPlayerPreference pref)
        throws DSGPlayerStoreException;
    
    public void insertIgnore(DSGIgnoreData data) throws DSGPlayerStoreException;
    public List<DSGIgnoreData> getIgnoreData(long pid) throws DSGPlayerStoreException;
    public DSGIgnoreData getIgnoreData(long pid, long ignorePid) throws DSGPlayerStoreException;
    public void deleteIgnore(DSGIgnoreData data) throws DSGPlayerStoreException;
    public void updateIgnore(DSGIgnoreData data) throws DSGPlayerStoreException;
    
    public List<java.util.Date> loadVacationDays(long playerID) throws DSGPlayerStoreException;
    public void storeVacationDays(long playerID, List<Date> vacationDays) throws DSGPlayerStoreException;
    
    public Vector search(
        int game, int sortField, 
        int startNum, int length, 
        boolean showProvisional, boolean showInactive,
        int playerType) throws DSGPlayerStoreException;

    public int getNumPlayers(
        int game, boolean showProvisional,
        boolean showInactive, int playerType)
        throws DSGPlayerStoreException;
}