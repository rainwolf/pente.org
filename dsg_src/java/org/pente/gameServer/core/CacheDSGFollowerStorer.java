package org.pente.gameServer.core;

import org.apache.log4j.Category;

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

    private Map<Long, List<Long>> followerGraph;
    private Map<Long, List<Long>> followingGraph;

    public CacheDSGFollowerStorer(MySQLDSGFollowerStorer baseStorer) {
        this.baseStorer = baseStorer;
    }
    public Map<Long, List<Long>> getFollowerGraph() { return followerGraph; }
    public Map<Long, List<Long>> getFollowingGraph() { return followingGraph; }



    @Override
    synchronized public void addFollower(long pid, long followerPid) throws DSGFollowerStoreException {
        List<Long> followerList = getFollowers(pid);
        followerList.add(followerPid);

        List<Long> followingList = getFollowing(followerPid);
        followingList.add(pid);

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


}
