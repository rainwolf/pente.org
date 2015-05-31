
<%
    /**
     *	$RCSfile: unreadTopics.jsp,v $
     *	$Revision: 1.3 $
     *	$Date: 2002/11/14 03:05:51 $
     */
%>

<%@ page import="java.util.*,
                     java.net.URLEncoder,
                     com.jivesoftware.forum.*,
                     com.jivesoftware.forum.util.*,
                     com.jivesoftware.forum.WatchManager,
                     com.jivesoftware.util.ParamUtils,
                 java.text.DecimalFormat"%>

<%@ include file="global.jsp" %>

<%	// Security check
    if (!isSystemAdmin && !isGroupAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }

    // get parameters
    long groupID = ParamUtils.getLongParameter(request,"group",-1L);
    long userID = ParamUtils.getLongParameter(request,"user",-1L);
    long catID = ParamUtils.getLongParameter(request,"cat",-1L);
    long forumID = ParamUtils.getLongParameter(request,"forum",-1L);

    // Get user and group managers
    UserManager userManager = forumFactory.getUserManager();
    GroupManager groupManager = forumFactory.getGroupManager();

    // Load the forum or cat we're viewing:
    ForumCategory category = null;
    Forum forum = null;
    if (catID > -1L) {
        try {
            category = forumFactory.getForumCategory(catID);
        }
        catch (Exception ignored) {}
    }
    if (forumID > -1L) {
        try {
            forum = forumFactory.getForum(forumID);
            category = forum.getForumCategory();
        }
        catch (Exception ignored) {}
    }

    // Load the group
    Group group = null;
    try {
        groupManager.getGroup(groupID);
    }
    catch (GroupNotFoundException ignored) {}

    // Load the user
    User user = userManager.getUser(userID);

    // Get a read tracker
    ReadTracker readTracker = forumFactory.getReadTracker();
%>

<%  // special onload command to load the sidebar
        onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=users';\"";
%>
<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
        String title = "Unread Topic Summary";
        String[][] breadcrumbs = {
            {"Main", "main.jsp"},
            {"Groups Summary", "groups.jsp"},
            {"Member Summary", "groupMembers.jsp?group="+groupID},
            {title, "unreadTopics.jsp?cat="+catID+"&forum="+forumID+"&user="+userID+"&group="+groupID}
        };
%>
<%@ include file="title.jsp" %>

<font size="-1">
Category: <b><%= category.getName() %></b>, Forum: <b><%= forum.getName() %></b>
<p>
Below is a summary for user <b><%= user.getUsername() %></b>
<%  if (group != null) { %>
    in group <b><%= group.getName() %></b>.
<%  } %>
<p>
For most threads the percentage of unread messages will be 100%. What that means is the user has not read
any part of the thread. Threads that have a percent of unread messages below 100% usually mean that the user
has read a few of the messages but since their last visit there have been new messages. The next time the user
logs in they will see an icon that indicates there is new content in thread to read.
</font>
<p></p>


<table bgcolor="#cccccc" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td>
    <table bgcolor="#cccccc" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#eeeeee">
        <td>&nbsp;</td>
        <td><font size="-2" face="verdana"><b>TOPIC</b></font></td>
        <td align="center"><font size="-2" face="verdana"><b>AUTHOR</b></font></td>
        <td align="center" nowrap><font size="-2" face="verdana"><b>TOTAL<br>MESSAGES</b></font></td>
        <td align="center" nowrap><font size="-2" face="verdana"><b>UNREAD<br>MESSAGES*</b></font></td>
        <td align="center" nowrap><font size="-2" face="verdana"><b>PERCENT<br>UNREAD**</b></font></td>
        <td align="center" nowrap><font size="-2" face="verdana"><b>LAST POST</b></font></td>
    </tr>
<%  // Get an iterator of unread topics in this forum:
    int count = 0;
    Iterator threads = readTracker.getUnreadThreads(user,forum);
    if (!threads.hasNext()) {
%>
    <tr bgcolor="#ffffff">
        <td colspan="7" align="center">
            <font size="-1">
            <i>The user has no unread threads in this forum.</i>
            </font>
        </td>
    </tr>
<%
    }
    while (threads.hasNext()) {
        ForumThread thread = (ForumThread)threads.next();
        count++;
        User author = thread.getRootMessage().getUser();
        int numMessages = thread.getMessageCount();
        int numUnreadMessages = 0;
        for (Iterator messages=thread.getMessages(); messages.hasNext(); ) {
            ForumMessage message = (ForumMessage)messages.next();
            int readStatus = readTracker.getReadStatus(user, message);
            if (readStatus != ReadTracker.READ) {
                numUnreadMessages++;
            }
        }
        double percent = ((double)numUnreadMessages/(double)numMessages)*100.0;
%>
    <tr bgcolor="#ffffff">
        <td>
            <font size="-1">
            <%= count %>
            </font>
        </td>
        <td>
            <font size="-1">
            <%= thread.getName() %>
            </font>
        </td>
        <td>
            <font size="-1">
            <%  if (author == null) { %>
                <i>Guest</i>
            <%  } else { %>
                <a href="userProfile.jsp?user=<%= author.getID() %>"><%= author.getUsername() %></a>
            <%  } %>
            </font>
        </td>
        <td align="center">
            <font size="-1">
            <%= numMessages %>
            </font>
        </td>
        <td align="center">
            <font size="-1">
            <%= numUnreadMessages %>
            </font>
        </td>
        <td align="center">
            <font size="-1">
            <%= ((int)percent) %>%
            </font>
        </td>
        <td nowrap>
            <font size="-1">
            <%= SkinUtils.formatDate(request,pageUser,thread.getModificationDate()) %>
            </font>
        </td>
    </tr>
<%
    }
%>
    </table>
</td></tr>
</table>

</body>
</html>
