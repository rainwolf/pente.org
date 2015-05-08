
<%
/**
 *	$RCSfile: forums.jsp,v $
 *	$Revision: 1.5 $
 *	$Date: 2002/11/22 22:35:08 $
 */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
				 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
    errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%  // Permission check
    if (!isSystemAdmin && !isCatAdmin && !isForumAdmin && !isModerator) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }

    // Get parameters
    long categoryID = ParamUtils.getLongParameter(request,"cat",-1L);
    long subCategoryID = ParamUtils.getLongParameter(request,"subcat",-1L);
	int start = ParamUtils.getIntParameter(request,"start",0);
	int range = ParamUtils.getIntParameter(request,"range",10);
    long[] forumIDs = ParamUtils.getLongParameters(request,"forum",-1L);
    long forumID = ParamUtils.getLongParameter(request,"forum",-1L);
    long srcCat = ParamUtils.getLongParameter(request,"srcCat",-1L);
    boolean newforum = request.getParameter("newforum") != null;
    boolean newcat = request.getParameter("newcat") != null;
    boolean moveForum = request.getParameter("moveForum") != null;
    boolean moveCat = request.getParameter("moveCat") != null;
    boolean up = ParamUtils.getBooleanParameter(request,"up");
    boolean down = ParamUtils.getBooleanParameter(request,"down");
    int curForumIndex = ParamUtils.getIntParameter(request,"fIndex",-1);
    int curCatIndex = ParamUtils.getIntParameter(request,"cIndex",-1);

    // Redirect if necessary
    if (newforum) {
        response.sendRedirect("createForum.jsp?cat=" + categoryID);
        return;
    }
    if (newcat) {
        response.sendRedirect("createCat.jsp?cat=" + categoryID);
        return;
    }
    if (moveForum) {
        StringBuffer buf = new StringBuffer("moveForums.jsp?cat=" + categoryID);
        if (forumIDs != null) {
            for (int i=0; i<forumIDs.length; i++) {
                buf.append("&forum=").append(forumIDs[i]);
            }
        }
        response.sendRedirect(buf.toString());
        return;
    }
    if (moveCat) {
        response.sendRedirect("moveCats.jsp?cat=" + categoryID
                + "&srcCat=" + srcCat);
        return;
    }

    // If the start isn't passed in as a parameter, look for it in the session.
    if (request.getParameter("start") == null) {
        try {
            start = Integer.parseInt((String)session.getAttribute("admin.forums.start"));
        } catch (Exception e) {}
    }
    else {
        session.setAttribute("admin.forums.start",start+"");
    }
    // Do the same for the category ID -- look for it in the session if it's not
    // passed in as a param.
    if (request.getParameter("cat") == null) {
        try {
            categoryID = Long.parseLong((String)session.getAttribute("admin.forums.cat"));
        } catch (Exception e) {}
    }
    else {
        session.setAttribute("admin.forums.cat",categoryID+"");
    }

    // Get the root category
    ForumCategory category = null;
    if (categoryID == -1L) {
        category = forumFactory.getRootForumCategory();
    }
    else {
        category = forumFactory.getForumCategory(categoryID);
    }

    // Change the order of the forums if requested
    if (up || down) {
        if (curForumIndex != -1) {
            Forum forum = forumFactory.getForum(forumID);
            if (up) {
                category.setForumIndex(forum, (curForumIndex-1));
            } else {
                category.setForumIndex(forum, (curForumIndex+1));
            }
            response.sendRedirect("forums.jsp?cat=" + categoryID);
            return;
        }
        else if (curCatIndex != -1) {
            ForumCategory subCategory = forumFactory.getForumCategory(subCategoryID);
            if (up) {
                category.setCategoryIndex(subCategory, (curCatIndex-1));
            } else {
                category.setCategoryIndex(subCategory, (curCatIndex+1));
            }
            response.sendRedirect("forums.jsp?cat=" + categoryID);
            return;
        }
    }

    // Get the root forums
    Iterator forums = category.getForums();
    // Get the root categories (no perm checking right now)
    Iterator categories = category.getCategories();

    // Get the root of all categories
    ForumCategory rootCategory = forumFactory.getRootForumCategory();

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
    String title = "Categories &amp; Forums";
    String[][] breadcrumbs = null;
    if (categoryID == -1L || categoryID == 1L) {
        // Root category case
        breadcrumbs = new String[][] {
            {"Main", "main.jsp"},
            {title, "forums.jsp?cat="+rootCategory.getID()}
        };
    }
    else {
        breadcrumbs = new String[][] {
            {"Main", "main.jsp"},
            {title, "forums.jsp?cat="+rootCategory.getID()},
            {"Sub Category", "forums.jsp?cat="+category.getID()}
        };
    };
%>
<%@ include file="title.jsp" %>

<style type="text/css">
.but {
    background-color : #eeeeee;
    font-family : verdana,arial,helvetica,sans-serif;
    font-size : 8pt;
    padding: 0;
}
</style>

<font size="-1">
<b>Category Options: (<%= category.getName() %>)</b><br>

[<a href="perms.jsp?cat=<%= category.getID() %>&mode=<%= CAT_MODE %>&permGroup=<%= CONTENT_GROUP %>"
  >Category Permissions</a>]
&nbsp;
<%   if (Version.getEdition() != Version.Edition.LITE) { %>
    [<a href="perms.jsp?cat=<%= category.getID() %>&mode=<%= CAT_MODE %>&permGroup=<%= ADMIN_GROUP %>"
      >Admins &amp; Moderators</a>]
    &nbsp;
<%  } %>
[<a href="editCat.jsp?cat=<%= category.getID() %>"
  >Edit Category Properties</a>]

</font><p>



<table bgcolor="#bbbbbb" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td>
<table bgcolor="#bbbbbb" cellpadding="0" cellspacing="1" border="0" width="100%">

<tr bgcolor="#dddddd">
    <td width="1%">
        <table cellpadding="3" cellspacing="0" border="0" width="100%">
        <tr><td>
            <font size="-2" face="verdana">
            <b>CATEGORIES</b>
            </font>
        </td></tr>
        </table>
    </td>
    <td width="99%">
        <table cellpadding="3" cellspacing="0" border="0" width="100%">
        <tr><td>
            <font size="-2" face="verdana">
            <b>&nbsp;</b>
            </font>
        </td></tr>
        </table>
    </td>
</tr>

<tr bgcolor="#ffffff">
    <td width="1%" valign="top">
    <%-- Show the root category first --%>
        <table cellpadding="2" cellspacing="0" border="0" width="200">
        <tr>
            <td width="1%"><img src="images/bullet_sm.gif" width="12" height="5" border="0"></td>
            <%  if (rootCategory.getID() == category.getID()) { %>
            <td width="99%" bgcolor="#eeeeee">
            <%  } else { %>
            <td width="99%">
            <%  } %>
            <font size="-1" face="arial">
                <b><a href="forums.jsp?cat=<%= rootCategory.getID() %>">Root Category</a></b>
            </font></td>
            </td>
        </tr>
        </table>
    <%  // Loop through all subcategories:
        for (Iterator iter=rootCategory.getRecursiveCategories(); iter.hasNext();) {
            ForumCategory subCat = (ForumCategory)iter.next();
            int depth = subCat.getCategoryDepth();
    %>
        <table cellpadding="2" cellspacing="0" border="0" width="200">
        <tr><%  for (int i=0; i<depth; i++) { %>
            <td width="1%"><img src="images/blank.gif" width="12" height="5" border="0"></td>
            <%  } %>
            <td width="1%"><img src="images/bullet_sm.gif" width="12" height="5" border="0"></td>
            <%  if (subCat.getID() == category.getID()) { %>
            <td width="<%= (100-depth) %>%" bgcolor="#eeeeee">
            <%  } else { %>
            <td width="<%= (100-depth) %>%">
            <%  } %>
            <font size="-1" face="arial">
                <b><a href="forums.jsp?cat=<%= subCat.getID() %>"><%= subCat.getName() %></a></b>
            </font></td>
            </td>
        </tr>
        </table>
    <%  } %>
        <p>
    </td>
    <td width="99%" bgcolor="#eeeeee" valign="top">


        <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <form action="forums.jsp">
        <input type="hidden" name="cat" value="<%= category.getID() %>">
        <input type="hidden" name="srcCat" value="">
        <tr>
            <td colspan="2">
                <table cellpadding="8" cellspacing="0" border="0">
                <tr><td>
                    <font face="arial">
                    <b>Forums</b>
                    </font>
                </td></tr>
                </table>
            </td>
        </tr>
        <tr>
            <td width="1%">
                <img src="images/blank.gif" width="20" height="1" border="0">
            </td>
            <td width="99%">
                <table bgcolor="#bbbbbb" cellpadding="0" cellspacing="0" border="0" width="100%">
                <tr><td>
                <table bgcolor="#bbbbbb" cellpadding="3" cellspacing="1" border="0" width="100%">
                <tr bgcolor="#dddddd">
                    <td width="94%">
                        <font size="-2" face="verdana">
                        <b>NAME / DESCRIPTION</b>
                        </font>
                    </td>
                    <td align="center" width="1%" nowrap>
                        <font size="-2" face="verdana">
                        &nbsp;<b>ORDER</b>&nbsp;
                        </font>
                    </td>
                    <td align="center" width="1%" nowrap>
                        <font size="-2" face="verdana">
                        &nbsp;<b>THREADS</b>&nbsp;
                        </font>
                    </td>
                    <td align="center" width="1%" nowrap>
                        <font size="-2" face="verdana">
                        &nbsp;<b>MESSAGES</b>&nbsp;
                        </font>
                    </td>
                    <td align="center" width="1%" nowrap>
                        <font size="-2" face="verdana">
                        &nbsp;<b>EDIT</b>&nbsp;
                        </font>
                    </td>
                    <td align="center" width="1%" nowrap>
                        <font size="-2" face="verdana">
                        &nbsp;<b>DELETE</b>&nbsp;
                        </font>
                    </td>
                    <td align="center" width="1%" nowrap>
                        <font size="-2" face="verdana">
                        &nbsp;<b>MOVE</b>&nbsp;
                        </font>
                    </td>
                </tr>
            <%  // Loop through all forums in this category. First, check
                // if there are no forums:
                if (!forums.hasNext()) {
            %>
                <tr bgcolor="#ffffff">
                    <td colspan="7" align="center">
                        <br>
                        <%  if (category.isAuthorized(Permissions.SYSTEM_ADMIN | ForumPermissions.FORUM_CATEGORY_ADMIN | ForumPermissions.MODERATOR))
                            {
                        %>
                            <input type="submit" value="Create New Forum" class="but" name="newforum">
                        <%  } %>
                        <br><br>
                    </td>
                </tr>
            <%  }
                // Print out all existing forums:
                boolean hadForums = false;
                int forumIndex = -1;
                while (forums.hasNext()) {
                    hadForums = true;
                    forumIndex ++;
                    Forum forum = (Forum)forums.next();
                    boolean hasEditPerms = forum.isAuthorized(Permissions.SYSTEM_ADMIN | ForumPermissions.FORUM_CATEGORY_ADMIN | ForumPermissions.MODERATOR);
            %>
                <tr bgcolor="#ffffff">
                    <td>
                        <font size="-1" face="arial">
                        <a href="editForum.jsp?forum=<%= forum.getID() %>"
                         ><b><%= forum.getName() %></b></a>
                        </font>
                        <%  if (forum.getDescription() != null) { %>
                        <font size="-2" face="arial">
                        <br><%= forum.getDescription() %>
                        </font>
                        <%  } %>
                    </td>
                    <td align="center">

                    <table cellpadding="0" cellspacing="0" border="0">
                    <tr><td>
                    <%  if (forumIndex > 0) { %>
                        <a href="forums.jsp?forum=<%= forum.getID() %>&cat=<%= category.getID() %>&fIndex=<%= forumIndex %>&up=true"
                         ><img src="images/arrow_up.gif" width="13" height="9" border="0" vspace="2" hspace="2"></a>
                    <%  } else { %>
                        <img src="images/blank.gif" width="13" height="9" border="0" vspace="2" hspace="2">
                    <%  } %>
                    </td>
                    <td>
                    <%  if (forums.hasNext()) { %>
                        <a href="forums.jsp?forum=<%= forum.getID() %>&cat=<%= category.getID() %>&fIndex=<%= forumIndex %>&down=true"
                         ><img src="images/arrow_down.gif" width="13" height="9" border="0" vspace="2" hspace="2"></a>
                    <%  } else { %>
                        <img src="images/blank.gif" width="13" height="9" border="0" vspace="2" hspace="2">
                    <%  } %>
                    </td></tr>
                    </table>

                    </td>
                    <td align="center">
                        <font size="-1" face="arial">
                        <%  if (forum.isAuthorized(Permissions.SYSTEM_ADMIN | ForumPermissions.FORUM_CATEGORY_ADMIN | ForumPermissions.MODERATOR)) {
                        %>
                            <a href="forumContent.jsp?forum=<%= forum.getID() %>"
                             ><%= forum.getThreadCount() %></a>
                        <%  } else { %>
                            <%= forum.getThreadCount() %>
                        <%  } %>
                        </font>
                    </td>
                    <td align="center">
                        <font size="-1" face="arial">
                        <%= forum.getMessageCount() %>
                        </font>
                    </td>
                    <td align="center">
                        <%  if (hasEditPerms) { %>
                            <a href="editForum.jsp?forum=<%= forum.getID() %>"
                             title="Click to edit all forum properties"
                            ><img src="images/button_edit.gif" width="17" height="17" border="0"></a>
                        <%  } else { %>
                            &nbsp;
                        <%  } %>
                    </td>
                    <td align="center">
                        <%  if (hasEditPerms) { %>
                            <a href="removeForum.jsp?forum=<%= forum.getID() %>"
                             title="Click to delete this forum"
                            ><img src="images/button_delete.gif" width="17" height="17" border="0"></a>
                        <%  } else { %>
                            &nbsp;
                        <%  } %>
                    </td>
                    <td align="center">
                        <%  if (hasEditPerms) { %>
                            <font size="-1" face="arial">
                            <input type="checkbox" name="forum" value="<%= forum.getID() %>">
                            </font>
                        <%  } else { %>
                            &nbsp;
                        <%  } %>
                    </td>
                </tr>
            <%  } // end while forums.hasNext() %>

            <%  if (hadForums) { %>
                <tr bgcolor="#ffffff">
                    <td colspan="6" bgcolor="#eeeeee">
                        <%  if (category.isAuthorized(Permissions.SYSTEM_ADMIN | ForumPermissions.FORUM_CATEGORY_ADMIN | ForumPermissions.MODERATOR))
                            {
                        %>
                            <input type="submit" value="Create New Forum" class="but" name="newforum">
                        <%  } else { %>
                            &nbsp;
                        <%  } %>
                    </td>
                    <td align="center">
                        <font size="-1" face="arial">
                        <input type="submit" value="Move.." class="but" name="moveForum">
                        </font>
                    </td>
                </tr>
            <%  } %>
                </table>
                </td></tr>
                </table>
            </td>
        </tr>
        <tr>
            <td colspan="2">
                <table cellpadding="8" cellspacing="0" border="0">
                <tr><td>
                    <font face="arial">
                    <br>
                    <b>Sub-Categories</b>
                    </font>
                </td></tr>
                </table>
            </td>
        </tr>
        <tr>
            <td width="1%">
                <img src="images/blank.gif" width="20" height="1" border="0">
            </td>
            <td width="99%">
                <table bgcolor="#bbbbbb" cellpadding="0" cellspacing="0" border="0" width="100%">
                <tr><td>
                <table bgcolor="#bbbbbb" cellpadding="3" cellspacing="1" border="0" width="100%">
                <tr bgcolor="#dddddd">
                    <td>
                        <font size="-2" face="verdana" width="94%">
                        <b>NAME / DESCRIPTION</b>
                        </font>
                    </td>
                    <td align="center" width="1%" nowrap>
                        <font size="-2" face="verdana">
                        &nbsp;<b>ORDER</b>&nbsp;
                        </font>
                    </td>
                    <td align="center" width="1%" nowrap>
                        <font size="-2" face="verdana">
                        &nbsp;<b>FORUMS</b>&nbsp;
                        </font>
                    </td>
                    <td align="center" width="1%" nowrap>
                        <font size="-2" face="verdana">
                        &nbsp;<b>CATEGORIES</b>&nbsp;
                        </font>
                    </td>
                    <td align="center" width="1%" nowrap>
                        <font size="-2" face="verdana">
                        &nbsp;<b>EDIT</b>&nbsp;
                        </font>
                    </td>
                    <td align="center" width="1%" nowrap>
                        <font size="-2" face="verdana">
                        &nbsp;<b>DELETE</b>&nbsp;
                        </font>
                    </td>
                    <td align="center" width="1%" nowrap>
                        <font size="-2" face="verdana">
                        &nbsp;<b>MOVE</b>&nbsp;
                        </font>
                    </td>
                </tr>
            <%  // Loop throught all the sub categories. Indicate if there are none:
                if (!categories.hasNext()) {
            %>
                <tr bgcolor="#ffffff">
                    <td colspan="7" align="center">
                        <br>
                        <%  if (category.isAuthorized(Permissions.SYSTEM_ADMIN | ForumPermissions.FORUM_CATEGORY_ADMIN | ForumPermissions.MODERATOR))
                            {
                        %>
                        <input type="submit" value="Create New Sub-Category" class="but" name="newcat">
                        <%  } %>
                        <br><br>
                    </td>
                </tr>
            <%  }
                // Print out all subcategories:
                boolean hadSubCats = false;
                int catIndex = -1;
                while (categories.hasNext()) {
                    catIndex ++;
                    hadSubCats = true;
                    ForumCategory subCat = (ForumCategory)categories.next();
                    boolean hasEditPerms = subCat.isAuthorized(Permissions.SYSTEM_ADMIN | ForumPermissions.FORUM_CATEGORY_ADMIN | ForumPermissions.MODERATOR);
            %>
                <tr bgcolor="#ffffff">
                    <td>
                        <font size="-1" face="arial">
                        <a href="forums.jsp?cat=<%= subCat.getID() %>"
                         ><b><%= subCat.getName() %></b></a>
                        </font>
                        <%  if (subCat.getDescription() != null) { %>
                        <font size="-2" face="arial">
                        <br><%= subCat.getDescription() %>
                        </font>
                        <%  } %>
                    </td>
                    <td align="center">
                        <table cellpadding="0" cellspacing="0" border="0">
                        <tr>
                            <td>
                            <%  if (catIndex > 0) { %>
                                <a href="forums.jsp?subcat=<%= subCat.getID() %>&cat=<%= category.getID() %>&cIndex=<%= catIndex %>&up=true"
                                 ><img src="images/arrow_up.gif" width="13" height="9" border="0" vspace="2" hspace="2"></a>
                            <%  } else { %>
                                <img src="images/blank.gif" width="13" height="9" border="0" vspace="2" hspace="2">
                            <%  } %>
                            </td>
                            <td>
                            <%  if (categories.hasNext()) { %>
                                <a href="forums.jsp?subcat=<%= subCat.getID() %>&cat=<%= category.getID() %>&cIndex=<%= catIndex %>&down=true"
                                 ><img src="images/arrow_down.gif" width="13" height="9" border="0" vspace="2" hspace="2"></a>
                            <%  } else { %>
                                <img src="images/blank.gif" width="13" height="9" border="0" vspace="2" hspace="2">
                            <%  } %>
                            </td>
                        </tr>
                        </table>
                    </td>
                    <td align="center">
                        <font size="-1" face="arial">
                        <%= subCat.getForumCount() %>
                        </font>
                    </td>
                    <td align="center">
                        <font size="-1" face="arial">
                        <%= subCat.getCategoryCount() %>
                        </font>
                    </td>
                    <td align="center">
                        <%  if (hasEditPerms) { %>
                            <a href="editCat.jsp?cat=<%= subCat.getID() %>"
                             title="Click to edit all category properties"
                            ><img src="images/button_edit.gif" width="17" height="17" border="0"></a>
                        <%  } else { %>
                            &nbsp;
                        <%  } %>
                    </td>
                    <td align="center">
                        <%  if (hasEditPerms) { %>
                            <a href="deleteCategory.jsp?cat=<%= subCat.getID() %>"
                             title="Click to delete this category"
                            ><img src="images/button_delete.gif" width="17" height="17" border="0"></a>
                        <%  } else { %>
                            &nbsp;
                        <%  } %>
                    </td>
                    <td align="center">
                        <%  if (hasEditPerms) { %>
                            <font size="-1" face="arial">
                            <input type="submit" value="Move" class="but" name="moveCat"
                             onclick="this.form.srcCat.value='<%= subCat.getID() %>';">
                        <%  } else { %>
                            &nbsp;
                        <%  } %>
                        </font>
                    </td>
                </tr>
            <%  } // end while categories.next() %>

            <%  if (hadSubCats) { %>
                <tr bgcolor="#ffffff">
                    <td colspan="7" bgcolor="#eeeeee">
                        <%  if (category.isAuthorized(Permissions.SYSTEM_ADMIN | ForumPermissions.FORUM_CATEGORY_ADMIN | ForumPermissions.MODERATOR))
                            {
                        %>
                        <input type="submit" value="Create New Sub-Category" class="but" name="newcat">
                        <%  } else { %>
                            &nbsp;
                        <%  } %>
                    </td>
                </tr>
            <%  } %>
                </table>
                </td></tr>
                </table>
            </td>
        </tr>
        </form>
        </table>

        <br>

    </td>
</tr>

</table>
</td></tr>
</table>


<%@ include file="footer.jsp" %>
