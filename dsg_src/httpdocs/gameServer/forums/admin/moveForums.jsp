
<%
/**
 *	$RCSfile: moveForums.jsp,v $
 *	$Revision: 1.2 $
 *	$Date: 2002/10/02 01:20:37 $
 */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
				 com.jivesoftware.forum.util.*,
                 com.jivesoftware.forum.database.*,
                 com.jivesoftware.util.ParamUtils"
    errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%	// Permission check
    if (!isSystemAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }
    
    // Get parameters
    boolean move = request.getParameter("move") != null;
    boolean cancel = request.getParameter("cancel") != null;
    long categoryID = ParamUtils.getLongParameter(request,"cat",-1L);
    long toCategoryID = ParamUtils.getLongParameter(request,"tocat",-1L);
    long[] forumIDs = ParamUtils.getLongParameters(request,"forum",-1L);
    if (forumIDs == null) {
        forumIDs = new long[0];
    }
    
    // Cancel if requested
    if (cancel) {
        response.sendRedirect("forums.jsp");
        return;
    }
    
    // Error check for no forum passed in
    boolean sourceForumError = false;
    if (forumIDs.length == 0) {
        sourceForumError = true;
    }
    
    // Load the category
    ForumCategory category = forumFactory.getForumCategory(categoryID);
    
    // Load the forums
    java.util.List forums = new LinkedList();
    for (int i=0; i<forumIDs.length; i++) {
        try {
            forums.add(forumFactory.getForum(forumIDs[i]));
        }
        catch (Exception ignored) {}
    }
    
    // Load the root category
    ForumCategory rootCategory = forumFactory.getRootForumCategory();
    
    // Do the move if requested
    boolean moveErrors = false;
    boolean moveIntoSameCatError = true;
    boolean noCatSelectedError = true;
    if (!sourceForumError && move) {
        // Error check
        if (toCategoryID != -1L) {
            noCatSelectedError = false;
        }
        if (toCategoryID != category.getID()) {
            moveIntoSameCatError = false;
        }
        moveErrors = moveIntoSameCatError || noCatSelectedError;
        if (!moveErrors) {
            // Do the move, redirect
            ForumCategory destCategory = forumFactory.getForumCategory(toCategoryID);
            for (int i=0; i<forums.size(); i++) {
                Forum forum = (Forum)forums.get(i);
                try {
                    category.moveForum(forum, destCategory);
                }
                catch (Exception ignored) {}
            }
            response.sendRedirect("forums.jsp?cat="+categoryID);
            return;
        }
    }

    // Indicate if the short term query cache is on:
    DatabaseCacheManager cacheManager = DbForumFactory.getInstance().cacheManager;
    boolean stqcEnabled = cacheManager.isShortTermQueryCacheEnabled();

    // Remove the forum in the session (if we come to this page, the sidebar
    // shouldn't show the specific forum options).
    session.removeAttribute("admin.sidebar.forums.currentForumID");
%>

<%  // special onload command to load the sidebar
    onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=forum';\"";
%>
<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Move Forum";
    String[][] breadcrumbs = new String[][] {
        {"Main", "main.jsp"},
        {"Forum Summary", "forums.jsp"},
        {"Move Forum", ""}
    };
%>
<%@ include file="title.jsp" %>

<font size="-1">
Use the form below to move the forums you selected to another category. Note,
this does not change the content but just associates the forum to a different category.
</font><p>

<%  if (stqcEnabled) { %>
    <font size="-1">
    <i>Note, because you have the short-term query cache enabled you will not see the
    forum move reflected for at least <%= (cacheManager.shortTermQueryCache.getMaxLifetime()/1000L) %>
    seconds.</i>
    </font><p>
<%  } %>

<%  if (sourceForumError) { %>

    <font size="-1" color="#ff0000">
    Error: No forum was selected to be moved. Please
    <a href="javascript:history.go(-1);">go back</a> and select a forum to move.
    </font>

<%  } else { // end if sourceForumError %>

<form action="moveForums.jsp">
<%  for (int i=0; i<forums.size(); i++) {
        Forum forum = (Forum)forums.get(i);
%>
<input type="hidden" name="forum" value="<%= forum.getID() %>">
<%  } %>
<input type="hidden" name="cat" value="<%= category.getID() %>">

<%  if (move && moveErrors) { %>
    
    <font size="-1" color="#ff0000">
    <%  if (moveIntoSameCatError) { %>
    Error: You can't move this forum into the same category. Please choose a
    different category.
    <%  } else if (noCatSelectedError) { %>
    Error: No target category was selected. Please select a category to move
    this forum into.
    <%  } %>
    <p/>
    </font>
    
<%  } %>

<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="">
<tr><td>
<table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
<tr bgcolor="#ffffff">
    <td valign="top"><font size="-1">
        Move the forum(s):
        </font>
    </td>
    <td><font size="-1">
        <%  for (int i=0; i<forums.size(); i++) {
                Forum forum = (Forum)forums.get(i);
        %>
            <b><%= forum.getName() %></b><br/>
        <%  } %>
        </font>
    </td>
</tr>
<tr bgcolor="#ffffff">
    <td><font size="-1">
        From the category:
        </font>
    </td>
    <td><font size="-1">
        <%  if (category.getID() == 1L) { %>
        <b>Root Category</b>
        <%  } else { %>
        <b><%= category.getName() %></b>
        <%  } %>
        </font>
    </td>
</tr>
<tr bgcolor="#ffffff">
    <td><font size="-1">
        To the category:
        </font>
    </td>
    <td><select size="1" name="tocat">
        <option value="-1">
        <%  if (category.getID() == 1L) { %>
        <option value="1">Root Category (Current)
        <%  } else { %>
        <option value="1">Root Category
        <%  } %>
        <%  for (Iterator iter=rootCategory.getRecursiveCategories(); iter.hasNext();) {
                ForumCategory cat = (ForumCategory)iter.next();
                int depth = cat.getCategoryDepth();
        %>
        <option value="<%= cat.getID() %>">
            <%  for (int i=0; i<depth; i++) { %>
                &nbsp;&nbsp;&nbsp;
            <%  } %>
            &#149; <%= cat.getName() %>
            <%  if (cat.getID() == category.getID()) { %>
            (Current)
            <%  } %>
        <%  } %>
        </select>
    </td>
</tr>
</table>
</td></tr>
</table>

<p/>

<center>
<input type="submit" name="move" value="Move Forum(s)">
<input type="submit" name="cancel" value="Cancel">
</center>
</form>

<%  } // end else to if sourceForumError %>

<%@ include file="footer.jsp" %>
