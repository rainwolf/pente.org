package org.pente.gameServer.client.awt;

import java.util.*;

import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.*;

public class CustomTableData implements PlayerDataChangeListener {

    private int tableNum;
    private Vector watchers = new Vector();
    private DSGPlayerData sittingPlayers[] = new DSGPlayerData[3];
    private int numPlayers = 0;
    
    private int game = GridStateFactory.PENTE_GAME.getId();
    private boolean rated = true;
    private boolean timed = true;
    private int initialTime = 20;
    private int incrementalTime = 0;
    private int tableType = DSGChangeStateTableEvent.TABLE_TYPE_PUBLIC;


    public void setTableNum(int tableNum) {
        this.tableNum = tableNum;
    }
    public int getTableNum() {
        return tableNum;
    }
    
    public void addPlayer(DSGPlayerData data) {
        watchers.addElement(data);
        numPlayers++;
    }
    public void removePlayer(String playerName) {
        
        for (int i = 0; i < watchers.size(); i++) {
            DSGPlayerData d = (DSGPlayerData) watchers.elementAt(i);
            if (d.getName().equals(playerName)) {
                watchers.removeElementAt(i);
                break;
            }
        }
        numPlayers--;

        for (int i = 1; i < sittingPlayers.length; i++) {
            if (sittingPlayers[i] == null) {
                continue;
            }
            if (playerName.equals(sittingPlayers[i].getName())) {
                sittingPlayers[i] = null;
                break;
            }
        }
    }
    
    public void playerChanged(DSGPlayerData updateData) {
        // don't see any point in updating here since just using the name
    }

    public boolean isEmpty() {
        return numPlayers == 0;
    }
    public int getNumWatching() {
        return watchers.size();
    }
    public Enumeration getWatchingPlayers() {
        return watchers.elements();
    }
    
    public String getPlayerAtSeat(int seat) {
        if (sittingPlayers[seat] == null) {
            return null;
        }
        else {
            return sittingPlayers[seat].getName();
        }
    }
    public void sitPlayer(String playerName, int seat) {
        for (int i = 0; i < watchers.size(); i++) {
            DSGPlayerData d = (DSGPlayerData) watchers.elementAt(i);
            if (d.getName().equals(playerName)) {
                watchers.removeElementAt(i);
                sittingPlayers[seat] = d;
                break;
            }
        }
    }
    public void standPlayer(String playerName) {
        for (int i = 1; i < sittingPlayers.length; i++) {
            if (sittingPlayers[i] == null) {
                continue;
            }
            if (playerName.equals(sittingPlayers[i].getName())) {
                watchers.addElement(sittingPlayers[i]);
                sittingPlayers[i] = null;
                break;
            }
        }
    }

    public void swapPlayers() {
        DSGPlayerData tmp = sittingPlayers[1];
        sittingPlayers[1] = sittingPlayers[2];
        sittingPlayers[2] = tmp;
    }

    public int getGame() {
        return game;
    }

    public int getIncrementalTime() {
        return incrementalTime;
    }

    public int getInitialTime() {
        return initialTime;
    }

    public boolean isRated() {
        return rated;
    }

    public boolean isTimed() {
        return timed;
    }

    public void setGame(int i) {
        game = i;
    }

    public void setIncrementalTime(int i) {
        incrementalTime = i;
    }

    public void setInitialTime(int i) {
        initialTime = i;
    }

    public void setRated(boolean b) {
        rated = b;
    }

    public void setTimed(boolean b) {
        timed = b;
    }
    
    public void setTableType(int type) {
        this.tableType = type;
    }
    public boolean isPublic() {
        return tableType == DSGChangeStateTableEvent.TABLE_TYPE_PUBLIC;
    }
    public boolean isPrivate() {
        return tableType == DSGChangeStateTableEvent.TABLE_TYPE_PRIVATE;
    }
}
