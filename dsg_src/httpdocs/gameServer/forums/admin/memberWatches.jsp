
<%
/**
 *	$RCSfile: memberWatches.jsp,v $
 *	$Revision: 1.4.4.1 $
 *	$Date: 2003/03/26 00:12:26 $
 */
%>

<%@ page import="java.util.*,
                 java.net.URLEncoder,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.forum.WatchManager,
                 com.jivesoftware.util.ParamUtils"%>

<%@ include file="global.jsp" %>

<%!
    // Returns an html padder:
    private String pad(int count) {
        String padding = "&nbsp;&nbsp;";
        StringBuffer buf = new StringBuffer(padding.length()*count);
        for (int i=0; i<count; i++) {
            buf.append(padding);
        }
        return buf.toString();
    }

    //
    private int getWatchCount(ForumFactory forumFactory, ForumCategory category, Forum forum,
                              User user)
            throws UnauthorizedException
    {
        // Get a watch mananger
        WatchManager watchManager = forumFactory.getWatchManager();
        if (forum != null) {
            return watchManager.getWatchCount(user, forum);
        }
        else {
            // Iterator over categories
            int count = 0;
            for (Iterator iter=category.getForums(); iter.hasNext();) {
                Forum f = (Forum)iter.next();
               count += watchManager.getWatchCount(user, f);
            }
            return count;
        }
    }
%>

<%	// Security check
    if (!isSystemAdmin && !isGroupAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }
    
    // get parameters
	long groupID = ParamUtils.getLongParameter(request,"group",-1L);
    long[] userIDs = ParamUtils.getLongParameters(request, "user", -1L);
    String objectID = ParamUtils.getParameter(request,"objectID");
    long catID = -1L;
    long forumID = -1L;
    boolean isCat = false;
    boolean isForum = false;
    if (objectID != null) {
        if (objectID.startsWith("c")) {
            try {
                catID = Long.parseLong(objectID.substring(1,objectID.length()));
                isCat = true;
            } catch (Exception ignored) {}
        }
        else if (objectID.startsWith("f")) {
            try {
                forumID = Long.parseLong(objectID.substring(1,objectID.length()));
                isForum = true;
            } catch (Exception ignored) {}
        }
    }

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
        }
        catch (Exception ignored) {}
    }

    // Load the group
    Group group = groupManager.getGroup(groupID);

    // Total number of members in this group
	int memberCount = group.getMemberCount();

    // An iterator of categories this group has explict read access to:
    Iterator catsWithGroupRead = categoriesWithGroupRead(forumFactory,group);

    // If the category is null, make the category the first one in the iterator:
    if (category == null && catsWithGroupRead.hasNext()) {
        category = (ForumCategory)catsWithGroupRead.next();
        // reset the iterator
        catsWithGroupRead = categoriesWithGroupRead(forumFactory,group);
    }

    // Load the users
    java.util.List users = new java.util.LinkedList();
    for (int i=0; i<userIDs.length; i++) {
        try {
            User user = userManager.getUser(userIDs[i]);
            users.add(user);
        }
        catch (Exception ignored) {}
    }

    // Iterator of members
	Iterator members = group.members();

    // Get a read tracker
    ReadTracker readTracker = forumFactory.getReadTracker();
%>

<%  // special onload command to load the sidebar
    onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=users';\"";
%>
<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Group Member Summary";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Groups Summary", "groups.jsp"},
        {"Member Summary", "groupMembers.jsp?group="+groupID},
        {title, "#"}
    };
%>
<%@ include file="title.jsp" %>

<font size="-1">
Below is a summary of watches for this group. In some cases where "N/A" is listed, that means
the information is not available. For instance, if you're looking at a specific category,
the forum watches column will be "N/A" because there is no forum specified.
</font>
<p></p>

<form action="memberWatches.jsp">
<%  for (int i=0; i<userIDs.length; i++) { %>
    <input type="hidden" name="user" value="<%= userIDs[i] %>">
<%  } %>
<input type="hidden" name="group" value="<%= groupID %>">
<font size="-1">
<%  if (category != null || forum != null) { %>
    <%  if (forum != null) { %>
    Current Forum: <b><%= forum.getName() %></b>
    <%  } else if (category != null) { %>
    Current Category: <b><%= category.getName() %></b>
    <%  } %>
<br>
<%  } %>
Choose a category or forum to view:
</font>
<select size="1" name="objectID"
 onchange="this.form.submit();">
<%  // Loop through all categories this group has read access to:
    int firstCatDepth = -1;
    boolean noCatsToRead = !catsWithGroupRead.hasNext();
    if (noCatsToRead) {
%>
    <option value="*" style="font-style:italic;"> - No Categories -

<%  }
    while (catsWithGroupRead.hasNext()) {
        ForumCategory cat = (ForumCategory)catsWithGroupRead.next();
        // used for indents:
        int catDepth = cat.getCategoryDepth();
        if (firstCatDepth == -1) {
            firstCatDepth = catDepth;
        }
%>
    <option value="c<%= cat.getID() %>" style="font-weight:bold;"
     <%= ((isCat && cat.getID()==category.getID())?" selected":"") %>>
        <%= pad(catDepth-firstCatDepth) %>&#149; <%= cat.getName() %>

    <%  // Print out forums in the category:
        for (Iterator forums=cat.getForums(); forums.hasNext();) {
            Forum f = (Forum)forums.next();
    %>
        <option value="f<%= f.getID() %>"
         <%= ((isForum && f.getID()==forum.getID())?" selected":"") %>>
            <%= pad(catDepth-firstCatDepth) %> &nbsp;&nbsp; <%= f.getName() %>

    <%  } %>

<%  } %>
</select>
</form>

<%  // Print out a message saying there are no categories to read
    if (noCatsToRead) {
%>
    <font size="-1">
    This group (<%= group.getName() %>) does not have read access to any categories
    in the system.
    </font>

<%  }
    else {
%>

    <table bgcolor="#bbbbbb" cellpadding="0" cellspacing="0" border="0" width="100%">
    <tr><td>
        <table bgcolor="#bbbbbb" cellpadding="3" cellspacing="1" border="0" width="100%">
        <tr bgcolor="#eeeeee">
            <td rowspan="2" nowrap width="1%">&nbsp;</td>
            <td rowspan="2"><font size="-2" face="verdana"><b>&nbsp;USERNAME</b></font></td>
            <td rowspan="2"><font size="-2" face="verdana"><b>&nbsp;NAME</b></font></td>
            <td colspan="2" align="center"><font size="-2" face="verdana"><b>UNREAD</b></font></td>
            <td colspan="3" align="center"><font size="-2" face="verdana"><b>WATCHES</b></font></td>
        </tr>
        <tr bgcolor="#eeeeee">
            <td align="center" nowrap width="1%"><font size="-2" face="verdana"><b>&nbsp; TOPICS &nbsp;</b></font></td>
            <td align="center" nowrap width="1%"><font size="-2" face="verdana"><b>&nbsp; MESSAGES &nbsp;</b></font></td>
            <td align="center" nowrap width="1%"><font size="-2" face="verdana"><b>&nbsp; THIS CATEGORY* &nbsp;</b></font></td>
            <td align="center" nowrap width="1%"><font size="-2" face="verdana"><b>&nbsp; THIS FORUM** &nbsp;</b></font></td>
            <td align="center" nowrap width="1%"><font size="-2" face="verdana"><b>&nbsp; TOPICS &nbsp;</b></font></td>
        </tr>
        <%  // Iterate through the users
            for (int i=0; i<users.size(); i++) {
                User user = (User)users.get(i);
                String name = user.getName();
                if (name == null) {
                    name = "<i>Not entered.</i>";
                }
        %>
        <tr bgcolor="#ffffff">
            <td width="1%" nowrap>
                <font size="-1">
                &nbsp;<%= (i+1) %>&nbsp;
                </font>
            </td>
            <td>
                <font size="-1">
                <a href="editUser.jsp?user=<%= user.getID() %>"><%= user.getUsername() %></a>
                </font>
            </td>
            <td>
                <font size="-1">
                <%= name %>
                </font>
            </td>
            <td align="center" width="1%" nowrap>
                <font size="-1">
                <%  if (forum == null) { %>
                    N/A
                <%  } else { %>
                    <a href="unreadTopics.jsp?cat=<%= catID %>&forum=<%= forumID %>&user=<%= user.getID() %>&group=<%= groupID %>"
                     ><%= readTracker.getUnreadThreadCount(user, forum) %></a>
                <%  } %>
                </font>
            </td>
            <td align="center" width="1%" nowrap>
                <font size="-1">
                <%  if (forum == null) { %>
                    N/A
                <%  } else { %>
                    <a href="unreadTopics.jsp?cat=<%= catID %>&forum=<%= forumID %>&user=<%= user.getID() %>&group=<%= groupID %>"
                     ><%= readTracker.getUnreadMessageCount(user, forum) %></a>
                <%  } %>
                </font>
            </td>
            <td align="center" width="1%" nowrap>
                &nbsp;
                <%--
                 // implemented when the cat & forum level watches are done
                <img src="images/watch.gif" width="17" height="17" border="0">
                --%>
            </td>
            <td align="center" width="1%" nowrap>
                &nbsp;
                <%--
                 // implemented when the cat & forum level watches are done
                <img src="images/watch.gif" width="17" height="17" border="0">
                --%>
            </td>
            <td align="center" width="1%" nowrap>
                <font size="-1">
                <%  // get numbers of watches:
                    int watchCount = getWatchCount(forumFactory, category, forum, user);
                %>
                <%  if (watchCount > 0) { %>
                    <a href=""><%= watchCount %></a>
                <%  } else { %>
                    0
                <%  } %>
                </font>
            </td>
        </tr>
        <%  } %>
        </table>
    </td></tr>
    </table>

    <p></p>

    <table cellpadding="3" cellspacing="0" border="0">
    <tr>
        <td><font size="-2" face="verdana">*</font></td>
        <td><font size="-2" face="verdana">
            Denotes the user is watching this category.
            </font>
        </td>
    </tr>
    <tr>
        <td><font size="-2" face="verdana">**</font></td>
        <td><font size="-2" face="verdana">
            Denotes the user is watching this forum.
            </font>
        </td>
    </tr>
    </table>

<%  } %>


</body>
</html>



