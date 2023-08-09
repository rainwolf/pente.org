package org.pente.jive;

import com.sun.syndication.feed.synd.*;
import com.sun.syndication.io.SyndFeedOutput;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import javax.servlet.http.HttpServlet;

import com.sun.syndication.io.FeedException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import com.jivesoftware.forum.*;
import com.jivesoftware.base.*;

public class JiveFeedServlet extends HttpServlet {

    private static final String DEFAULT_FEED_TYPE = "default.feed.type";
    private static final String FEED_TYPE = "type";
    private static final String MIME_TYPE = "application/xml; charset=UTF-8";
    private static final String COULD_NOT_GENERATE_FEED_ERROR = "Could not generate feed";

    private String _defaultFeedType;

    public void init() {
        _defaultFeedType = getServletConfig().getInitParameter(DEFAULT_FEED_TYPE);
        _defaultFeedType = (_defaultFeedType != null) ? _defaultFeedType : "rss_2.0";
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        try {

            long forumID = 0;//0=all
            String forumIDStr = req.getParameter("forumID");
            if (forumIDStr != null) {
                try {
                    forumID = Long.parseLong(forumIDStr);
                } catch (NumberFormatException n) {
                }
            }

            SyndFeed feed = getFeed(forumID);

            String feedType = req.getParameter(FEED_TYPE);
            feedType = (feedType != null) ? feedType : _defaultFeedType;
            feed.setFeedType(feedType);

            res.setContentType(MIME_TYPE);
            SyndFeedOutput output = new SyndFeedOutput();
            output.output(feed, res.getWriter());
        } catch (Exception ex) {
            String msg = COULD_NOT_GENERATE_FEED_ERROR;
            log(msg, ex);
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
        }
    }

    protected SyndFeed getFeed(long forumID) throws IOException,
            FeedException, ForumNotFoundException, ForumCategoryNotFoundException,
            UnauthorizedException {

        SyndFeed feed = new SyndFeedImpl();

        feed.setTitle("Pente.org Forum Feed");
        feed.setLink("http://pente.org");
        feed.setDescription("All Forum Activity");

        AuthToken auth = new DSGAuthToken(22000000000002L);
        ForumFactory ff = ForumFactory.getInstance(auth);

        ResultFilter rf = new ResultFilter();
        rf.setSortOrder(ResultFilter.DESCENDING);
        rf.setSortField(JiveConstants.CREATION_DATE);
        rf.setNumResults(25);

        Iterator messages = null;
        if (forumID != 0) {
            Forum f = ff.getForum(forumID);
            messages = f.getMessages(rf);
        } else {
            //need 3.2.0 jar to load messages by forum category
            //ForumCategory fc = ff.getForumCategory(1);
            //messages = ff.get

            // so instead load all forums and top 25 messages in each
            // then sort all messages by date and keep only most recent 25
            List<ForumMessage> m = new ArrayList<ForumMessage>();
            for (Iterator forumIter = ff.getForumCategory(1).getForums(); forumIter.hasNext(); ) {
                Forum f = (Forum) forumIter.next();
                for (Iterator mIter = f.getMessages(rf); mIter.hasNext(); ) {
                    ForumMessage m2 = (ForumMessage) mIter.next();
                    m.add(m2);
                }
            }
            Collections.sort(m, new Comparator<ForumMessage>() {
                public int compare(ForumMessage f1, ForumMessage f2) {
                    return f2.getCreationDate().compareTo(f1.getCreationDate());
                }
            });
            while (m.size() > 25) {
                m.remove(25);
            }
            messages = m.iterator();
        }


        List<SyndEntry> entries = new ArrayList();
        for (Iterator iter = messages; iter.hasNext(); ) {
            ForumMessage fm = (ForumMessage) iter.next();

            SyndEntry entry = new SyndEntryImpl();
            entry.setTitle(fm.getSubject());
            //http://pente.org/gameServer/forums/thread.jspa?forumID=1&threadID=4014&messageID=15442#15442
            entry.setLink("http://pente.org/gameServer/forums/thread.jspa?forumID=" +
                    fm.getForumThread().getForum().getID() + "&threadID=" +
                    fm.getForumThread().getID() + "&messageID=" +
                    fm.getID() + "#" + fm.getID());
            entry.setAuthor(fm.getUser().getName());
            entry.setPublishedDate(fm.getCreationDate());

            SyndContent description = new SyndContentImpl();
            description.setType("text/html");
            description.setValue(fm.getBody());
            entry.setDescription(description);

            entries.add(entry);
        }

        feed.setEntries(entries);

        return feed;
    }
}
