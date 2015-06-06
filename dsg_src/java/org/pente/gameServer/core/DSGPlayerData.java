/** DSGPlayerData.java
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
import java.awt.Color;

public interface DSGPlayerData extends Cloneable {

    public void setPlayerID(long pid);
    public long getPlayerID();

    public void setName(String name);
    public String getName();

	public void setNameColor(Color nameColor);
	public Color getNameColor();
	public void setNameColorRGB(int color);
	public int getNameColorRGB();

	public boolean hasPlayerDonated();
    Public void getSubscriberLevel(int subscriberLevel);
    Public int getSubscriberLevel();

    public void setPassword(String password);
    public String getPassword();

    public void setEmail(String email);
    public String getEmail();

    public void setEmailValid(boolean valid);
    public boolean getEmailValid();

	public void setEmailVisible(boolean visible);
	public boolean getEmailVisible();
	
	public void setLocation(String location);
	public String getLocation();

	public static final char UNKNOWN = 'U';
	public static final char MALE = 'M';
	public static final char FEMALE = 'F';
	public void setSex(char sex);
	public char getSex();
	
	public void setAge(int age);
	public int getAge();
	
	public void setHomepage(String homepage);
	public String getHomepage();

    public void setLogins(int logins);
    public int getLogins();

    public void setLastLoginDate(Date lastLoginDate);
    public Date getLastLoginDate();

    public void loginSuccessful();

    public void setRegisterDate(Date registerDate);
    public Date getRegisterDate();

    public void deRegister(char status);

    public void setDeRegisterDate(Date deregisterDate);
    public Date getDeRegisterDate();

    public void setHashCode(String hashCode);
    public String getHashCode();

    public static final char ACTIVE = 'A';
    public static final char DEACTIVE = 'D';
    public static final char SPEED = 'S';
    public static final char CHEATER = 'C';
    public void setStatus(char status);
    public char getStatus();
    public boolean isActive();
    public boolean isOldSpeedAccount();

    public void setLastUpdateDate(Date lastUpdateDate);
    public Date getLastUpdateDate();

    public static final char HUMAN = 'H';
    public static final char COMPUTER = 'C';
    public void setPlayerType(char type);
    public char getPlayerType();
    public boolean isHuman();
    public boolean isComputer();

    public void setAdmin(boolean admin);
    public boolean isAdmin();
    
    public void setGuest(boolean guest);
    public boolean isGuest();
    
    public void addPlayerGameData(DSGPlayerGameData gameData);
    public void setPlayerGameData(DSGPlayerGameData gameData);
    public DSGPlayerGameData getPlayerGameData(int game);
    public DSGPlayerGameData getPlayerGameData(int game, boolean computer);
    public Vector getAllPlayerGameData();
    public boolean hasPlayerPlayed();
	public int getTotalGames();
	
	public Object clone();
    
    public boolean hasAvatar();
    public byte[] getAvatar();
    public void setAvatar(byte[] avatar);
    public String getAvatarContentType();
    public void setAvatarContentType(String contentType);
    public void setAvatarLastModified(long lastModified);
    public long getAvatarLastModified();
    
    public String getNote();
    public void setNote(String note);

    public int getTourneyWinner();
    
    public void setTimezone(String zone);
    public String getTimezone();
}