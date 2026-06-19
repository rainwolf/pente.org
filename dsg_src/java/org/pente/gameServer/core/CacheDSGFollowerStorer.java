package org.pente.gameServer.core;

import org.apache.log4j.Category;
import org.pente.gameServer.server.RedisConnectionManager;
import org.pente.notifications.NotificationServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by waliedothman on 22/01/2017.
 */
public class CacheDSGFollowerStorer implements DSGFollowerStorer {
    private static final Category log4j =
            Category.getInstance(CacheDSGFollowerStorer.class.getName());

    MySQLDSGFollowerStorer baseStorer;
    NotificationServer notificationServer;
    DSGPlayerStorer playerStorer;


    public CacheDSGFollowerStorer(MySQLDSGFollowerStorer baseStorer,
                                  NotificationServer notificationServer,
                                  DSGPlayerStorer playerStorer) {
        this.baseStorer = baseStorer;
        this.notificationServer = notificationServer;
        this.playerStorer = playerStorer;
    }

    private RedisConnectionManager redisManager = RedisConnectionManager.getInstance();

    @Override
    synchronized public void addFollower(long pid, long followerPid) throws DSGFollowerStoreException {
        baseStorer.addFollower(pid, followerPid);
        ArrayList<Long> followerList = getFollowers(pid);
        if (!followerList.contains(followerPid)) {
            followerList.add(followerPid);
            redisManager.hput(RedisConnectionManager.PID_TO_FOLLOWERS, pid, followerList);
        }

        ArrayList<Long> followingList = getFollowing(followerPid);
        if (!followingList.contains(pid)) {
            followingList.add(pid);
            redisManager.hput(RedisConnectionManager.PID_TO_FOLLOWING, followerPid, followingList);
        }
    }

    @Override
    synchronized public void removeFollower(long pid, long followerPid) throws DSGFollowerStoreException {
        baseStorer.removeFollower(pid, followerPid);

        ArrayList<Long> followerList = getFollowers(pid);
        followerList.remove(followerPid);
        redisManager.hput(RedisConnectionManager.PID_TO_FOLLOWERS, pid, followerList);

        ArrayList<Long> followingList = getFollowing(followerPid);
        followingList.remove(pid);
        redisManager.hput(RedisConnectionManager.PID_TO_FOLLOWING, followerPid, followingList);
    }

    @Override
    synchronized public ArrayList<Long> getFollowers(long pid) throws DSGFollowerStoreException {
        ArrayList<Long> followerList = redisManager.hget(RedisConnectionManager.PID_TO_FOLLOWERS, pid);
        if (followerList == null) {
            followerList = new ArrayList<>(baseStorer.getFollowers(pid));
            redisManager.hput(RedisConnectionManager.PID_TO_FOLLOWERS, pid, followerList);
        }
        return followerList;
    }

    @Override
    synchronized public ArrayList<Long> getFollowing(long pid) throws DSGFollowerStoreException {
        ArrayList<Long> followingList = redisManager.hget(RedisConnectionManager.PID_TO_FOLLOWING, pid);
        if (followingList == null) {
            followingList = new ArrayList<>(baseStorer.getFollowing(pid));
            redisManager.hput(RedisConnectionManager.PID_TO_FOLLOWING, pid, followingList);
        }
        return followingList;
    }

    @Override
    public boolean isFollower(long pid, long followerPid) {
        try {
            List<Long> followerList = getFollowers(pid);
            return followerList.contains(followerPid);
        } catch (DSGFollowerStoreException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public List<Long> getFriends(long pid) throws DSGFollowerStoreException {
        List<Long> followerList = getFollowers(pid);
        List<Long> followingList = getFollowing(pid);
        List<Long> friends = new ArrayList<>(followerList);
        friends.retainAll(followingList);
        return friends;
    }

    boolean getPref(long pid, String prefName) {
        try {
            for (DSGPlayerPreference pref : playerStorer.loadPlayerPreferences(pid)) {
                if (prefName.equals(pref.getName())) {
                    return (Boolean) pref.getValue();
                }
            }
        } catch (DSGPlayerStoreException e) {
            e.printStackTrace();
        }
        return false;
    }
//    @Override
//    public void notifyFollowers(long pid, String message) {
//        if (getPref(pid, "allow_followers_be_notified")) {
//            try {
//                List<Long> followerList = getFollowers(pid);
//                for (long follower_pid : followerList) {
//                    if (getPref(follower_pid, "allow_notification_online_from_following")) {
//                        notificationServer.
//                    }
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }
}
