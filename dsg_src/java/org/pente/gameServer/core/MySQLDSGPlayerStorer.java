/** MySQLDSGPlayerStorer.java
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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.*;
import java.math.*;
import java.util.Date;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameServer.client.web.StatsData;

public class MySQLDSGPlayerStorer implements DSGPlayerStorer {
    
    private static final Category log4j = Category.getInstance(
        MySQLDSGPlayerStorer.class.getName());

    private static final String PLAYER_TABLE = "player";
    private static final String DSG_PLAYER_TABLE = "dsg_player";
    private static final String DSG_PLAYER_GAME_TABLE = "dsg_player_game";
    private static final String DSG_DONATION_TABLE = "dsg_donation";
    private static final String DSG_PLAYER_AVATAR_TABLE = "dsg_player_avatar";
    private static final String DSG_PLAYER_PREFS_TABLE = "dsg_player_prefs";
    
    public static final int NOADS = 1;
    public static final int UNLIMITEDTBGAMES = (1 << 1);
    public static final int DBACCESS = (1 << 2);
    public static final int UNLIMITEDMOBILETBGAMES = (1 << 8);
    public static final int ONEMONTH = (1 << 16);
    public static final int ONEYEAR = (1 << 17);

    public static final int FLOATINGVACATIONDAYS = 10;

    private static final Vector<String> PLAYER_TABLES = new Vector<String>();
    static {
        PLAYER_TABLES.addElement(PLAYER_TABLE);
        PLAYER_TABLES.addElement(DSG_PLAYER_TABLE);
        PLAYER_TABLES.addElement(DSG_PLAYER_GAME_TABLE);
        PLAYER_TABLES.addElement(DSG_DONATION_TABLE);
    }

    private DBHandler               dbHandler;
    private MySQLPenteGameStorer    playerStorer;
    private GameVenueStorer			gameVenueStorer;

    public MySQLDSGPlayerStorer(DBHandler dbHandler, GameVenueStorer gameVenueStorer) throws Exception {
        this.dbHandler = dbHandler;
        this.gameVenueStorer = gameVenueStorer;
        playerStorer = new MySQLPenteGameStorer(dbHandler, gameVenueStorer);
    }

    /** Make sure the database handler is destroyed
     */
    public void destroy() {
        dbHandler.destroy();
    }


    public void insertPlayer(DSGPlayerData dsgPlayerData) throws DSGPlayerStoreException {

        try {

        	Connection con = null;
        	PreparedStatement stmt = null;

            try {

                con = dbHandler.getConnection();

                // store the player in the player table used by the dsg database
                PlayerData playerData = playerStorer.loadPlayer(con, dsgPlayerData.getName(), DSG2_12GameFormat.SITE_NAME);
                if (playerData == null) {
                	playerData = new DefaultPlayerData();
                	playerData.setUserIDName(dsgPlayerData.getName());
	                playerStorer.storePlayer(con, playerData, DSG2_12GameFormat.SITE_NAME);
                }
                dsgPlayerData.setPlayerID(playerData.getUserID());

                stmt = con.prepareStatement("INSERT INTO " + DSG_PLAYER_TABLE +
                                            " (pid, password, email, email_valid, " +
                                            " email_visible, location, sex, age, homepage, " +
                                            " last_login_date, register_date, status, " +
                                            " hash_code, last_update_date, player_type, " +
                                            " timezone) " +
                                            "VALUES (? , ?, ?, 'Y', ?, ?, ?, ?, ?, " +
                                            "       sysdate(), sysdate(), 'A', " +
                                            "       old_password(CONCAT(pid, password)), sysdate(), ?, ?)");
                stmt.setLong(1, dsgPlayerData.getPlayerID());
                stmt.setString(2, dsgPlayerData.getPassword());
                stmt.setString(3, dsgPlayerData.getEmail());
                stmt.setString(4, dsgPlayerData.getEmailVisible() ? "Y" : "N");
                stmt.setString(5, dsgPlayerData.getLocation());
                stmt.setString(6, new Character(dsgPlayerData.getSex()).toString());
                stmt.setInt(7, dsgPlayerData.getAge());
                stmt.setString(8, dsgPlayerData.getHomepage());
                stmt.setString(9, new Character(dsgPlayerData.getPlayerType()).toString());
                stmt.setString(10, dsgPlayerData.getTimezone());
                stmt.executeUpdate();

            } finally {
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Insert player problem", t);
        }
    }

    public void updatePlayer(DSGPlayerData dsgPlayerData) throws DSGPlayerStoreException {

        try {
        	      	
 	        Connection con = null;
	        PreparedStatement stmt = null;

            try {

                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                    "update " + DSG_PLAYER_TABLE + " " +
                    "set name_color = ?, " +
                    "password = ?, " +
                    "email = ?, " +
                    "email_valid = ?, " +
                    "email_visible = ?, " +
                    "location = ?, " +
                    "sex = ?, " +
                    "age = ?, " +
                    "homepage = ?, " +
                    "num_logins = ?, " +
                    "last_login_date = ?, " +
                    "de_register_date = ?, " +
                    "status = ?, " +
                    // don't ever update hash_code since
                    // players may update password then
                    // use old (now invalid) hash_code
                    "last_update_date = ?, " +
                    "player_type = ?, " +
                    "note = ?, " +
                    "timezone = ? " +
                    "where pid = ?");
                
                int paramNum = 1;
	            stmt.setInt(paramNum++, dsgPlayerData.getNameColorRGB());
                stmt.setString(paramNum++, dsgPlayerData.getPassword());
                stmt.setString(paramNum++, dsgPlayerData.getEmail());
                stmt.setString(paramNum++, dsgPlayerData.getEmailValid() ? "Y" : "N");
                stmt.setString(paramNum++, dsgPlayerData.getEmailVisible() ? "Y" : "N");
                stmt.setString(paramNum++, dsgPlayerData.getLocation());
                stmt.setString(paramNum++, new Character(dsgPlayerData.getSex()).toString());
                stmt.setInt(paramNum++, dsgPlayerData.getAge());
                stmt.setString(paramNum++, dsgPlayerData.getHomepage());
                stmt.setInt(paramNum++, dsgPlayerData.getLogins());
                stmt.setTimestamp(paramNum++, new Timestamp(dsgPlayerData.getLastLoginDate().getTime()));
                if (dsgPlayerData.getDeRegisterDate() != null) {
                	stmt.setTimestamp(paramNum++, new Timestamp(dsgPlayerData.getDeRegisterDate().getTime()));
                }
                else {
                	stmt.setTimestamp(paramNum++, null);
                }
                stmt.setString(paramNum++, new Character(dsgPlayerData.getStatus()).toString());
                stmt.setTimestamp(paramNum++, new Timestamp(dsgPlayerData.getLastUpdateDate().getTime()));
                stmt.setString(paramNum++, new Character(dsgPlayerData.getPlayerType()).toString());
                stmt.setString(paramNum++, dsgPlayerData.getNote());
                stmt.setString(paramNum++, dsgPlayerData.getTimezone());
                stmt.setLong(paramNum++, dsgPlayerData.getPlayerID());
                
                stmt.executeUpdate();

            } finally {
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Update player problem", t);
        }
                
    }

    public DSGPlayerData loadPlayer(long playerID) throws DSGPlayerStoreException {

		DSGPlayerData dsgPlayerData = null;

        try {
        	Connection con = null;
        	PreparedStatement stmt = null;
        	ResultSet result = null;

            try {

                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                    "select player.name, dsg_player.name_color, player.pid, " +
                    "dsg_player.password, dsg_player.email, " +
                    "dsg_player.email_valid, dsg_player.email_visible, " +
                    "dsg_player.location, dsg_player.sex, dsg_player.age, " +
                    "dsg_player.homepage, dsg_player.num_logins, " +
                    "dsg_player.last_login_date, dsg_player.register_date, " +
                    "dsg_player.de_register_date, dsg_player.status, " +
                    "dsg_player.hash_code, dsg_player.last_update_date, " +
                    "dsg_player.player_type, dsg_player.note, dsg_player.admin, " +
                    "dsg_player.timezone " +
                    "from player, dsg_player " +
                    "where player.pid = dsg_player.pid " +
                    "and player.pid = ?");
                stmt.setLong(1, playerID);
                result = stmt.executeQuery();
                if (result.next()) {
					dsgPlayerData = fillDSGPlayerData(result);
                }

            } finally {
            	if (result != null) {
            		result.close();
            	}
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Load player problem", t);
        }
        
        if (dsgPlayerData != null) {
            loadPlayerGames(dsgPlayerData);
            loadAvatar(dsgPlayerData);
            getSubscriberData(dsgPlayerData);
        }

        return dsgPlayerData;
    }


    public DSGPlayerData loadPlayer(String name) throws DSGPlayerStoreException {

		DSGPlayerData dsgPlayerData = null;

        try {
        	Connection con = null;
        	PreparedStatement stmt = null;
        	ResultSet result = null;

            try {

                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                    "select player.name, dsg_player.name_color, player.pid, " +
                    "dsg_player.password, dsg_player.email, " +
                    "dsg_player.email_valid, dsg_player.email_visible, " +
                    "dsg_player.location, dsg_player.sex, dsg_player.age, " +
                    "dsg_player.homepage, dsg_player.num_logins, " +
                    "dsg_player.last_login_date, dsg_player.register_date, " +
                    "dsg_player.de_register_date, dsg_player.status, " +
                    "dsg_player.hash_code, dsg_player.last_update_date, " +
                    "dsg_player.player_type, dsg_player.note, dsg_player.admin, " +
                    "dsg_player.timezone " +
                    "from player, dsg_player " +
                    "where player.pid = dsg_player.pid " +
                    "and player.name = ? " + 
                    "and player.site_id = ?");

                int siteID = gameVenueStorer.getSiteID(DSG2_12GameFormat.SITE_NAME);

                stmt.setString(1, name);
                stmt.setInt(2, siteID);
                
                result = stmt.executeQuery();
                if (result.next()) {
					dsgPlayerData = fillDSGPlayerData(result);
                }

            } finally {
            	if (result != null) {
            		result.close();
            	}
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Load player problem", t);
        }
        
        if (dsgPlayerData != null) {
	        loadPlayerGames(dsgPlayerData);
            loadAvatar(dsgPlayerData);
            getSubscriberData(dsgPlayerData);
        }
       
        return dsgPlayerData;
    }

	private void loadPlayerGames(DSGPlayerData data) throws DSGPlayerStoreException {
		Vector allGames = loadAllGames(data.getPlayerID());
		for (int i = 0; i < allGames.size(); i++) {
			DSGPlayerGameData g = (DSGPlayerGameData) allGames.elementAt(i);
			data.addPlayerGameData(g);
		}
	}

    private void getSubscriberData(DSGPlayerData dsgPlayerData) throws DSGPlayerStoreException {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {
            try {
                con = dbHandler.getConnection();

                Calendar lastMonth = Calendar.getInstance();
                lastMonth.add(java.util.Calendar.DATE, -31);
                Calendar lastYear = Calendar.getInstance();
                lastYear.add(java.util.Calendar.YEAR, -1);

                stmt = con.prepareStatement(
                    "select level, paymentdate " +
                    "from dsg_subscribers " +
                    "where pid = ?");
                stmt.setLong(1, dsgPlayerData.getPlayerID());
                result = stmt.executeQuery();
                int level = 0;
                Calendar expirationDate = null;
                Calendar paymentDate;
                while (result.next()) {
                    paymentDate = Calendar.getInstance();
                    paymentDate.setTime(result.getDate("paymentdate"));
                    int registeredLvl = result.getInt(1);
                    if ((registeredLvl & ONEMONTH) != 0) {
                        if (paymentDate.after(lastMonth)) {
                            level = level | registeredLvl;
                            paymentDate.add(java.util.Calendar.DATE, 31);
                            if (expirationDate == null) {
                                expirationDate = paymentDate;
                            } else {
                                if (paymentDate.after(expirationDate)) {
                                    expirationDate = paymentDate;
                                }
                            }
                        }
                    } else  if ((registeredLvl & ONEYEAR) != 0) {
                        if (paymentDate.after(lastYear)) {
                            level = level | registeredLvl;
                            paymentDate.add(java.util.Calendar.YEAR, 1);
                            if (expirationDate == null) {
                                expirationDate = paymentDate;
                            } else {
                                if (paymentDate.after(expirationDate)) {
                                    expirationDate = paymentDate;
                                }
                            }
                        }
                    }
                }

                dsgPlayerData.setSubscriberLevel(level);
                if (expirationDate != null) {
                    dsgPlayerData.setSubscriptionExpiration(expirationDate.getTime());
                } 

                if ((level & NOADS) == 0) {
                    dsgPlayerData.setShowAds(true);
                } else {
                    dsgPlayerData.setShowAds(false);
                }
                    
                if ((level & UNLIMITEDTBGAMES) == 0) {
                    dsgPlayerData.setUnlimitedTBGames(false);
                } else {
                    dsgPlayerData.setUnlimitedTBGames(true);
                }
                    
                if ((level & UNLIMITEDMOBILETBGAMES) == 0) {
                    dsgPlayerData.setUnlimitedMobileTBGames(false);
                } else {
                    dsgPlayerData.setUnlimitedMobileTBGames(true);
                }

                if (dsgPlayerData.unlimitedTBGames()) {
                    dsgPlayerData.setUnlimitedMobileTBGames(true);
                }
                    
                if ((level & DBACCESS) == 0) {
                    dsgPlayerData.setDatabaseAccess(false);
                } else {
                    dsgPlayerData.setDatabaseAccess(true);
                }

            } finally {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Load subscriber data problem", t);
        }
    }

    public void loadAvatar(DSGPlayerData dsgPlayerData) 
        throws DSGPlayerStoreException {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {
            try {
                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                    "select avatar, content_type, last_update_date " +
                    "from dsg_player_avatar " +
                    "where pid = ?");
                stmt.setLong(1, dsgPlayerData.getPlayerID());
                result = stmt.executeQuery();
                if (result.next()) {
                    dsgPlayerData.setAvatar(result.getBytes(1));
                    dsgPlayerData.setAvatarContentType(result.getString(2));
                    dsgPlayerData.setAvatarLastModified(
                        result.getTimestamp(3).getTime());
                }
                    
            } finally {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Load avatar problem", t);
        }
    }


    public void deleteAvatar(DSGPlayerData dsgPlayerData)
        throws DSGPlayerStoreException {

        Connection con = null;
        PreparedStatement stmt = null;

        try {
            try {
                con = dbHandler.getConnection();
    
                stmt = con.prepareStatement(
                    "delete from dsg_player_avatar " +
                    "where pid = ?");
                stmt.setLong(1, dsgPlayerData.getPlayerID());
                stmt.execute();
                    
            } finally {
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Delete avatar problem", t);
        }
    }
    public void updateAvatar(DSGPlayerData dsgPlayerData) 
        throws DSGPlayerStoreException {

        deleteAvatar(dsgPlayerData);
        if (dsgPlayerData.hasAvatar()) {
            insertAvatar(dsgPlayerData);
        }
    }
    public void insertAvatar(DSGPlayerData dsgPlayerData)
        throws DSGPlayerStoreException {

        Connection con = null;
        PreparedStatement stmt = null;

        try {
            try {
                con = dbHandler.getConnection();
    
                stmt = con.prepareStatement(
                    "insert into dsg_player_avatar " +
                    "(pid, avatar, content_type, last_update_date) " +
                    "values(?, ?, ?, sysdate());");
                stmt.setLong(1, dsgPlayerData.getPlayerID());
                stmt.setBytes(2, dsgPlayerData.getAvatar());
                stmt.setString(3, dsgPlayerData.getAvatarContentType());
                stmt.execute();
                    
            } finally {
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Insert avatar problem", t);
        }
    }

	private DSGPlayerData fillDSGPlayerData(ResultSet result) throws java.sql.SQLException {

		DSGPlayerData dsgPlayerData = new SimpleDSGPlayerData();

		int resultNum = 1;
		dsgPlayerData.setName(result.getString(resultNum++));
		dsgPlayerData.setNameColorRGB(result.getInt(resultNum++));
		dsgPlayerData.setPlayerID(result.getLong(resultNum++));
		dsgPlayerData.setPassword(result.getString(resultNum++));
		dsgPlayerData.setEmail(result.getString(resultNum++));
		dsgPlayerData.setEmailValid(result.getString(resultNum++).equals("Y"));
		dsgPlayerData.setEmailVisible(result.getString(resultNum++).equals("Y"));
		dsgPlayerData.setLocation(result.getString(resultNum++));
		dsgPlayerData.setSex(result.getString(resultNum++).charAt(0));
		dsgPlayerData.setAge(result.getInt(resultNum++));
		dsgPlayerData.setHomepage(result.getString(resultNum++));
		dsgPlayerData.setLogins(result.getInt(resultNum++));
		Timestamp lastLoginDate = result.getTimestamp(resultNum++);
		dsgPlayerData.setLastLoginDate(new java.util.Date(lastLoginDate.getTime()));
		Timestamp registerDate = result.getTimestamp(resultNum++);
		dsgPlayerData.setRegisterDate(new java.util.Date(registerDate.getTime()));
		Timestamp deRegisterDate = result.getTimestamp(resultNum++);
		if (deRegisterDate != null) {
			dsgPlayerData.setDeRegisterDate(new java.util.Date(deRegisterDate.getTime()));
		}
		dsgPlayerData.setStatus(result.getString(resultNum++).charAt(0));
        dsgPlayerData.setHashCode(result.getString(resultNum++));
        Timestamp lastUpdateDate = result.getTimestamp(resultNum++);
        dsgPlayerData.setLastUpdateDate(new java.util.Date(lastUpdateDate.getTime()));
        dsgPlayerData.setPlayerType(result.getString(resultNum++).charAt(0));
        dsgPlayerData.setNote(result.getString(resultNum++));
        dsgPlayerData.setAdmin(result.getString(resultNum++).equals("Y"));
        dsgPlayerData.setTimezone(result.getString(resultNum++));
        
		return dsgPlayerData;
	}


	public void insertDonation(DSGDonationData dsgDonationData, long playerID) throws DSGPlayerStoreException {

        try {

        	Connection con = null;
        	PreparedStatement stmt = null;

            try {

                con = dbHandler.getConnection();


                stmt = con.prepareStatement("insert into " + DSG_DONATION_TABLE +
                                            "(pid, amount, date, email_valid, " +
                                            "values(?, ?, ?)");
                stmt.setLong(1, playerID);
                stmt.setDouble(2, dsgDonationData.getAmount());
                stmt.setTimestamp(3, new Timestamp(dsgDonationData.getDonationDate().getTime()));

                stmt.executeUpdate();

            } finally {
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Insert donation problem", t);
        }
	}
	public Collection getDonations(long playerID) throws DSGPlayerStoreException {
		
		Collection<DSGDonationData> donations = new Vector<DSGDonationData>();

        try {

        	Connection con = null;
        	PreparedStatement stmt = null;
			ResultSet result = null;

            try {

                con = dbHandler.getConnection();

                //MySQLDBHandler.lockTables(PLAYER_TABLES, con);

                stmt = con.prepareStatement("select player.name, " +
                							"dsg_donation.amount, dsg_donation.date " +
                							"from " + DSG_DONATION_TABLE + ", " + PLAYER_TABLE + " " +
                                            "where player.pid = dsg_donation.pid " +
                                            "and player.pid = ?");
                                            
                stmt.setLong(1, playerID);

                result = stmt.executeQuery();
                while (result.next()) {
                	donations.add(fillDSGDonationData(result));
                }
                
            } finally {
            	if (result != null) {
            		result.close();
            	}
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                //    MySQLDBHandler.unLockTables(con);
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("get donation problem", t);
        }

		return donations;
	}
	public List<DSGDonationData> getAllPlayersWhoDonated() throws DSGPlayerStoreException {
		
		List<DSGDonationData> donations = new ArrayList<DSGDonationData>();

        try {

        	Connection con = null;
        	PreparedStatement stmt = null;
			ResultSet result = null;

            try {

                con = dbHandler.getConnection();

                //MySQLDBHandler.lockTables(PLAYER_TABLES, con);

                stmt = con.prepareStatement("select player.pid, player.name, " +
                							"dsg_donation.amount, dsg_donation.date " +
                							"from " + DSG_DONATION_TABLE + ", " + 
                                            PLAYER_TABLE + ", " + 
                                            DSG_PLAYER_TABLE + " " +
                                            "where player.pid = dsg_donation.pid " +
                                            "and player.pid = dsg_player.pid " +
                                            "and dsg_player.status = '" + DSGPlayerData.ACTIVE + "' " +
                                            "order by dsg_donation.date desc");

                result = stmt.executeQuery();
                while (result.next()) {
                	donations.add(fillDSGDonationData(result));
                }

            } finally {
            	if (result != null) {
            		result.close();
            	}
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                //    MySQLDBHandler.unLockTables(con);
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("get all donations problem", t);
        }

		return donations;
	}

   	private DSGDonationData fillDSGDonationData(ResultSet result) throws java.sql.SQLException {
    	DSGDonationData donationData = new SimpleDSGDonationData();

    	donationData.setPid(result.getLong(1));
    	donationData.setName(result.getString(2));
    	donationData.setAmount(result.getDouble(3));
		Timestamp donationDate = result.getTimestamp(4);
    	donationData.setDonationDate(new java.util.Date(donationDate.getTime()));

		return donationData;
	}

    public void insertGame(DSGPlayerGameData dsgPlayerGameData) throws DSGPlayerStoreException {

        try {

        	Connection con = null;
        	PreparedStatement stmt = null;

            try {

                con = dbHandler.getConnection();

                stmt = con.prepareStatement("insert into " + DSG_PLAYER_GAME_TABLE + " " +
                                            "(pid, game, wins, losses, draws, " +
                                            " rating, streak, last_game_date, " +
                                            " computer) " +
                                            "values(?, ?, ?, ?, ?, ?, ?, ?, ?)");
                stmt.setLong(1, dsgPlayerGameData.getPlayerID());
                stmt.setInt(2, dsgPlayerGameData.getGame());
                stmt.setInt(3, dsgPlayerGameData.getWins());
                stmt.setInt(4, dsgPlayerGameData.getLosses());
                stmt.setInt(5, dsgPlayerGameData.getDraws());
                stmt.setDouble(6, dsgPlayerGameData.getRating());
                stmt.setInt(7, dsgPlayerGameData.getStreak());
                stmt.setTimestamp(8, new Timestamp(dsgPlayerGameData.getLastGameDate().getTime()));
                stmt.setString(9, new Character(dsgPlayerGameData.getComputer()).toString());
                stmt.executeUpdate();

            } finally {
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Insert game problem", t);
        }
    }
    public void updateGame(DSGPlayerGameData dsgPlayerGameData) throws DSGPlayerStoreException {

        try {

 	        Connection con = null;
	        PreparedStatement stmt = null;

            try {

                con = dbHandler.getConnection();

                stmt = con.prepareStatement("update " + DSG_PLAYER_GAME_TABLE + " " +
                                            "set wins = ?, " +
                                            "losses = ?, " +
                                            "draws = ?, " +
                                            "rating = ?, " +
                                            "streak = ?, " +
                                            "last_game_date = ? " +
                                            "where pid = ? " +
                                            "and game = ? " +
                                            "and computer = ?");
                stmt.setInt(1, dsgPlayerGameData.getWins());
                stmt.setInt(2, dsgPlayerGameData.getLosses());
                stmt.setInt(3, dsgPlayerGameData.getDraws());
                stmt.setDouble(4, dsgPlayerGameData.getRating());
                stmt.setInt(5, dsgPlayerGameData.getStreak());
                stmt.setTimestamp(6, new Timestamp(dsgPlayerGameData.getLastGameDate().getTime()));
                stmt.setLong(7, dsgPlayerGameData.getPlayerID());
                stmt.setInt(8, dsgPlayerGameData.getGame());
                stmt.setString(9, new Character(dsgPlayerGameData.getComputer()).toString());
                stmt.executeUpdate();

            } finally {
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Update game problem", t);
        }
    }



    public DSGPlayerGameData loadGame(int game, long playerID, boolean computer)
        throws DSGPlayerStoreException {

		DSGPlayerGameData dsgPlayerGameData = null;

        try {
        	Connection con = null;
        	PreparedStatement stmt = null;
        	ResultSet result = null;

            try {

                con = dbHandler.getConnection();

                stmt = con.prepareStatement("select pid, game, wins, losses, draws," +
                                            "rating, streak, last_game_date, " +
                                            "computer, tourney_winner " +
                                            "from " + DSG_PLAYER_GAME_TABLE + " " +
                                            "where pid = ? " +
                                            "and game = ? " +
                                            "and computer = ?");
                stmt.setLong(1, playerID);
                stmt.setInt(2, game);
                stmt.setString(3, "" + (computer ? DSGPlayerGameData.YES : DSGPlayerGameData.NO));

                result = stmt.executeQuery();
                if (result.next()) {
					dsgPlayerGameData = fillDSGPlayerGameData(result);
                }

            } finally {
            	if (result != null) {
            		result.close();
            	}
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Load game problem", t);
        }

        return dsgPlayerGameData;
    }

    public Vector loadAllGames(long playerID) throws DSGPlayerStoreException {

		Vector<DSGPlayerGameData> allGames = new Vector<DSGPlayerGameData>();

        try {
        	Connection con = null;
        	PreparedStatement stmt = null;
        	ResultSet result = null;

            try {

                con = dbHandler.getConnection();

                stmt = con.prepareStatement("select pid, game, wins, losses, draws," +
                                            "rating, streak, last_game_date, " +
                                            "computer, tourney_winner " +
                                            "from " + DSG_PLAYER_GAME_TABLE + " " +
                                            "where pid = ?");
                stmt.setLong(1, playerID);

                result = stmt.executeQuery();
                while (result.next()) {
                	allGames.addElement(fillDSGPlayerGameData(result));
                }

            } finally {
            	if (result != null) {
            		result.close();
            	}
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Load all games problem", t);
        }

        return allGames;
    }
   	private DSGPlayerGameData fillDSGPlayerGameData(ResultSet result) throws java.sql.SQLException {
		DSGPlayerGameData dsgPlayerGameData = new SimpleDSGPlayerGameData();

		dsgPlayerGameData.setPlayerID(result.getLong(1));
		dsgPlayerGameData.setGame(result.getInt(2));
		dsgPlayerGameData.setWins(result.getInt(3));
		dsgPlayerGameData.setLosses(result.getInt(4));
		dsgPlayerGameData.setDraws(result.getInt(5));
		dsgPlayerGameData.setRating(result.getDouble(6));
		dsgPlayerGameData.setStreak(result.getInt(7));
		Timestamp lastGameDate = result.getTimestamp(8);
		dsgPlayerGameData.setLastGameDate(new java.util.Date(lastGameDate.getTime()));
        dsgPlayerGameData.setComputer(result.getString(9).charAt(0));
        dsgPlayerGameData.setTourneyWinner(result.getInt(10));

		return dsgPlayerGameData;
	}

    private static final String dsgPlayerHuman = "dsg_player.player_type = '" + DSGPlayerData.HUMAN + "'";
    private static final String dsgPlayerDataHuman = "dsg_player_game.computer = '" + DSGPlayerGameData.NO + "'";
    private static final String dsgPlayerComputer = "dsg_player.player_type = '" + DSGPlayerData.COMPUTER + "'";


	private static final String sortFields[] = new String[] { "dsg_player_game.wins",
															  "dsg_player_game.losses",
															  "dsg_player_game.rating",
															  "dsg_player_game.streak",
															  "player.name",
															  "(dsg_player_game.wins + dsg_player_game.losses)",
															  "((dsg_player_game.wins + 1) / (dsg_player_game.wins + dsg_player_game.losses + 1))",
															  "dsg_player_game.draws",
															  "dsg_player_game.last_game_date"};

    public Vector search(
        int game, int sortField,
        int startNum, int length,
        boolean showProvisional, boolean showInactive,
        int playerType) throws DSGPlayerStoreException {

    	Vector<DSGPlayerData> searchResults = new Vector<DSGPlayerData>();

		String searchString =
            "select player.name, dsg_player_game.wins, " +
            "dsg_player_game.losses, dsg_player_game.draws, dsg_player_game.rating, " +
            "dsg_player_game.streak, dsg_player.player_type, dsg_player_game.tourney_winner, dsg_player_game.last_game_date, dsg_player.name_color " +
            "from player, dsg_player, dsg_player_game " +
            "where player.pid = dsg_player_game.pid " +
            "and player.pid = dsg_player.pid " +
            "and dsg_player.status = '" + DSGPlayerData.ACTIVE + "' " +
            "and game = ? ";

        if (playerType == StatsData.HUMAN) {
            searchString += "and " + dsgPlayerHuman + " and " + dsgPlayerDataHuman + " ";
        }
        else if (playerType == StatsData.AI) {
            searchString += "and " + dsgPlayerComputer + " ";
        }
        else if (playerType == StatsData.BOTH) {
            searchString += "and ((" + dsgPlayerHuman + " and " + dsgPlayerDataHuman + ") or " +
                            dsgPlayerComputer + ") ";
        }

        if (!showProvisional) {
			searchString +=	  "and (dsg_player_game.wins + dsg_player_game.losses) >= 20 ";
		}

        Calendar inactiveCutoff = null;
        if (!showInactive) {
			inactiveCutoff = Calendar.getInstance();
			inactiveCutoff.add(Calendar.DATE, -7);
			searchString +=	  "and last_game_date > ? ";
		}

		String orderOrder = (sortField == 4) ? "" : "desc";
		searchString +=		  "order by " + sortFields[sortField] + " " + orderOrder + " " +
							  "limit " + startNum + ", " + length;


		try {
			Connection con = null;
			PreparedStatement stmt = null;
			ResultSet result = null;

            try {

                con = dbHandler.getConnection();

                //MySQLDBHandler.lockTables(PLAYER_TABLES, con);

                stmt = con.prepareStatement(searchString);

                stmt.setInt(1, game);
                if (!showInactive) {
                	stmt.setTimestamp(2, new Timestamp(inactiveCutoff.getTime().getTime()));
                }

                result = stmt.executeQuery();
                while (result.next()) {
                	DSGPlayerData dsgPlayerData = new SimpleDSGPlayerData();
                	dsgPlayerData.setName(result.getString(1));

                	DSGPlayerGameData dsgPlayerGameData = new SimpleDSGPlayerGameData();
                	dsgPlayerGameData.setGame(game);
                	dsgPlayerGameData.setWins(result.getInt(2));
                	dsgPlayerGameData.setLosses(result.getInt(3));
                	dsgPlayerGameData.setDraws(result.getInt(4));
                	dsgPlayerGameData.setRating(result.getDouble(5));
                	dsgPlayerGameData.setStreak(result.getInt(6));
                    dsgPlayerData.setPlayerType(result.getString(7).charAt(0));
                	dsgPlayerGameData.setTourneyWinner(result.getInt(8));
                	Timestamp lastGameDate = result.getTimestamp(9);
            		dsgPlayerGameData.setLastGameDate(new java.util.Date(lastGameDate.getTime()));
            		dsgPlayerData.setNameColorRGB(result.getInt(10));
                	dsgPlayerData.addPlayerGameData(dsgPlayerGameData);

                	searchResults.addElement(dsgPlayerData);
                }

            } finally {
            	if (result != null) {
            		result.close();
            	}
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                //    MySQLDBHandler.unLockTables(con);
                    dbHandler.freeConnection(con);
                }
            }

		} catch (Throwable t) {
			throw new DSGPlayerStoreException("Search problem", t);
		}

    	return searchResults;
    }

    public int getNumPlayers(
        int game,
        boolean showProvisional,
        boolean showInactive,
        int playerType)
        throws DSGPlayerStoreException {

		int numPlayers = 0;

		try {
			Connection con = null;
			PreparedStatement stmt = null;
			ResultSet result = null;

			try {

                con = dbHandler.getConnection();

				String searchString = "select count(*) " +
									  "from dsg_player, dsg_player_game " +
									  "where dsg_player_game.game = ? " +
                                      "and dsg_player.pid = dsg_player_game.pid ";

                if (playerType == StatsData.HUMAN) {
                    searchString += "and " + dsgPlayerHuman + " and " + dsgPlayerDataHuman + " ";
                }
                else if (playerType == StatsData.AI) {
                    searchString += "and " + dsgPlayerComputer + " ";
                }
                else if (playerType == StatsData.BOTH) {
                    searchString += "and ((" + dsgPlayerHuman + " and " + dsgPlayerDataHuman + ") or " +
                                    dsgPlayerComputer + ") ";
                }
				if (!showProvisional) {
					searchString += "and (dsg_player_game.wins + dsg_player_game.losses) > 20 ";
				}
				Calendar inactiveCutoff = null;
				if (!showInactive) {
					inactiveCutoff = Calendar.getInstance();
					inactiveCutoff.add(Calendar.MONTH, -1);
					searchString += "and last_game_date > ? ";
				}

				stmt = con.prepareStatement(searchString);
				stmt.setInt(1, game);

				if (!showInactive) {
                	stmt.setTimestamp(2, new Timestamp(inactiveCutoff.getTime().getTime()));
				}

				result = stmt.executeQuery();
				if (result.next()) {
					numPlayers = result.getInt(1);
				}

			} finally {
				if (result != null) {
					result.close();
				}
				if (stmt != null) {
					stmt.close();
				}
				if (con != null) {
                    dbHandler.freeConnection(con);
                }
			}

		} catch (Throwable t) {
			throw new DSGPlayerStoreException("Get num players", t);
		}

		return numPlayers;
    }

    public List<DSGPlayerPreference> loadPlayerPreferences(long playerID)
        throws DSGPlayerStoreException {

        List<DSGPlayerPreference> prefs = new ArrayList<DSGPlayerPreference>(5);

        try {
            Connection con = null;
            PreparedStatement stmt = null;
            ResultSet result = null;

            try {

                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                    "select pref_name, pref_value " +
                    "from " + DSG_PLAYER_PREFS_TABLE + " " +
                    "where pid = ?");
                stmt.setLong(1, playerID);
                result = stmt.executeQuery();
                while (result.next()) {
                    String name = result.getString(1);
                    Blob blob = result.getBlob(2);
                    ObjectInputStream in = new ObjectInputStream(
                        blob.getBinaryStream());
                    Object value = in.readObject();
                    in.close();

                    DSGPlayerPreference p = new DSGPlayerPreference(
                        name, value);
                    prefs.add(p);
                }

            } finally {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Load prefs problem", t);
        }

        return prefs;
    }

    public void storePlayerPreference(long playerID, DSGPlayerPreference pref)
        throws DSGPlayerStoreException {

        try {
            if (isPreferenceStored(playerID, pref.getName())) {
                updatePreference(playerID, pref);
            }
            else {
                insertPreference(playerID, pref);
                updatePreference(playerID, pref);
            }

        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Store prefs problem", t);
        }
    }

    private boolean isPreferenceStored(long playerID, String prefName)
        throws Throwable {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {

            con = dbHandler.getConnection();

            stmt = con.prepareStatement(
                "select 1 " +
                "from " + DSG_PLAYER_PREFS_TABLE + " " +
                "where pid = ? and pref_name = ?");
            stmt.setLong(1, playerID);
            stmt.setString(2, prefName);
            result = stmt.executeQuery();

            return (result.next());

        } finally {
            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }
    private void insertPreference(long playerID, DSGPlayerPreference pref)
        throws Throwable {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {

            con = dbHandler.getConnection();

            stmt = con.prepareStatement(
                "insert into " + DSG_PLAYER_PREFS_TABLE + " " +
                "(pid, pref_name, pref_value, last_update_date) " +
                "values(?, ?, ?, sysdate())");
            stmt.setLong(1, playerID);
            stmt.setString(2, pref.getName());

       // System.out.println("pref name " + pref.getName());

            Blob blob = con.createBlob();
            if (pref.getName().equals("emailDsgMessages")) {
                blob.setBytes(1, new BigInteger("ACED0005737200116A6176612E6C616E672E426F6F6C65616ECD207280D59CFAEE0200015A000576616C7565787001", 16).toByteArray());
            } else if (pref.getName().equals("emailSentDsgMessages")) {
                blob.setBytes(1, new BigInteger("ACED0005737200116A6176612E6C616E672E426F6F6C65616ECD207280D59CFAEE0200015A000576616C7565787000", 16).toByteArray());
            } else if (pref.getName().equals("gameRoomSize")) {
                blob.setBytes(1, new BigInteger("ACED0005740003383030", 16).toByteArray());
            } else if (pref.getName().equals("refresh")) {
                blob.setBytes(1, new BigInteger("ACED0005737200116A6176612E6C616E672E496E746567657212E2A0A4F781873802000149000576616C7565787200106A6176612E6C616E672E4E756D62657286AC951D0B94E08B020000787000000000", 16).toByteArray());
            } else if (pref.getName().equals("gameOptions")) {
                blob.setBytes(1, new BigInteger("ACED00057372002D6F72672E70656E74652E67616D655365727665722E636C69656E742E53696D706C6547616D654F7074696F6E737DE3D36C62824C250200055A000564657074685A000C6472617733445069656365735A0009706C6179536F756E645A000C73686F774C6173744D6F76655B0006636F6C6F72737400025B49787000010101757200025B494DBA602676EAB2A502000078700000001B000000000000000000000007000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000", 16).toByteArray());
            } else if (pref.getName().equals("pentedb")) {
                blob.setBytes(1, new BigInteger("ACED0005737200116A6176612E6C616E672E426F6F6C65616ECD207280D59CFAEE0200015A000576616C7565787001", 16).toByteArray());
            } else if (pref.getName().equals("tableSize")) {
                blob.setBytes(1, new BigInteger("ACED0005737200126A6176612E6177742E44696D656E73696F6E418ED9D7AC5F441402000249000668656967687449000577696474687870000002FC00000508", 16).toByteArray());
            } else if (pref.getName().equals("tb")) {
                blob.setBytes(1, new BigInteger("ACED0005737200116A6176612E6C616E672E426F6F6C65616ECD207280D59CFAEE0200015A000576616C7565787001", 16).toByteArray());
            } else if (pref.getName().equals("playJoinSound")) {
                blob.setBytes(1, new BigInteger("ACED0005737200116A6176612E6C616E672E426F6F6C65616ECD207280D59CFAEE0200015A000576616C7565787001", 16).toByteArray());
            } else if (pref.getName().equals("attach")) {
                blob.setBytes(1, new BigInteger("ACED00057400047472756565", 16).toByteArray());
            } else if (pref.getName().equals("chatTimestamp")) {
                blob.setBytes(1, new BigInteger("ACED0005737200116A6176612E6C616E672E426F6F6C65616ECD207280D59CFAEE0200015A000576616C7565787000", 16).toByteArray());
            } else if (pref.getName().equals("showPlayerJoinExit")) {
                blob.setBytes(1, new BigInteger("ACED0005737200116A6176612E6C616E672E426F6F6C65616ECD207280D59CFAEE0200015A000576616C7565787001", 16).toByteArray());
            } else if (pref.getName().equals("ims")) {
                blob.setBytes(1, new BigInteger("0xACED0005737200116A6176612E6C616E672E426F6F6C65616ECD207280D59CFAEE0200015A000576616C7565787001", 16).toByteArray());
            } else if (pref.getName().equals("weekend")) {
                blob.setBytes(1, new BigInteger("0xACED0005757200025B494DBA602676EAB2A50200007870000000020000000700000001", 16).toByteArray());
            } else if (pref.getName().equals("playInviteSound")) {
                blob.setBytes(1, new BigInteger("0xACED0005737200116A6176612E6C616E672E426F6F6C65616ECD207280D59CFAEE0200015A000576616C7565787001", 16).toByteArray());
            } else if (pref.getName().equals("gameState")) {
                blob.setBytes(1, new BigInteger("ACED0005737200336F72672E70656E74652E67616D655365727665722E6576656E742E4453474368616E676553746174655461626C654576656E74D0CA2B77B03BFB3202000649000467616D65490012696E6372656D656E74616C5365636F6E647349000E696E697469616C4D696E757465735A000572617465644900097461626C65547970655A000574696D6564787200306F72672E70656E74652E67616D655365727665722E6576656E742E41627374726163744453475461626C654576656E74FD9FC39D5F2A30B70200024900057461626C654C0006706C617965727400124C6A6176612F6C616E672F537472696E673B7872002B6F72672E70656E74652E67616D655365727665722E6576656E742E41627374726163744453474576656E74C41AC7DB54D63EBC0200014A000474696D65787000000126D81EF7BA0000000174000664776565626F000000010000000100000014000000000101", 16).toByteArray());
            } else {
                blob.setBytes(1, new BigInteger("", 16).toByteArray());
            }
            stmt.setBlob(3, blob);

            stmt.executeUpdate();

        } finally {
            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }
    private void updatePreference(long playerID, DSGPlayerPreference pref)
        throws Throwable {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {

            con = dbHandler.getConnection();

            stmt = con.prepareStatement(
                "select pid, pref_name, pref_value, last_update_date " +
                "from " + DSG_PLAYER_PREFS_TABLE + " " +
                "where pid = ? and pref_name = ? " +
                "for update",
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_UPDATABLE);
            stmt.setLong(1, playerID);
            stmt.setString(2, pref.getName());

            result = stmt.executeQuery();
            if (result.next()) {
                Blob blob = result.getBlob(3);
                ObjectOutputStream out = new ObjectOutputStream(
                    blob.setBinaryStream(1));
                out.writeObject(pref.getValue());
                out.flush();
                out.close();
                result.updateBlob(3, blob);
                result.updateTimestamp(4, new Timestamp(System.currentTimeMillis()));
                result.updateRow();
            }

        } finally {
            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    public List<java.util.Date> loadVacationDays(long playerID) throws DSGPlayerStoreException {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        List<java.util.Date> days = new ArrayList<java.util.Date>();

        try {
            try {

                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                    "select date " +
                    "from tb_vacation " +
                    "where pid = ?");
                stmt.setLong(1, playerID);
                result = stmt.executeQuery();

                while (result.next()) {
                    days.add(new java.util.Date(result.getDate(1).getTime()));
                }
            }
            finally {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }

        } catch (SQLException sq) {
            throw new DSGPlayerStoreException("Problem getting vacation days for " + playerID, sq);
        }

        return days;
    }

    public void storeVacationDays(long pid, List<java.util.Date> vacationDays) throws DSGPlayerStoreException {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {
            try {

                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                    "delete from tb_vacation where pid = ?");
                stmt.setLong(1, pid);
                stmt.executeUpdate();

                stmt.close();

                stmt = con.prepareStatement(
                    "insert into tb_vacation " +
                    "(pid, date) " +
                    "values(?, ?)");
                stmt.setLong(1, pid);
                for (java.util.Date vc : vacationDays) {
                    stmt.setDate(2, new java.sql.Date(vc.getTime()));
                    stmt.executeUpdate();
                }

            } finally {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (SQLException sq) {
            throw new DSGPlayerStoreException("Problem setting vacation days for " + pid, sq);
        }
    }

    public int loadFloatingVacationDays(long playerID) throws DSGPlayerStoreException {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        int days = 0;

        try {
            try {

                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                    "select daysLeft, lastUpdateYear " +
                    "from tb_vacation_floating " +
                    "where pid = ?");
                stmt.setLong(1, playerID);
                result = stmt.executeQuery();

                if (result.next()) {
                    Calendar now = Calendar.getInstance();
                    int currentYear = now.get(Calendar.YEAR);
                    int lastUpdate = result.getInt(2);
                    if (lastUpdate < currentYear) {
                        days = FLOATINGVACATIONDAYS*24;
                    } else {
                        days = result.getInt(1);
                    }
                } else {
                    days = FLOATINGVACATIONDAYS*24;
                }
            }
            finally {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }

        } catch (SQLException sq) {
            throw new DSGPlayerStoreException("Problem getting floating vacation days for " + playerID, sq);
        }

        return days;
    }

    public void pinchFloatingVacationDays(long pid) throws DSGPlayerStoreException {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {
            try {
                Calendar now = Calendar.getInstance();
                int currentYear = now.get(Calendar.YEAR);
                int days = 0;

                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                    "select daysLeft, lastUpdateYear " +
                    "from tb_vacation_floating " +
                    "where pid = ?");
                stmt.setLong(1, pid);
                result = stmt.executeQuery();

                if (result.next()) {
                    int lastUpdate = result.getInt(2);
                    if (lastUpdate < currentYear) {
                        days = FLOATINGVACATIONDAYS*24;
                    } else {
                        days = result.getInt(1);
                    }
                } else {
                    days = FLOATINGVACATIONDAYS*24;
                }
                stmt.close();

                if (days > 0) {
                    days -= 1;
                }

                stmt = con.prepareStatement(
                    "delete from tb_vacation_floating " +
                    "where pid = ?");
                stmt.setLong(1, pid);
                stmt.executeUpdate();
                stmt.close();

                stmt = con.prepareStatement(
                    "insert into tb_vacation_floating " +
                    "(pid, daysLeft, lastUpdateYear) " +
                    "values(?, ?, ?)");
                stmt.setLong(1, pid);
                stmt.setInt(2, days);
                stmt.setInt(3, currentYear);
                stmt.executeUpdate();

            } finally {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (SQLException sq) {
            throw new DSGPlayerStoreException("Problem setting floating vacation days for " + pid, sq);
        }
    }

    public void addFloatingVacationDays(long playerID, int extraDays) throws DSGPlayerStoreException {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {
            try {
                Calendar now = Calendar.getInstance();
                int currentYear = now.get(Calendar.YEAR);
                int days = 0;

                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                    "select daysLeft, lastUpdateYear " +
                    "from tb_vacation_floating " +
                    "where pid = ?");
                stmt.setLong(1, playerID);
                result = stmt.executeQuery();

                if (result.next()) {
                    int lastUpdate = result.getInt(2);
                    if (lastUpdate < currentYear) {
                        days = FLOATINGVACATIONDAYS*24;
                    } else {
                        days = result.getInt(1);
                    }
                } else {
                    days = FLOATINGVACATIONDAYS*24;
                }
                stmt.close();

                days += (24*extraDays);

                stmt = con.prepareStatement(
                    "delete from tb_vacation_floating " +
                    "where pid = ?");
                stmt.setLong(1, playerID);
                stmt.executeUpdate();
                stmt.close();

                stmt = con.prepareStatement(
                    "insert into tb_vacation_floating " +
                    "(pid, daysLeft, lastUpdateYear) " +
                    "values(?, ?, ?)");
                stmt.setLong(1, playerID);
                stmt.setInt(2, days);
                stmt.setInt(3, currentYear);
                stmt.executeUpdate();

            } finally {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (SQLException sq) {
            throw new DSGPlayerStoreException("Problem setting floating vacation days for " + playerID, sq);
        }
    }

	public void insertIgnore(DSGIgnoreData data) throws DSGPlayerStoreException {

        try {

        	Connection con = null;
        	PreparedStatement stmt = null;
        	ResultSet result = null;
            try {

                con = dbHandler.getConnection();

                stmt = con.prepareStatement("insert into dsg_player_ignore " +
                                            "(pid, ignore_pid, ignore_invite, " +
                                            " ignore_chat, last_update_date) " +
                                            "values(?, ?, ?, ?, sysdate())",
                        					Statement.RETURN_GENERATED_KEYS);
                stmt.setLong(1, data.getPid());
                stmt.setLong(2, data.getIgnorePid());
                stmt.setString(3, data.getIgnoreInvite() ? "Y" : "N");
                stmt.setString(4, data.getIgnoreChat() ? "Y" : "N");

	            stmt.executeUpdate();
				result = stmt.getGeneratedKeys();

	            if (result.next()) {
	                long id = result.getLong(1);
					data.setIgnoreId(id);
	            }

            } finally {
            	if (stmt != null) {
            		stmt.close();
            	}
            	if (result != null) {
            		result.close();
            	}
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Insert ignore problem", t);
        }
	}
	public void updateIgnore(DSGIgnoreData data) throws DSGPlayerStoreException {

        try {

        	Connection con = null;
        	PreparedStatement stmt = null;
            try {

                con = dbHandler.getConnection();

                stmt = con.prepareStatement("update dsg_player_ignore " +
                                            "set ignore_invite = ?, " +
                                            "ignore_chat = ?, " +
                                            "last_update_date = sysdate() " +
                                            "where ignore_id = ?");

                stmt.setString(1, data.getIgnoreInvite() ? "Y" : "N");
                stmt.setString(2, data.getIgnoreChat() ? "Y" : "N");
                stmt.setLong(3, data.getIgnoreId());

	            stmt.executeUpdate();


            } finally {
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Update ignore problem", t);
        }
	}

	public void deleteIgnore(DSGIgnoreData data) throws DSGPlayerStoreException {
        Connection con = null;
        PreparedStatement stmt = null;

        try {
            try {
                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                    "delete from dsg_player_ignore " +
                    "where ignore_id = ?");
                stmt.setLong(1, data.getIgnoreId());
                stmt.execute();

            } finally {
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Delete ignore problem", t);
        }
	}

	public List<DSGIgnoreData> getIgnoreData(long pid) throws DSGPlayerStoreException {
    	Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        List<DSGIgnoreData> data = new ArrayList<DSGIgnoreData>();

        try {
        	try {

	            con = dbHandler.getConnection();

	            stmt = con.prepareStatement(
	                "select ignore_id, ignore_pid, ignore_invite, ignore_chat " +
	                "from dsg_player_ignore " +
	                "where pid = ?");
	            stmt.setLong(1, pid);
	            result = stmt.executeQuery();

	            while (result.next()) {
	            	DSGIgnoreData d = new DSGIgnoreData();
	            	d.setIgnoreId(result.getLong(1));
	            	d.setIgnorePid(result.getLong(2));
	            	d.setIgnoreInvite(result.getString(3).equals("Y"));
	            	d.setIgnoreChat(result.getString(4).equals("Y"));
	            	d.setPid(pid);
	            	data.add(d);
	            }
        	}
        	finally {
	            if (result != null) {
	                result.close();
	            }
	            if (stmt != null) {
	                stmt.close();
	            }
	            if (con != null) {
	                dbHandler.freeConnection(con);
	            }
	        }

        } catch (SQLException sq) {
        	throw new DSGPlayerStoreException("Problem getting ignore data for " + pid, sq);
        }

        return data;
	}
	public DSGIgnoreData getIgnoreData(long pid, long ignorePid) throws DSGPlayerStoreException {
		Connection con = null;
	    PreparedStatement stmt = null;
	    ResultSet result = null;
	    DSGIgnoreData data = null;

	    try {
	    	try {

	            con = dbHandler.getConnection();

	            stmt = con.prepareStatement(
	                "select ignore_id, ignore_pid, ignore_invite, ignore_chat " +
	                "from dsg_player_ignore " +
	                "where pid = ? " +
	                "and ignore_pid = ?");
	            stmt.setLong(1, pid);
	            stmt.setLong(2, ignorePid);
	            result = stmt.executeQuery();

	            if (result.next()) {
	            	data = new DSGIgnoreData();
	            	data.setIgnoreId(result.getLong(1));
	            	data.setIgnorePid(result.getLong(2));
	            	data.setIgnoreInvite(result.getString(3).equals("Y"));
	            	data.setIgnoreChat(result.getString(4).equals("Y"));
	            	data.setPid(pid);
	            }
	    	}
	    	finally {
	            if (result != null) {
	                result.close();
	            }
	            if (stmt != null) {
	                stmt.close();
	            }
	            if (con != null) {
	                dbHandler.freeConnection(con);
	            }
	        }

	    } catch (SQLException sq) {
	    	throw new DSGPlayerStoreException("Problem getting ignore data for " + pid, sq);
	    }

	    return data;
	}


    public void insertLiveSet(LiveSet set) throws DSGPlayerStoreException {
    	try {

        	Connection con = null;
        	PreparedStatement stmt = null;
        	ResultSet result = null;

            try {
                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                	"insert into dsg_live_set " +
                    "(p1_pid, p2_pid, g1_gid, g2_gid, status, creation_date) " +
                    "values(?, ?, 0, 0, 'A', sysdate())",
            		Statement.RETURN_GENERATED_KEYS);
                stmt.setLong(1, set.getP1Pid());
                stmt.setLong(2, set.getP2Pid());

                stmt.executeUpdate();
				result = stmt.getGeneratedKeys();

	            if (result.next()) {
	                long sid = result.getLong(1);
	                set.setSid(sid);
	            }

            } finally {
            	if (result != null) {
            		result.close();
            	}
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Insert set problem", t);
        }
    }
    public void updateLiveSet(LiveSet set) throws DSGPlayerStoreException {
    	try {

        	Connection con = null;
        	PreparedStatement stmt = null;

            try {
                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                	"update dsg_live_set " +
                    "set status = ?, " +
                    "winner = ?, " +
                    "g1_gid = ?, " +
                    "g2_gid = ?, " +
                    "completion_date = ? " +
                    "where sid = ?");
                stmt.setString(1, set.getStatus());
                stmt.setInt(2, set.getWinner());
                stmt.setLong(3, set.getG1Gid());
                stmt.setLong(4, set.getG2Gid());
                if (set.getCompletionDate() == null) {
                	stmt.setNull(5, Types.DATE);
                }
                else {
                	stmt.setTimestamp(5, new java.sql.Timestamp(
                		set.getCompletionDate().getTime()));
                }
                stmt.setLong(6, set.getSid());

                stmt.executeUpdate();

            } finally {
            	if (stmt != null) {
            		stmt.close();
            	}
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new DSGPlayerStoreException("Update set problem", t);
        }
    }

    // load the set data and the games too?
    public LiveSet loadLiveSet(long sid) throws DSGPlayerStoreException {
    	return null;
    }

    public Map<Long, String> getExpiringiOSSubscribers() throws DSGPlayerStoreException {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        Map<Long, String> resultMap = new HashMap<Long, String>();

        Date lastYearMinus5 = new Date();
        long timeMillis = lastYearMinus5.getTime();
        lastYearMinus5.setTime(timeMillis - 1000L*3600*24*368);
        Date lastYearPlus5 = new Date();
        lastYearPlus5.setTime(timeMillis - 1000L*3600*24*363);

        try {
            try {
                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                        "select pid, receipt " +
                                "from dsg_subscribers_ios " +
                                "where payment_date between ? and ?");
                stmt.setTimestamp(1, new Timestamp(lastYearMinus5.getTime()));
                stmt.setTimestamp(2, new Timestamp(lastYearPlus5.getTime()));
                result = stmt.executeQuery();

                while (result.next()) {
                    resultMap.put(new Long(result.getLong(1)), result.getString(2));
                }
            }
            finally {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }

        } catch (SQLException sq) {
            throw new DSGPlayerStoreException("Problem getting getExpiringiOSSubscribers", sq);
        }
        
        return resultMap;
    }
    public boolean hasiOSTransactionId(String transactionId) throws DSGPlayerStoreException {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        
        boolean haveIdOrNot = false;

        try {
            try {
                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                        "select * " +
                                "from dsg_subscribers " +
                                "where transaction_id = ?");
                stmt.setString(1, transactionId);
                result = stmt.executeQuery();

                if (result.next()) {
                    haveIdOrNot = true;
                }
            } finally {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }

        } catch (SQLException sq) {
            throw new DSGPlayerStoreException("Problem hasiOSTransactionId: " + transactionId, sq);
        }

        return haveIdOrNot;
    }
    public void insertiOSTransactionId(long pid, String transactionId, Date startDate) throws DSGPlayerStoreException {
        Connection con = null;
        PreparedStatement stmt = null;

        int subscriptionLvl = 0;
        subscriptionLvl = (subscriptionLvl | ONEYEAR);
        subscriptionLvl = (subscriptionLvl | UNLIMITEDTBGAMES);
        subscriptionLvl = (subscriptionLvl | NOADS);
        subscriptionLvl = (subscriptionLvl | DBACCESS);

        try {
            try {
                con = dbHandler.getConnection();

                stmt = con.prepareStatement("INSERT INTO dsg_subscribers (pid, level, paymentdate, transactionid, amount, verified) " +
                        " VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE paymentdate=VALUES(paymentdate)");
                stmt.setLong(1, pid);
                stmt.setInt(2, subscriptionLvl);
                stmt.setTimestamp(3, new Timestamp(startDate.getTime()));
                stmt.setString(4, transactionId);
                stmt.setDouble(5, 0);
                stmt.setInt(6, 1);
                stmt.executeUpdate();

            } finally {
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }

        } catch (SQLException sq) {
            throw new DSGPlayerStoreException("Problem insertiOSTransactionId pid: " + pid +
                    " transactionID: " + transactionId, sq);
        }
    }
    public void updateiOSPaymentDate(long pid, Date startDate) throws DSGPlayerStoreException {
        Connection con = null;
        PreparedStatement stmt = null;

        try {
            try {
                con = dbHandler.getConnection();

                stmt = con.prepareStatement("UPDATE dsg_subscribers_ios SET paymentdate=? " +
                        " WHERE pid=? ");
                stmt.setTimestamp(1, new Timestamp(startDate.getTime()));
                stmt.setLong(2, pid);
                stmt.executeUpdate();

            } finally {
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }

        } catch (SQLException sq) {
            throw new DSGPlayerStoreException("Problem updateiOSPaymentDate pid: " + pid, sq);
        }
    }
}