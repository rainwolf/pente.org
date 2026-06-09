package org.pente.turnBased.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import org.pente.gameServer.core.DSGDonationData;
import org.pente.gameServer.core.DSGIgnoreData;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.core.DSGPlayerPreference;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.gameServer.core.LiveSet;
import org.pente.gameServer.core.SimpleDSGPlayerData;

/**
 * Minimal in-memory DSGPlayerStorer for CacheTBStorer tests.
 *
 * <p>Only the methods exercised by the turn-based move path are meaningful:
 * {@link #loadPlayerPreferences(long)} returns an empty list so
 * {@code Utilities.calculateNewTimeout} falls back to its default sat/sun
 * weekend, and {@link #loadPlayer(long)} returns a player whose timezone is a
 * valid id ("UTC") so {@code TimeZone.getTimeZone(...)} does not NPE. Every
 * other interface method is a harmless no-op default.
 */
public class InMemoryDSGPlayerStorer implements DSGPlayerStorer {

    public void insertPlayer(DSGPlayerData dsgPlayerData) throws DSGPlayerStoreException {
    }

    public void updatePlayer(DSGPlayerData dsgPlayerData) throws DSGPlayerStoreException {
    }

    public DSGPlayerData loadPlayer(long playerID) throws DSGPlayerStoreException {
        SimpleDSGPlayerData data = new SimpleDSGPlayerData();
        data.setPlayerID(playerID);
        data.setTimezone("UTC");
        return data;
    }

    public DSGPlayerData loadPlayer(String name) throws DSGPlayerStoreException {
        SimpleDSGPlayerData data = new SimpleDSGPlayerData();
        data.setName(name);
        data.setTimezone("UTC");
        return data;
    }

    public void deleteAvatar(DSGPlayerData dsgPlayerData) throws DSGPlayerStoreException {
    }

    public void insertAvatar(DSGPlayerData dsgPlayerData) throws DSGPlayerStoreException {
    }

    public void insertDonation(DSGDonationData dsgDonationData, long playerID) throws DSGPlayerStoreException {
    }

    public Collection getDonations(long playerID) throws DSGPlayerStoreException {
        return new ArrayList();
    }

    public List<DSGDonationData> getAllPlayersWhoDonated() throws DSGPlayerStoreException {
        return new ArrayList<DSGDonationData>();
    }

    public void insertGame(DSGPlayerGameData dsgPlayerGameData) throws DSGPlayerStoreException {
    }

    public void updateGame(DSGPlayerGameData dsgPlayerGameData) throws DSGPlayerStoreException {
    }

    public DSGPlayerGameData loadGame(int game, long playerID, boolean computer) throws DSGPlayerStoreException {
        return null;
    }

    public Vector loadAllGames(long playerID) throws DSGPlayerStoreException {
        return new Vector();
    }

    public void insertLiveSet(LiveSet set) throws DSGPlayerStoreException {
    }

    public void updateLiveSet(LiveSet set) throws DSGPlayerStoreException {
    }

    public LiveSet loadLiveSet(long sid) throws DSGPlayerStoreException {
        return null;
    }

    public List<DSGPlayerPreference> loadPlayerPreferences(long playerID) throws DSGPlayerStoreException {
        return new ArrayList<DSGPlayerPreference>();
    }

    public void storePlayerPreference(long playerID, DSGPlayerPreference pref) throws DSGPlayerStoreException {
    }

    public void insertIgnore(DSGIgnoreData data) throws DSGPlayerStoreException {
    }

    public List<DSGIgnoreData> getIgnoreData(long pid) throws DSGPlayerStoreException {
        return new ArrayList<DSGIgnoreData>();
    }

    public DSGIgnoreData getIgnoreData(long pid, long ignorePid) throws DSGPlayerStoreException {
        return null;
    }

    public void deleteIgnore(DSGIgnoreData data) throws DSGPlayerStoreException {
    }

    public void updateIgnore(DSGIgnoreData data) throws DSGPlayerStoreException {
    }

    public List<Date> loadVacationDays(long playerID) throws DSGPlayerStoreException {
        return new ArrayList<Date>();
    }

    public void storeVacationDays(long playerID, List<Date> vacationDays) throws DSGPlayerStoreException {
    }

    public Vector search(
            int game, int sortField,
            int startNum, int length,
            boolean showProvisional, boolean showInactive,
            int playerType) throws DSGPlayerStoreException {
        return new Vector();
    }

    public int getNumPlayers(
            int game, boolean showProvisional,
            boolean showInactive, int playerType) throws DSGPlayerStoreException {
        return 0;
    }

    public String insertEmailVerificationCode(long playerID) throws DSGPlayerStoreException {
        return null;
    }

    public long verifyEmailCode(String code) throws DSGPlayerStoreException {
        return 0;
    }
}
