package org.pente.gameServer.core;

import org.apache.log4j.Category;
import org.pente.notifications.NotificationServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by waliedothman on 22/01/2017.
 */
public class CacheDSGFollowerStorer implements DSGFollowerStorer {
    private static final Category log4j =
            Category.getInstance(CacheDSGFollowerStorer.class.getName());

    MySQLDSGFollowerStorer baseStorer;
    NotificationServer notificationServer;
    DSGPlayerStorer playerStorer;

    private Map<Long, List<Long>> followerGraph;
    private Map<Long, List<Long>> followingGraph;

    public CacheDSGFollowerStorer(MySQLDSGFollowerStorer baseStorer, 
                                  NotificationServer notificationServer,
                                  DSGPlayerStorer playerStorer) {
        this.baseStorer = baseStorer;
        this.notificationServer = notificationServer;
        this.playerStorer = playerStorer;
    }
    public Map<Long, List<Long>> getFollowerGraph() { return followerGraph; }
    public Map<Long, List<Long>> getFollowingGraph() { return followingGraph; }



    @Override
    synchronized public void addFollower(long pid, long followerPid) throws DSGFollowerStoreException {
        List<Long> followerList = getFollowers(pid);
        if (!followerList.contains(followerPid)) {
            followerList.add(followerPid);
        }

        List<Long> followingList = getFollowing(followerPid);
        if (!followingList.contains(followerPid)) {
            followingList.add(pid);
        }
        baseStorer.addFollower(pid, followerPid);
    }

    @Override
    synchronized public void removeFollower(long pid, long followerPid) throws DSGFollowerStoreException {
        List<Long> followerList = getFollowers(pid);
        followerList.remove(followerPid);

        List<Long> followingList = getFollowing(followerPid);
        followingList.remove(pid);

        baseStorer.removeFollower(pid, followerPid);
    }

    @Override
    synchronized public List<Long> getFollowers(long pid) throws DSGFollowerStoreException {
        if (followerGraph == null) {
            followerGraph = new HashMap<>();
        }
        List<Long> followerList = followerGraph.get(pid);
        if (followerList == null) {
            followerList = baseStorer.getFollowers(pid);
            followerGraph.put(pid, followerList);
        }
        return followerList;
    }

    @Override
    synchronized public List<Long> getFollowing(long pid) throws DSGFollowerStoreException {
        if (followingGraph == null) {
            followingGraph = new HashMap<>();
        }
        List<Long> followingList = followingGraph.get(pid);
        if (followingList == null) {
            followingList = baseStorer.getFollowing(pid);
            followingGraph.put(pid, followingList);
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
    @Override
    public void notifyFollowers(long pid, String message) {
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
    }
}
