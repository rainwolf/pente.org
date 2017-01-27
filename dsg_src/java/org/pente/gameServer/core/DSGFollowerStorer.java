package org.pente.gameServer.core;

import java.util.List;

/**
 * Created by waliedothman on 22/01/2017.
 */
public interface DSGFollowerStorer {
    void addFollower(long pid, long followerPid) throws DSGFollowerStoreException;
    void removeFollower(long pid, long followerPid) throws DSGFollowerStoreException;
    List<Long> getFollowers(long pid) throws DSGFollowerStoreException;
    List<Long> getFollowing(long pid) throws DSGFollowerStoreException;
    boolean isFollower(long pid, long followerPid);
}
