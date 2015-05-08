/** RegisterHandler.java
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

public interface RegisterHandler {

	public static final int SUCCESS = 1;
	public static final int ERROR_INVALID_DATA = 2;
	public static final int ERROR_NAME_TAKEN = 3;
	public static final int ERROR_UNKNOWN = 4;

	public boolean isValidRegistration(
           String name, String password, String email, boolean updatingRegistration);
	public int register(String name, String password, String encryptedPassword,
                        String email, boolean emailVisible,
						boolean emailUpdates, String location, String timezone,
						char sex, int age, String homepage);
}

