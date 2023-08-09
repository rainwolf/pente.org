package org.pente.gameServer.core;

import org.apache.log4j.Category;
import org.pente.database.DBHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by waliedothman on 22/01/2017.
 */
public class MySQLDSGFollowerStorer implements DSGFollowerStorer {
    private static final Category log4j = Category.getInstance(
            MySQLDSGFollowerStorer.class.getName());

    private DBHandler dbHandler;

    public MySQLDSGFollowerStorer(DBHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    /**
     * Make sure the database handler is destroyed
     */
    public void destroy() {
        dbHandler.destroy();
    }

    @Override
    public void addFollower(long pid, long followerPid) throws DSGFollowerStoreException {
        Connection con = null;
        PreparedStatement stmt = null;

        try {
            try {
                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                        "insert IGNORE into dsg_followers " +
                                "(pid, follower_pid) VALUES(?,?)");
                stmt.setLong(1, pid);
                stmt.setLong(2, followerPid);
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
            throw new DSGFollowerStoreException("Add follower " + followerPid + " for " + pid + " problem", t);
        }
    }

    @Override
    public void removeFollower(long pid, long followerPid) throws DSGFollowerStoreException {
        Connection con = null;
        PreparedStatement stmt = null;

        try {
            try {
                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                        "delete from dsg_followers " +
                                "where pid = ? and follower_pid = ?");
                stmt.setLong(1, pid);
                stmt.setLong(2, followerPid);
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
            throw new DSGFollowerStoreException("remove follower " + followerPid + " of " + pid + " problem", t);
        }
    }

    @Override
    public List<Long> getFollowers(long pid) throws DSGFollowerStoreException {
        List<Long> followers = new ArrayList<>();
        try {
            Connection con = null;
            PreparedStatement stmt = null;
            ResultSet result = null;

            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement(
                        "select follower_pid " +
                                "from dsg_followers " +
                                "where pid = ?");
                stmt.setLong(1, pid);
                result = stmt.executeQuery();
                while (result.next()) {
                    Long followerPid = result.getLong(1);
                    followers.add(followerPid);
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
            throw new DSGFollowerStoreException("get followers for " + pid + " problem", t);
        }
        return followers;
    }

    @Override
    public List<Long> getFollowing(long pid) throws DSGFollowerStoreException {
        List<Long> following = new ArrayList<>();
        try {
            Connection con = null;
            PreparedStatement stmt = null;
            ResultSet result = null;

            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement(
                        "select pid " +
                                "from dsg_followers " +
                                "where follower_pid = ?");
                stmt.setLong(1, pid);
                result = stmt.executeQuery();
                while (result.next()) {
                    Long followerPid = result.getLong(1);
                    following.add(followerPid);
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
            throw new DSGFollowerStoreException("get following for " + pid + " problem", t);
        }
        return following;
    }

    @Override
    public boolean isFollower(long pid, long followerPid) {
        // not implemented
        return false;
    }

    @Override
    public List<Long> getFriends(long pid) throws DSGFollowerStoreException {
        return null;
    }
}
