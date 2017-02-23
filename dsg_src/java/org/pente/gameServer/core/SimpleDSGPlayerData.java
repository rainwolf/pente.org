/** SimpleDSGPlayerData.java
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

public class SimpleDSGPlayerData implements DSGPlayerData, java.io.Serializable {

    private long        pid;
    private String      name;
    private String      password;
    private String      email;
    private boolean     emailValid;
    private boolean 	emailVisible;
    private String		location;
    private char		sex;
    private int			age;
    private String		homepage;
    private int         logins;
    private int         subscriberLevel;
    private boolean     showAds;
    private boolean     unlimitedTBGames;
    private boolean     unlimitedMobileTBGames;
    private boolean     databaseAccess;
    private Date        subscriptionExpiration;
    private Date        lastLoginDate;
    private Date        registerDate;
    private Date        deRegisterDate;
    private char        status;
    private String      hashCode;
    private Date        lastUpdateDate;
    private String		timezone;
    private char        type;
    private boolean     admin;
    private boolean 	guest;
    
    // special donor fields
    private Color       nameColor;
    private boolean  hasAvatar; // need a boolean since not sending avatar bytes
                                // over network (can't check avatar != null)
    private transient byte[] avatar;
    private transient String avatarContentType;
    private transient long avatarLastModified;
    private String      note;
    
    private Vector<DSGPlayerGameData>      gameData;

	public SimpleDSGPlayerData() {
		gameData = new Vector<DSGPlayerGameData>();
		sex = UNKNOWN;
		age = 0;
	}

    public void setPlayerID(long pid) {
        this.pid = pid;
    }
    public long getPlayerID() {
        return pid;
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }

	public void setNameColor(Color nameColor) {
		this.nameColor = nameColor;
	}
	public Color getNameColor() {
		return nameColor;
	}
	public void setNameColorRGB(int color) {
		if (color == 0) {
			nameColor = null;
		}
		else {
			nameColor = new Color(color);
		}
	}
	public int getNameColorRGB() {
		if (nameColor == null) {
			return 0;
		}
		else {
			return nameColor.getRGB();
		}
	}

	public boolean hasPlayerDonated() {
        // return nameColor != null;
        return this.subscriberLevel > 0;
	}

    public void setSubscriberLevel(int subscriberLevel) {
        this.subscriberLevel = subscriberLevel;
    }

    public int getSubscriberLevel() {
        return this.subscriberLevel;
    }

    public boolean getShowAds() {
        return this.showAds;
    }

    public boolean getUnlimitedTBGames() {
        return this.unlimitedTBGames;
    }

    public boolean getUnlimitedMobileTBGames() {
        return (this.unlimitedMobileTBGames | this.unlimitedTBGames);
    }

    public boolean getDatabaseAccess() {
        return this.databaseAccess;
    }

    public Date getSubscriptionExpiration() {
        return this.subscriptionExpiration;
    }

    public void setShowAds(boolean showAds) {
        this.showAds = showAds;
    }

    public void setUnlimitedTBGames(boolean unlimitedTBGames) {
        this.unlimitedTBGames = unlimitedTBGames;
    }

    public void setUnlimitedMobileTBGames(boolean unlimitedMobileTBGames) {
        this.unlimitedMobileTBGames = unlimitedMobileTBGames;
    }

    public void setDatabaseAccess(boolean databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    public void setSubscriptionExpiration(Date expirationDate) {
        this.subscriptionExpiration = expirationDate;
    }

    public boolean showAds() {
        return this.showAds;
    }

    public boolean unlimitedTBGames() {
        return this.unlimitedTBGames;
    }

    public boolean unlimitedMobileTBGames() {
        return (this.unlimitedMobileTBGames | this.unlimitedTBGames);
    }

    public boolean databaseAccess() {
        return this.databaseAccess;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    public String getPassword() {
        return password;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    public String getEmail() {
        return email;
    }

    public void setEmailValid(boolean valid) {
        this.emailValid = valid;
    }
    public boolean getEmailValid() {
        return emailValid;
    }

	public void setEmailVisible(boolean visible) {
		this.emailVisible = visible;
	}
	public boolean getEmailVisible() {
		return emailVisible;
	}

	public void setLocation(String location) {
		this.location = location;
	}
	public String getLocation() {
		return location;
	}

	public void setSex(char sex) {
		this.sex = sex;
	}
	public char getSex() {
		return sex;
	}

	public void setAge(int age) {
		if (age < 0) {
			throw new IllegalArgumentException("Can't have a negative age.");
		}
		this.age = age;
	}
	public int getAge() {
		return age;
	}
	
	public void setHomepage(String homepage) {
		this.homepage = homepage;
	}
	public String getHomepage() {
		return homepage;
	}

    public void setLogins(int logins) {
        this.logins = logins;
    }
    public int getLogins() {
        return logins;
    }

    public void setLastLoginDate(Date lastLoginDate) {
        this.lastLoginDate = lastLoginDate;
    }
    public Date getLastLoginDate() {
        return lastLoginDate;
    }

    public void setRegisterDate(Date registerDate) {
        this.registerDate = registerDate;
    }
    public Date getRegisterDate() {
        return registerDate;
    }

    public void loginSuccessful() {
        logins++;
        lastLoginDate = new Date();
    }

    public void deRegister(char s) {
        status = s;
        deRegisterDate = new Date();
        
        // remove avatar on de-registration since can't
        // view it anyways (and can't view profile)
        if (hasAvatar()) {
            setAvatar(null);
        }
    }

    public void setDeRegisterDate(Date deRegisterDate) {
        this.deRegisterDate = deRegisterDate;
    }
    public Date getDeRegisterDate() {
        return deRegisterDate;
    }

    public void setHashCode(String hashCode) {
        this.hashCode = hashCode;
    }
    public String getHashCode() {
        return hashCode;
    }

    public void setStatus(char status) {
        this.status = status;
    }
    public char getStatus() {
        return status;
    }
    public boolean isActive() {
        return status == ACTIVE;
    }
    public boolean isOldSpeedAccount() {
        return status == SPEED;
    }

    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }
    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setPlayerType(char type) {
        this.type = type;
    }
    public char getPlayerType() {
        return type;
    }
    public boolean isHuman() {
        return type == HUMAN;
    }
    public boolean isComputer() {
        return type == COMPUTER;
    }

    public void setPlayerGameData(Vector<DSGPlayerGameData> gameData) {
        this.gameData = gameData;
    }
    public void addPlayerGameData(DSGPlayerGameData dsgPlayerGameData) {
        gameData.addElement(dsgPlayerGameData);
    }
    
    public int getTotalGames() {
    	int count = 0;
    	for (int i = 0; i < gameData.size(); i++) {
            DSGPlayerGameData g = (DSGPlayerGameData) gameData.elementAt(i);
            if (g.isComputerScore() == isComputer()) {
                count += g.getTotalGames();
            }
    	}
    	return count;
    }

    /** Gets the "viewable" stats for a player.
     *  Note that if the player is a human the data is stored
     *  with isHumanScore() == true and if the player is a computer
     *  it is stored with isComputerScore() == true
     */
    public DSGPlayerGameData getPlayerGameData(int game) {
        return getPlayerGameData(game, isComputer());
    }
    public DSGPlayerGameData getPlayerGameData(
        int game, boolean computer) {

        for (int i = 0; i < gameData.size(); i++) {
            DSGPlayerGameData g = (DSGPlayerGameData) gameData.elementAt(i);
            if (g.getGame() == game && 
                g.isComputerScore() == computer) {
                return g;
            }
        }

        // if we are trying to get a computer record and none exists yet
        // then get the "non-computer" record and copy it
        if (computer) {
            DSGPlayerGameData g = (DSGPlayerGameData) 
                getPlayerGameData(game, false).clone();
            g.setComputer(DSGPlayerGameData.YES);

            return g;
        }
        else {
            // if no data exists yet create it
            DSGPlayerGameData g = new SimpleDSGPlayerGameData();
            g.setPlayerID(pid);
            g.setGame(game);
            g.setComputer(computer ? DSGPlayerGameData.YES : DSGPlayerGameData.NO);
            addPlayerGameData(g);
        
            return g;
        }
    }
    public Vector getAllPlayerGameData() {
        return gameData;
    }
    public void setPlayerGameData(DSGPlayerGameData newData) {
        for (int i = 0; i < gameData.size(); i++) {
            DSGPlayerGameData g = (DSGPlayerGameData) gameData.elementAt(i);
			if (g.getGame() == newData.getGame() && 
				g.isComputerScore() == newData.isComputerScore()) {
				gameData.setElementAt(newData, i);
				return;
			}
		}
		// not found, insert
		gameData.addElement(newData);
	}
    
    
    public boolean hasPlayerPlayed() {
        for (int i = 0; i < gameData.size(); i++) {
            DSGPlayerGameData data = (DSGPlayerGameData) gameData.elementAt(i);
            if (data.isComputerScore() == isComputer()) {
                return true;
            }
        }
        return false;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof DSGPlayerData)) return false;
        DSGPlayerData o = (DSGPlayerData) obj;
        return o.getPlayerID() == pid;
    }
    public Object clone() {

        SimpleDSGPlayerData data = null;
        
        try {
            data = (SimpleDSGPlayerData) super.clone();
        
            if (lastLoginDate != null) {
                data.lastLoginDate = new Date(lastLoginDate.getTime());
            }
            if (registerDate != null) {
                data.registerDate = new Date(registerDate.getTime());
            }
            if (deRegisterDate != null) {
                data.deRegisterDate = new Date(deRegisterDate.getTime());
            }
            if (lastUpdateDate != null) {
                data.lastUpdateDate = new Date(lastUpdateDate.getTime());
            }

        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        
        return data;
    }
    
    public boolean hasAvatar() {
        return hasAvatar;
    }
    public byte[] getAvatar() {
        return avatar;
    }
    public void setAvatar(byte[] avatar) {
        this.avatar = avatar;
        hasAvatar = avatar != null;
    }
    public String getAvatarContentType() {
        return avatarContentType;
    }
    public void setAvatarContentType(String contentType) {
        this.avatarContentType = contentType;
    }
    
    public long getAvatarLastModified() {
        return avatarLastModified;
    }
    public void setAvatarLastModified(long mod) {
        this.avatarLastModified = mod;
    }
    
    public String getNote() {
        return note;
    }
    public void setNote(String note) {
        this.note = note;
    }
    
    public boolean isAdmin() {
        return admin;
    }
    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public int getTourneyWinner() {
        int myCrown = DSGPlayerGameData.TOURNEY_WINNER_NONE;
        int myKotHCrown = 0;
        for (int i = 0; i < gameData.size(); i++) {
            DSGPlayerGameData d = (DSGPlayerGameData) gameData.elementAt(i);
            if (d.getTourneyWinner() > DSGPlayerGameData.TOURNEY_WINNER_NONE) {
                if (d.getTourneyWinner() == DSGPlayerGameData.KINGOFTHEHILL_WINNER) {
                    myKotHCrown = myKotHCrown + 1;
                } else if (0 == myCrown) {
                    myCrown = d.getTourneyWinner();
                } else if (d.getTourneyWinner() < myCrown) {
                    myCrown = d.getTourneyWinner();
                }
            } 
        }
        if (myCrown > DSGPlayerGameData.TOURNEY_WINNER_NONE) {
            return myCrown;
        } else if (myKotHCrown > DSGPlayerGameData.TOURNEY_WINNER_NONE) {
            return myKotHCrown+3;
        }
        return DSGPlayerGameData.TOURNEY_WINNER_NONE;
    }

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public boolean isGuest() {
		return guest;
	}

	public void setGuest(boolean guest) {
		this.guest = guest;
	}
}