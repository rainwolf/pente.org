/** DSGPlayerStorerLoginHandler.java
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

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.*;

public class DSGPlayerStorerLoginHandler implements LoginHandler {

	private DSGPlayerStorer dsgPlayerStorer;

    private static Category log4j = Category.getInstance(DSGPlayerStorerLoginHandler.class.getName());

    private Map<String, Long> bootTimes = new HashMap<String, Long>();
    
	public DSGPlayerStorerLoginHandler(DSGPlayerStorer dsgPlayerStorer) {
		this.dsgPlayerStorer = dsgPlayerStorer;
	}

    public int isValidLogin(String name, String password) {

      	DSGPlayerData dsgPlayerData = null;
      	
      	try {
			dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
      	} catch (DSGPlayerStoreException e) {
      		log4j.error("Error checking login " + name, e);
      	}
        if (bootTimes.get(name) != null &&
            System.currentTimeMillis() < bootTimes.get(name)) {
            log4j.debug("still boted");
            return LoginHandler.BOOT;
        }
        else if (dsgPlayerData == null || !dsgPlayerData.isActive() ||
            !dsgPlayerData.getPassword().equals(password)) {
            log4j.debug("invalid login");
            return LoginHandler.INVALID; 
        }
        else if (dsgPlayerData != null && dsgPlayerData.isOldSpeedAccount()) {
            log4j.debug("speed login");
            return LoginHandler.SPEED;
        }
        else {
            log4j.debug("valid login");
            return LoginHandler.VALID;
        }
    }

	public boolean login(String name, String password) {
		
		if (isValidLogin(name, password) != LoginHandler.VALID) {
			return false;
		}
		
		DSGPlayerData dsgPlayerData = null;

		try {
			dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
			dsgPlayerData.loginSuccessful();
			dsgPlayerStorer.updatePlayer(dsgPlayerData);
			
		} catch (DSGPlayerStoreException e) {
			log4j.error("Error logging in " + name, e);
			return false;
		}
		
		return true;
	}
	
	public void bootPlayer(String name, int minutes) {
    	bootTimes.put(name, System.currentTimeMillis() + 1000 * 60 * minutes);
	}
}

