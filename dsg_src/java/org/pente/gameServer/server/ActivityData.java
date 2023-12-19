package org.pente.gameServer.server;

import java.net.*;
import java.util.*;
import java.util.StringTokenizer;

import org.apache.log4j.*;

public class ActivityData {

    private static final Category log4j = Category.getInstance(ActivityData.class.getName());

    private String playerName;
    private long serverId = -1; // if activity is on a server
    private byte[] address; // stored in network byte order

    private List<ActivityTableData> activeGames = new ArrayList<>();

    public ActivityData(String playerName, InetAddress address) {
        setPlayerName(playerName);
        this.address = address.getAddress();
    }

    public ActivityData(String playerName, byte[] address) {
        setPlayerName(playerName);
        this.address = address;
    }

    public ActivityData(String playerName, String address) {
        this(playerName, address, -1);
    }

    public ActivityData(String playerName, String address, long serverId) {
        setPlayerName(playerName);
        setServerId(serverId);
        StringTokenizer st = new StringTokenizer(address, ".");
        this.address = new byte[4];
        if (address.indexOf(":") == -1) {
            for (int i = 0; i < 4; i++) {
                this.address[i] = (byte) Integer.parseInt(st.nextToken());
            }
        }
    }

    private void setPlayerName(String name) {
        playerName = "" + name; // if name is null, make name = "null"
    }

    public String getPlayerName() {
        return playerName;
    }

    public byte[] getAddress() {
        return address;
    }

    public String getAddressStr() {
        String ip = null;
        try {
            ip = InetAddress.getByAddress(address).getHostAddress();
        } catch (UnknownHostException e) {
        }
        return ip;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public String toString() {
        if (serverId != -1) {
            return "[" + serverId + " " + playerName + ", " +
                    getAddressStr() + "]";
        } else {
            return "[" + playerName + ", " + getAddressStr() + "]";
        }
    }

    public boolean matches(ActivityData o) {
        if (o.getPlayerName().equals(playerName)) {
            return true;
        } else {
            for (int i = 0; i < 3; i++) {
                if (o.getAddress()[i] != address[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    public void addActiveGame(ActivityTableData game) {
        log4j.debug("addActiveGame(" + game + "), player=" + playerName);
        activeGames.add(game);
    }

    public void removeActiveGame(ActivityTableData game) {
        log4j.debug("removeActiveGame(" + game + "), player=" + playerName);
        activeGames.remove(game);
    }

    public boolean playingRatedGame() {
        log4j.debug("playingRatedGame(), player=" + playerName);
        for (Iterator<ActivityTableData> it = activeGames.iterator(); it.hasNext(); ) {
            ActivityTableData d = it.next();
            log4j.debug("checking " + d);
            if (d.isRated()) return true;
        }
        log4j.debug("return false");
        return false;
    }

    public boolean equals(Object o) {
        ActivityData d = (ActivityData) o;
        if (!d.playerName.equals(playerName)) return false;
        for (int i = 0; i < 4; i++) {
            if (d.address[i] != address[i]) return false;
        }
        return true;
    }

    public int hashCode() {
        return playerName.hashCode() * 37 + address.hashCode();
    }
}