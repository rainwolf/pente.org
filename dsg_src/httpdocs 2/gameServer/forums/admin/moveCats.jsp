
<%
    /**
     *	$RCSfile: moveCats.jsp,v $
     *	$Revision: 1.3 $
     *	$Date: 2002/10/17 20:10:34 $
     */
%>

<%@ page import="java.util.*,
                     com.jivesoftware.forum.*,
                     com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
    errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%	// Permission check
    if (!isSystemAdmin && !isCatAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }

    // Get parameters
    boolean move = request.getParameter("move") != null;
    boolean cancel = request.getParameter("cancel") != null;
    // Current category
    long categoryID = ParamUtils.getLongParameter(request,"cat",-1L);
    // Category we're moving
    long srcCategoryID = ParamUtils.getLongParameter(request,"srcCat",-1L);
    // Category to move to
    long destCategoryID = ParamUtils.getLongParameter(request,"destCat",-1L);

    // Cancel if requested
    if (cancel) {
        response.sendRedirect("forums.jsp");
        return;
    }

    // Load the root category
    ForumCategory rootCategory = forumFactory.getRootForumCategory();

    // The current category:
    ForumCategory currCategory = forumFactory.getForumCategory(categoryID);

    // Check to see we have category admin perms on this category:
    if (!isSystemAdmin && !currCategory.isAuthorized(ForumPermissions.FORUM_CATEGORY_ADMIN)) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }

    // Load the src category
    ForumCategory srcCategory = forumFactory.getForumCategory(srcCategoryID);

    // Load the target category
    ForumCategory destCategory = null;
    if (destCategoryID != -1L) {
        destCategory = forumFactory.getForumCategory(destCategoryID);
    }

    // Do the move if requested
    boolean moveErrors = false;
    boolean moveIntoSameCatError = false;
    boolean noCatSelectedError = false;
    if (move) {
        // Error check
        if (destCategoryID == -1L) {
            noCatSelectedError = true;
        }
        if (currCategory.getID() != destCategory.getID()) {
            moveIntoSameCatError = false;
        }
        moveErrors = moveIntoSameCatError || noCatSelectedError;
        if (!moveErrors) {
            // Do the move, redirect
            currCategory.moveCategory(srcCategory, destCategory);
            response.sendRedirect("forums.jsp?cat="+srcCategory.getID());
            return;
        }
    }

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
    String title = "Move Categories";
    String[][] breadcrumbs = new String[][] {
        {"Main", "main.jsp"},
        {"Forum Summary", "forums.jsp"},
        {title, ""}
    };
%>
<%@ include file="title.jsp" %>

<font size="-1">
Use the form below to move the current category to a different category. Note,
you can not move a category down in the category tree.
</font><p>

<form action="moveCats.jsp">
<input type="hidden" name="cat" value="<%= currCategory.getID() %>">
<input type="hidden" name="srcCat" value="<%= srcCategory.getID() %>">
<input type="hidden" name="move" value="true">

<%  if (move && moveErrors) { %>

    <font size="-1" color="#ff0000">
    Error moving the category. Note, you can not move this category lower
    in the tree structure and you can not move a category into its own category.
    </font><p>

<%  } %>

<font size="-1">
Choose a new category for the category <b><%= srcCategory.getName() %></b>:
</font>
<p>

<ul>

    <table cellpadding="3" cellspacing="1" border="0">
    <tr>
        <td><input type="radio" name="destCat" value="<%= rootCategory.getID() %>"
             <%= ((currCategory.getID() == rootCategory.getID())?" checked":"") %>>
        </td>
        <td>
            <font size="-1">
            <i>Root Category</i>
            </font>
        </td>
    </tr>
<%
    boolean isOption = true;
    int depth = 0;
    for (Iterator iter=rootCategory.getRecursiveCategories(); iter.hasNext(); ) {
        ForumCategory cat = (ForumCategory)iter.next();
        boolean hasCatAdminPerm = isSystemAdmin || cat.isAuthorized(ForumPermissions.FORUM_CATEGORY_ADMIN);
        if (cat.getID() == srcCategory.getID()) {
            depth = cat.getCategoryDepth();
            isOption = false;
        }
        else if (cat.getCategoryDepth() <= depth) {
            isOption = true;
        }
        if (!hasCatAdminPerm) {
            isOption = false;
        }
        boolean isCurrent = (srcCategory.getID() == cat.getID());
%>

    <tr>

        <%  if (isOption) { %>
            <td><input type="radio" name="destCat" value="<%= cat.getID() %>"
                <%= ((currCategory.getID() == cat.getID())?" checked":"") %>>
            </td>
        <%  } else { %>
            <td>&nbsp;</td>
        <%  } %>

        <%  if (isCurrent) { %>
        <td bgcolor="#cccccc">
        <%  } else if (!isOption) { %>
        <td bgcolor="#eeeeee">
        <%  } else { %>
        <td>
        <%  } %>
            <table cellpadding="0" cellspacing="0" border="0">
            <tr>
            <%  for (int i=0; i<cat.getCategoryDepth(); i++) { %>
                <td><img src="images/blank.gif" width="15" height="1" border="0"></td>
            <%  } %>
                <td>
                    <font size="-1">
                <%  if (isCurrent) { %>
                    <b><%= cat.getName() %></b>
                <%  } else { %>
                    <%= cat.getName() %>
                <%  } %>
                    </font>
                </td>
            </tr>
            </table>
        </td>
    </tr>
<%  } // end for %>
    </table>

</ul>
<p>

<center>
<input type="submit" name="move" value="Move Categories">
<input type="submit" name="cancel" value="Cancel">
</center>
</form>

<%@ include file="footer.jsp" %>

<%! private static boolean isChild(ForumCategory currCategory, ForumCategory start)
    {
        boolean isChild = false;
        ForumCategory parentCat = start.getParentCategory();
        while (parentCat.getID() != 1L) {
            if (currCategory.getID() == parentCat.getID()) {
                isChild = true;
                break;
            }
            parentCat = parentCat.getParentCategory();
        }
        return isChild;
    }
%>
