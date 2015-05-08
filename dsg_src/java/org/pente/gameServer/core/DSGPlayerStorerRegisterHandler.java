/** DSGPlayerStorerRegisterHandler.java
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

import org.apache.log4j.*;

public class DSGPlayerStorerRegisterHandler implements RegisterHandler {

	private static final int MAX_EMAIL_LEN = 100;

	private DSGPlayerStorer dsgPlayerStorer;

    private static Category cat = Category.getInstance(DSGPlayerStorerRegisterHandler.class.getName());

	public DSGPlayerStorerRegisterHandler(DSGPlayerStorer dsgPlayerStorer) {
		this.dsgPlayerStorer = dsgPlayerStorer;
	}

	public boolean isValidRegistration(
        String name, String password, String email, boolean updatingRegistration) {

		if (name == null || password == null || email == null) {
			return false;
		}
        // when updating registrations, names can be different lengths
        // this was added because a few donors changed their names to
        // shorter names
		if (!updatingRegistration &&
            (name.length() < 5 || name.length() > 10)) {
			return false;
		}

        for (int i = 0; i < name.length(); i++) {
            if (!Character.isLetterOrDigit(name.charAt(i)) && name.charAt(i) != '_') {
                return false;
            }
        }
        
        if (password.length() < 5 || password.length() > 16) {
            return false;
        }
            
        for (int i = 0; i < password.length(); i++) {
            if (!Character.isLetterOrDigit(password.charAt(i)) && password.charAt(i) != '_') {
				return false;
            }
        }


        if (email.length() > MAX_EMAIL_LEN) {
			return false;
       	}

		int atIndex = email.indexOf('@');
		if (atIndex < 0) {
			return false;
		}

		int dotIndex = email.indexOf('.', atIndex);
		if (dotIndex < 0) {
			return false;
		}

		return true;
	}

	public int register(String name, String password, String encryptedPassword,
        String email, boolean emailVisible, boolean emailUpdates,
        String location, String timezone, char sex, int age, String homepage) {
		
		if (!isValidRegistration(name, password, email, false)) {
			return ERROR_INVALID_DATA;
		}

		try {
			DSGPlayerData existingData = dsgPlayerStorer.loadPlayer(name);
			if (existingData != null) {
				return ERROR_NAME_TAKEN;
			}
			
			DSGPlayerData data = new SimpleDSGPlayerData();
			data.setName(name);
			data.setPassword(encryptedPassword);
			data.setEmail(email);
			data.setEmailVisible(emailVisible);
			data.setLocation(location);
			data.setTimezone(timezone);
			data.setSex(sex);
			data.setAge(age);
			data.setHomepage(homepage);
            data.setPlayerType(DSGPlayerData.HUMAN);
            
			dsgPlayerStorer.insertPlayer(data);

		} catch (DSGPlayerStoreException e) {
			cat.error("Error registering " + name, e);
			return ERROR_UNKNOWN;
		}

		return SUCCESS;
	}
}
