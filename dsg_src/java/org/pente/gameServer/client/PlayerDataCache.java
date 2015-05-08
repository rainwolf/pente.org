/** PlayerDataCache.java
 *  Copyright (C) 2003 Dweebo's Stone Games (http://pente.org/)
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
package org.pente.gameServer.client;

import java.util.*;

import org.pente.gameServer.core.*;

public class PlayerDataCache {
    
    private Vector players = new Vector();
    private Vector changeListeners = new Vector();
    
    //for now not going to receive updates/deletes made from web server
    private Vector ignored = new Vector();
    
    public synchronized void addPlayer(DSGPlayerData data) {
        if (!players.contains(data)) {
            players.addElement(data);
        }
    }
    public synchronized void removePlayer(DSGPlayerData data) {
        players.removeElement(data);
    }
    public synchronized void removePlayer(String name) {
        for (int i = 0; i < players.size(); i++) {
            DSGPlayerData d = (DSGPlayerData) players.elementAt(i);
            if (d.getName().equals(name)) {
                players.removeElementAt(i);
                return;
            }
        }
    }
    
    public synchronized DSGPlayerData getPlayer(String name) {
        for (int i = 0; i < players.size(); i++) {
            DSGPlayerData d = (DSGPlayerData) players.elementAt(i);
            if (d.getName().equals(name)) {
                return d;
            }
        }
        return null;
    }
    public synchronized Enumeration getAllPlayers() {
        return players.elements();
    }
    
    public synchronized void updatePlayer(DSGPlayerData updateData) {
        for (int i = 0; i < players.size(); i++) {
            DSGPlayerData d = (DSGPlayerData) players.elementAt(i);
            if (d.getName().equals(updateData.getName())) {
                players.removeElementAt(i);
                break;
            }
        }
        players.addElement(updateData);
        
        notifyListeners(updateData);
    }
    
    public synchronized void addChangeListener(PlayerDataChangeListener l) {
        changeListeners.addElement(l);
    }
    public synchronized void removeChangeListener(PlayerDataChangeListener l) {
        changeListeners.removeElement(l);
    }
    private synchronized void notifyListeners(DSGPlayerData updateData) {
        for (int i = 0; i < changeListeners.size(); i++) {
            PlayerDataChangeListener l = (PlayerDataChangeListener)
                changeListeners.elementAt(i);
            l.playerChanged(updateData);
        }
    }
    
    public synchronized void updateIgnore(DSGIgnoreData data[]) {
    	ignored.clear();
    	if (data != null) { // could be null if all are removed from web page
    		for (int i = 0; i < data.length; i++) {
	    		ignored.add(data[i]);
	    	}
    	}
    	//printIgnores();
    }
    public synchronized void addIgnore(DSGIgnoreData d) {

    	ignored.add(d);
    	
    	//printIgnores();
    }
    public synchronized DSGIgnoreData getIgnore(long ignorePid) {

        for (int i = 0; i < ignored.size(); i++) {
        	DSGIgnoreData d2 = (DSGIgnoreData) ignored.elementAt(i);
        	if (d2.getIgnorePid() == ignorePid) {
        		return d2;
        	}
        }
        return null;
    }
//    private void printIgnores() {
//    	System.out.println("ignores:");
//        for (int i = 0; i < ignored.size(); i++) {
//        	DSGIgnoreData d2 = (DSGIgnoreData) ignored.elementAt(i);
//        	System.out.println(d2);
//        }
//    }
    public synchronized void removeIgnore(long ignoredPid) {

        for (int i = 0; i < ignored.size(); i++) {
        	DSGIgnoreData d = (DSGIgnoreData) ignored.elementAt(i);
        	if (d.getIgnorePid() == ignoredPid) {
        		ignored.remove(i);
        		break;
        	}
        }
    	//printIgnores();
    }
    public synchronized boolean isChatIgnored(long ignoredPid) {
        for (int i = 0; i < ignored.size(); i++) {
        	DSGIgnoreData d = (DSGIgnoreData) ignored.elementAt(i);
        	if (d.getIgnorePid() == ignoredPid) {
        		return d.getIgnoreChat();
        	}
        }
        return false;
    }
    public synchronized boolean isInviteIgnored(long ignoredPid) {
        for (int i = 0; i < ignored.size(); i++) {
        	DSGIgnoreData d = (DSGIgnoreData) ignored.elementAt(i);
        	if (d.getIgnorePid() == ignoredPid) {
        		return d.getIgnoreInvite();
        	}
        }
        return false;
    }
}