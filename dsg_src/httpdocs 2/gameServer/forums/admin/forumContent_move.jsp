<%
/**
 *	$RCSfile: forumContent_move.jsp,v $
 *	$Revision: 1.3 $
 *	$Date: 2002/10/17 20:10:34 $
 */
%>

<%@ page import="java.util.*,
                 java.text.SimpleDateFormat,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
	errorPage="error.jsp"
%>

<%!	// Global variables, methods, etc
    
    // default range & starting point for the thread iterators
	private final static int RANGE = 15;
	private final static int START = 0;
%>

<%@ include file="global.jsp" %>
 
<%	// Permission check
    if (!isSystemAdmin && !isForumAdmin && !isModerator) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }
    
    // Get parameters
	long forumID = ParamUtils.getLongParameter(request,"forum",-1L);
	long[] threadIDs = ParamUtils.getLongParameters(request,"thread",-1L);
	boolean move = request.getParameter("move") != null;
    boolean cancel = request.getParameter("cancel") != null;
    long destForumID = ParamUtils.getLongParameter(request,"destForum",-1L);
    if (threadIDs == null) {
        threadIDs = new long[0];
    }

    if (cancel) {
        response.sendRedirect("forumContent.jsp?forum=" + forumID);
        return;
    }

    // Load the forum we're working with
    Forum forum = forumFactory.getForum(forumID);

    Forum destForum = null;
    if (destForumID > -1L) {
        destForum = forumFactory.getForum(destForumID);
    }

    boolean errors = false;
    boolean noDest = false;

    // Load a list of threads
    List threads = new LinkedList();
    for (int i=0; i<threadIDs.length; i++) {
        try {
            ForumThread thread = forum.getThread(threadIDs[i]);
            threads.add(thread);
        }
        catch (Exception ignored) {}
    }
    errors = threads.size() == 0;
    noDest = (destForum == null) && move;
    errors = errors || noDest;

    // Cancel if requested
    if (cancel) {
        response.sendRedirect("forumContent.jsp?forum="+forumID);
    }
    
    // Delete a thread if necessary
	if (!errors && move) {
        for (int i=0; i<threads.size(); i++) {
            forum.moveThread((ForumThread)threads.get(i), destForum);
        }
        response.sendRedirect("forumContent.jsp?forum="+forumID);
        return;
	}
%>

<%  // special onload command to load the sidebar
    onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=forum';\"";
%>
<%@ include file="header.jsp" %>

<p>

<%
    String title = "Move Threads";
    String[][] breadcrumbs = new String[][] {
        {"Main", "main.jsp"},
        {"Forums", "forums.jsp"},
        {"Edit Forum", "editForum.jsp?forum="+forumID},
        {"Manage Content", "forumContent.jsp?forum="+forumID},
        {title, ""}
    };
%>

<%@ include file="title.jsp" %>

<%  if (errors) { %>

<font size="-1" color="#ff0000">
<%  if (noDest) { %>
Error: Please choose a destination forum.
<%  } else { %>
Error: No threads to move.
<%  } %>
</font>

<%  } %>

<p>


<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td>
<table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
<tr bgcolor="#eeeeee">
    <td align="center"><font size="-2" face="verdana"><b>SUBJECT</b></font></td>
    <td align="center"><font size="-2" face="verdana"><b>REPLIES</b></font></td>
    <td align="center"><font size="-2" face="verdana"><b>AUTHOR</b></font></td>
    <td align="center"><font size="-2" face="verdana"><b>LAST MODIFIED</b></font></td>
</tr>
<%  for (int i=0; i<threads.size(); i++) {
        ForumThread thread = (ForumThread)threads.get(i);
        User author = thread.getRootMessage().getUser();
%>
<tr bgcolor="#ffffff">
    <td width="97%"><font size="-1"><%= thread.getName() %></font></td>
    <td align="center" width="1%" nowrap><font size="-1"><%= thread.getMessageCount()-1 %></font></td>
    <td align="center" width="1%" nowrap>
    <%  if (author != null) { %>
        <font size="-1"><%= author.getUsername() %></font>
    <%  } else { %>
        <font size="-1"><i>Guest</i></font>
    <%  } %>
    </td>
    <td align="center" width="1%" nowrap><font size="-1">&nbsp;<%= SkinUtils.formatDate(request,pageUser,thread.getModificationDate()) %>&nbsp;</font></td>
</tr>
<%  } %>
</table>
</td></tr>
</table>

<form action="forumContent_move.jsp">
<%  for (int i=0; i<threads.size(); i++) {
        ForumThread thread = (ForumThread)threads.get(i);
%>
<input type="hidden" name="thread" value="<%= thread.getID() %>">
<%  } %>
<input type="hidden" name="forum" value="<%= forumID %>">

<font size="-1">
<b>Move to:</b>
</font>

<ul>
<table cellpadding="3" cellspacing="0" border="0">
<%  ForumCategory rootCat = forumFactory.getRootForumCategory();
    Iterator rootForums = rootCat.getForums();
    int forumSeq = 0;
    int rootDepth = rootCat.getCategoryDepth();
%>
<tr bgcolor="#eeeeee">
    <td><font size="-1">&nbsp;</font></td>
    <td>
        <table cellpadding="0" cellspacing="0" border="0">
        <tr>
            <%  for (int i=0; i<rootDepth-1; i++) { %>
            <td><img src="images/blank.gif" width="20" height="1" border="0"></td>
            <%  } %>
            <td><font size="-1"><b>Root Category</b></font></td>
        </tr>
        </table>
    </td>
</tr>
<%
    if (!rootForums.hasNext()) {
%>
    <tr><td><font size="-1">&nbsp;</font></td>
        <td><font size="-1"><i>No forums</i></font></td>
    </tr>
<%
    }
    while (rootForums.hasNext()) {
        Forum f = (Forum)rootForums.next();
        forumSeq ++;
%>
<tr>
        <td>
            <%  if ((isSystemAdmin
                        || rootCat.isAuthorized(ForumPermissions.FORUM_CATEGORY_ADMIN)
                        || f.isAuthorized(ForumPermissions.FORUM_ADMIN))
                    && f.getID() != forum.getID())
                {
            %>
                <input type="radio" name="destForum" value="<%= f.getID() %>" id="rb<%= forumSeq %>">
            <%  } else { %>
                <font size="-1">&nbsp;</font>
            <%  } %>
        </td>
    <td>
        <table cellpadding="0" cellspacing="0" border="0">
        <tr>
            <%  for (int i=0; i<rootDepth-1; i++) { %>
            <td><img src="images/blank.gif" width="20" height="1" border="0"></td>
            <%  } %>
            <td><font size="-1"><%= f.getName() %></font></td>
        </tr>
        </table>
    </td>
</tr>
<%
    }
    Iterator categories = forumFactory.getRootForumCategory().getRecursiveCategories();
    while (categories.hasNext()) {
        ForumCategory category = (ForumCategory)categories.next();
        int catDepth = category.getCategoryDepth();
        // Show the category:
%>
<tr bgcolor="#eeeeee">
    <td><font size="-1">&nbsp;</font></td>
    <td>
        <table cellpadding="0" cellspacing="0" border="0">
        <tr>
            <%  for (int i=0; i<catDepth-1; i++) { %>
            <td><img src="images/blank.gif" width="20" height="1" border="0"></td>
            <%  } %>
            <td><font size="-1"><b><%= category.getName() %></b></font></td>
        </tr>
        </table>
    </td>
</tr>
<%
        Iterator forums = category.getForums();
        if (!forums.hasNext()) {
%>
    <tr><td><font size="-1">&nbsp;</font></td>
        <td><font size="-1"><i>No forums</i></font></td>
    </tr>
<%
        }
        while (forums.hasNext()) {
            Forum f = (Forum)forums.next();
            forumSeq ++;
%>
    <tr>
        <td>
            <%  if ((isSystemAdmin
                        || category.isAuthorized(ForumPermissions.FORUM_CATEGORY_ADMIN)
                        || f.isAuthorized(ForumPermissions.FORUM_ADMIN))
                    && f.getID() != forum.getID())
                {
            %>
                <input type="radio" name="destForum" value="<%= f.getID() %>" id="rb<%= forumSeq %>">
            <%  } else { %>
                <font size="-1">&nbsp;</font>
            <%  } %>
        </td>
        <td>
            <table cellpadding="0" cellspacing="0" border="0">
            <tr>
                <%  for (int i=0; i<catDepth; i++) { %>
                <td><img src="images/blank.gif" width="20" height="1" border="0"></td>
                <%  } %>
                <td>
                    <label for="rb<%= forumSeq %>"><font size="-1"><%= f.getName() %>
                    <%  if (f.getID() == forum.getID()) { %>
                    <i>(current forum)</i>
                    <%  } %>
                    </font></label>
                </td>
            </tr>
            </table>
        </td>
    </tr>
<%
        }
    }
%>
</table>
</ul>

<center>
<input type="submit" value="Move Thread(s)" name="move">
<input type="submit" value="Cancel" name="cancel">
</center>

</form>

<p>

<%@ include file="footer.jsp" %>
