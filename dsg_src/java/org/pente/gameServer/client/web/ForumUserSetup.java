package org.pente.gameServer.client.web;

import org.apache.log4j.Category;

import com.jivesoftware.base.User;
import com.jivesoftware.base.UserManager;
import com.jivesoftware.forum.Forum;
import com.jivesoftware.forum.ForumFactory;
import com.jivesoftware.forum.Watch;
import com.jivesoftware.forum.WatchManager;

import org.pente.jive.DSGAuthToken;

/** Default Jive forum settings for a newly registered player. */
public final class ForumUserSetup {

    private static final Category log4j =
            Category.getInstance(ForumUserSetup.class.getName());

    private static final DSGAuthToken adminToken =
            new DSGAuthToken(22000000000002L);

    private ForumUserSetup() {
    }

    /** Sets the default forum properties and, when newsWatch, an email watch on the news forum. Never throws. */
    public static void initNewUser(String name, boolean newsWatch) {
        try {
            ForumFactory forumFactory = ForumFactory.getInstance(
                    adminToken);
            UserManager userManager = forumFactory.getUserManager();
            User newUser = userManager.getUser(name);

            // set default properties for forums
            newUser.setProperty("jiveThreadRange", "15");
            newUser.setProperty("jiveTimeZoneID", "America/New_York");
            newUser.setProperty("jiveAutoWatchNewTopics", "true");
            newUser.setProperty("jiveAutoWatchReplies", "true");
            newUser.setProperty("jiveAutoAddEmailWatch", "true");

            // add watch for news forum
            if (newsWatch) {
                WatchManager watchManager = forumFactory.getWatchManager();
                Forum newsForum = forumFactory.getForum(4);
                Watch emailUpdatesWatch = watchManager.createWatch(
                        newUser, newsForum);
                emailUpdatesWatch.setWatchType(Watch.EMAIL_WATCH);
                emailUpdatesWatch.setExpirable(false);
            }

        } catch (Throwable t) {
            log4j.error("Problem adding watch during " +
                    "registration for player " + name, t);
        }
    }
}
