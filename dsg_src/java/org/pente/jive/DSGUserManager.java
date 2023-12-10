package org.pente.jive;

import java.sql.*;
import java.util.Iterator;
import java.util.Map;

import com.jivesoftware.base.*;
import com.jivesoftware.base.database.*;
import com.jivesoftware.util.LongList;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.gameServer.core.*;
import org.pente.game.*;

public class DSGUserManager implements UserManager {

    private static final Category log4j =
            Category.getInstance(DSGUserManager.class.getName());

    private DSGPlayerStorer dsgPlayerStorer;

    public DSGUserManager() {

        DBHandler JIVE_DB_HANDLER = new JiveDBHandler();
        try {
            GameVenueStorer gameVenueStorer =
                    new MySQLGameVenueStorer(JIVE_DB_HANDLER);
            dsgPlayerStorer =
                    new MySQLDSGPlayerStorer(JIVE_DB_HANDLER, gameVenueStorer);

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }


    public User createUser(String username, String password, String email)
            throws UserAlreadyExistsException {
        throw new UnsupportedOperationException("Unable to create users in external table");
    }

    public User createUser(String username, String password, String name, String email,
                           boolean nameVisible, boolean emailVisible, Map properties)
            throws UserAlreadyExistsException {
        throw new UnsupportedOperationException("Unable to create users in external table");
    }


    public User getUser(long userId) throws UserNotFoundException {

        log4j.debug("getUser " + userId);
        User user = (User) UserManagerFactory.userCache.get(Long.valueOf(userId));
        if (user == null) {
            log4j.debug("not cached");
            try {
                DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(userId);
                if (dsgPlayerData == null) {
                    throw new UserNotFoundException("User not found");
                }
                user = new DSGUser(dsgPlayerData);

            } catch (DSGPlayerStoreException e) {
                throw new UserNotFoundException("DSGPlayerStoreException", e);
            }

            UserManagerFactory.userCache.put(Long.valueOf(userId), user);
        }

        return user;
    }


    public User getUser(String username) throws UserNotFoundException {
        log4j.debug("getUser " + username);
        if (username == null) {
            throw new UserNotFoundException("Username with null value is not valid.");
        }
        return getUser(getUserID(username));
    }


    public long getUserID(String username) throws UserNotFoundException {

        log4j.debug("getUserID " + username);
        Long userIDLong = (Long) UserManagerFactory.userIDCache.get(username);
        // If ID wan't found in cache, load it up and put it there.
        if (userIDLong == null) {
            log4j.debug("not cached " + username);
            try {
                DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(username);
                if (dsgPlayerData == null) {
                    throw new UserNotFoundException("User not found");
                }
                User user = new DSGUser(dsgPlayerData);
                userIDLong = Long.valueOf(user.getID());
                UserManagerFactory.userCache.put(userIDLong, user);
                UserManagerFactory.userIDCache.put(username, userIDLong);
                log4j.info(user.getName() + ":" + user.getID());
            } catch (DSGPlayerStoreException e) {
                throw new UserNotFoundException("DSGPlayerStoreException", e);
            }
        }

        return userIDLong.longValue();
    }


    // not used
    public void deleteUser(User user) throws UnauthorizedException {
        throw new UnsupportedOperationException("Unable to delete users in external table");
    }


    // copied from DbUserManager
    public int getUserCount() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = ConnectionManager.getConnection();
            pstmt = con.prepareStatement("select count(*) from dsg_player");
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } finally {
            try {
                pstmt.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                con.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return count;
    }

    private static final String ALL_USERS = "select pid from dsg_player";

    public Iterator users() {

        LongList users = new LongList(500);
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = ConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_USERS);
            ResultSet rs = pstmt.executeQuery();
            // Set the fetch size. This will prevent some JDBC drivers from trying
            // to load the entire result set into memory.
            ConnectionManager.setFetchSize(rs, 500);
            while (rs.next()) {
                users.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            Log.error(e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            } catch (Exception e) {
                Log.error(e);
            }
        }
        return new UserIterator(users.toArray());
    }


    private static final String SOME_USERS = "select pid from dsg_player limit ?, ?";

    public Iterator users(int startIndex, int numResults) {

        LongList users = new LongList();
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = ConnectionManager.getConnection();
            pstmt = con.prepareStatement(SOME_USERS);
            pstmt.setInt(1, startIndex);
            pstmt.setInt(2, numResults);
            ResultSet rs = pstmt.executeQuery();
            // Move to start of index
            while (rs.next()) {
                users.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            Log.error(e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            } catch (Exception e) {
                Log.error(e);
            }
        }
        return new UserIterator(users.toArray());
    }

    public void updateUser(DSGPlayerData data) {

        try {
            DSGUser u = (DSGUser) getUser(data.getPlayerID());
            if (u != null) {
                u.updateUser(data);
            }
        } catch (UserNotFoundException unne) {
        }
    }
}
