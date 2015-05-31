<%
/**
 * $RCSfile: thread-buttons.jsp,v $
 * $Revision: 1.6 $
 * $Date: 2002/12/20 21:14:48 $
 */
%>

<%  // This page is meant to be *included statically* in the thread pages.
    //
    // Variables assumed on this page:
    //
    // action - an instance of ForumThreadAction
    // forum - the forum this thread is in
    // thread - the thread we're in
%>

<span class="jive-button">
<table cellpadding="0" cellspacing="0" border="0">
<tr>
    <%  // Only show the reply button if the thread is not locked & not archived
        if (!action.isLocked() && !action.isArchived() && forum.isAuthorized(ForumPermissions.CREATE_MESSAGE)) { %>
        <td>
            <table cellpadding="3" cellspacing="2" border="0">
            <tr>
                <td><a href="post!reply.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>"
                     ><img src="images/reply-16x16.gif" width="16" height="16" border="0"></a></td>
                <td>
                    <%-- Reply to this topic --%>
                    <span class="jive-button-label">
                    <a href="post!reply.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>"
                     ><jive:i18n key="thread.reply_topic" /></a>
                    </span>
                </td>
            </tr>
            </table>
        </td>
    <%  } %>
    <td>
        <table cellpadding="3" cellspacing="2" border="0">
        <tr>
            <td><a href="search!default.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>"
                 ><img src="images/search-16x16.gif" width="16" height="16" border="0"></a></td>
            <td>
                <%-- Search Forum --%>
                <span class="jive-button-label">
                <a href="search!default.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>"
                 ><jive:i18n key="global.search_forum" /></a>
                </span>
            </td>
        </tr>
        </table>
    </td>
    <ww:if test="pageUser">
        <td>
            <table cellpadding="3" cellspacing="2" border="0">
            <tr>
                <td><a href="watches.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>"><img src="images/watch-16x16.gif" width="16" height="16" border="0"></a></td>
                <td>
                    <span class="jive-button-label">
                    <%  if (action.getForumFactory().getWatchManager().isWatched(action.getPageUser(), thread)) { %>

                        <%-- Stop watching topic --%>
                        <a href="watches!remove.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>"
                         ><jive:i18n key="thread.stop_watch" /></a>

                    <%  } else { %>

                        <%-- Watch Topic --%>
                        <a href="watches!add.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>"
                         ><jive:i18n key="global.watch_topic" /></a>

                    <%  } %>
                    </span>
                </td>
            </tr>
            </table>
        </td>
    </ww:if>
</tr>
</table>
</span>