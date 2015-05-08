<%
/**
 *    $RCSfile: mergeForums.jsp,v $
 *    $Revision: 1.2.4.1 $
 *    $Date: 2003/03/20 23:02:45 $
 */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.database.*,
                 com.jivesoftware.forum.util.*"
    errorPage="error.jsp"
%>

<%! // Global methods/vars/ etc

    // Returns a separator
    String getSep(String sep, int num) {
        StringBuffer buf = new StringBuffer(sep.length()*num);
        for (int i=0; i<num; i++) {
            buf.append(sep);
        }
        return buf.toString();
    }
%>

<%@ include file="global.jsp" %>

<%  // Get parameters
    long forumID = ParamUtils.getLongParameter(request,"forum",-1L);
    long destForumID = ParamUtils.getLongParameter(request,"destForum",-1L);
    long destCatID = ParamUtils.getLongParameter(request,"destCat",-1L);
    boolean doMerge = ParamUtils.getBooleanParameter(request,"doMerge");
    boolean newForum = ParamUtils.getBooleanParameter(request,"newForum");
    String destForumName = ParamUtils.getParameter(request,"destForumName");

    // Load forums
    Forum forum = forumFactory.getForum(forumID);
    Forum destForum = null;
    ForumCategory destCat = null;

    if (!isSystemAdmin && !forum.isAuthorized(ForumPermissions.FORUM_ADMIN)) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }

    Map errors = new HashMap();
    Map errorMessages = new HashMap();
    if (doMerge) {
        if (!newForum) {
            // Move to an existing forum
            try {
                destForum = forumFactory.getForum(destForumID);
                if (destForum.getID() == forumID) {
                    errors.put("destForum","Destination forum can not be the same as the source forum.");
                }
                else {
                    // Do the merge to the spef forum
                    try {
                        forumFactory.mergeForums(destForum, forum);
                        response.sendRedirect("mergeForumsSuccess.jsp?forum=" + destForum.getID());
                        return;
                    }
                    catch (UnauthorizedException ue) {
                        errors.put("destForum", "You don't have permission to merge this forum");
                    }
                }
            }
            catch (ForumNotFoundException e) {
                errors.put("destForum","Invalid destination forum");
            }
        }
        else {
            // Move to a new forum
            if (destForumName == null) {
                errors.put("destCat","Invalid forum name");
            }
            else {
                // Load the dest category
                try {
                    destCat = forumFactory.getForumCategory(destCatID);
                    // Create the category
                    Forum tempForum = forumFactory.createForum(destForumName, null, destCat);
                    // Do the merge to the new forum
                    try {
                        forumFactory.mergeForums(tempForum, forum);
                        response.sendRedirect("mergeForumsSuccess.jsp?forum=" + tempForum.getID());
                        return;
                    }
                    catch (UnauthorizedException ue) {
                        errors.put("destCat", "You don't have permission to merge this forum");
                    }
                }
                catch (ForumCategoryNotFoundException fcnfe) {
                    errors.put("destCat", "Invalid destination category");
                }
                catch (UnauthorizedException ue) {
                    errors.put("destCat", "You don't have permission to create a new forum.");
                }
            }
        }

        // Add a global error
        if (errors.size() > 0) {
            errorMessages.put("message","Please correct the errors listed below");
        }
    }

    // Indentation separator
    String sep = "&nbsp;";
%>

<%@ include file="header.jsp" %>

<%  // Title of this page and breadcrumbs
    String title = "Merge Forum Data";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "mergeForums.jsp?forum="+forumID}
    };
%>
<%@ include file="title.jsp" %>

<p>
Use the form below to specify where the data in the forum "<b><%= forum.getName() %></b>"
should be moved.
</p>

<style type="text/css">
.forum-list .cat-label {
    color : #666;
}
.subheader {
    font-weight : bold;
}
.warning, .description {
    font-size : 0.85em;
}
.error {
    font-size : 0.85em;
    color : #c00;
}
</style>

<form action="mergeForums.jsp" name="f">
<input type="hidden" name="doMerge" value="true">
<input type="hidden" name="forum" value="<%= forumID %>">

<%  if (errorMessages != null && errorMessages.size() > 0) { %>

    <p class="error">
    <%  if (errorMessages.size() == 1) { %>

        <%= errorMessages.get(errorMessages.keySet().iterator().next()) %>

    <%  } else { %>

        <ul>
            <%  for (Iterator iter=errorMessages.keySet().iterator(); iter.hasNext(); ) { %>

                <li><%= errorMessages.get(iter.next()) %>

            <%  } %>
        </ul>

    <%  } %>
    </p>

<%  } %>

<p class="subheader">
Choose Destination Forum
</p>

<table cellpadding="3" cellspacing="0" border="0" width="100%">
<tr>
    <td width="1%" nowrap>
        <input type="radio" name="newForum" value="false"<%= ((!newForum) ? " checked" : "") %> id="rb01">
    </td>
    <td width="99%">
        <label for="rb01">
        Move all data TO the following forum:
        </label>
    </td>
</tr>
<tr>
    <td width="1%" nowrap>&nbsp;</td>
    <td width="99%">
        <%  if (errors.containsKey("destForum")) { %>

            <span class="error">
            <%= errors.get("destForum") %>
            </span>
            <br>

        <%  } %>
        <select size="1" name="destForum" class="forum-list"
         onfocus="this.form.newForum[0].checked=true;">

            <option value="*">Choose forum...
            <option value="*">

            <option value="*" disabled><span class="cat-label">Root Category</span>

            <%  for (Iterator rootForums=forumFactory.getRootForumCategory().getForums(); rootForums.hasNext(); ) {
                    Forum f = (Forum)rootForums.next();
                    if (isSystemAdmin || f.isAuthorized(ForumPermissions.FORUM_ADMIN)) {
            %>
                    <option value="<%= f.getID() %>" class="forum-label"
                     <%= ((f.getID() == destForumID) ? " selected" : "") %>><%= getSep(sep,1) %>&nbsp;&#149;&nbsp;<%= f.getName() %>

                    <%  if (forumID == f.getID()) { %>

                        (current)

                    <%  } %>

            <%      }
                }
            %>

            <%  for (Iterator cats=forumFactory.getRootForumCategory().getRecursiveCategories(); cats.hasNext(); ) {
                    ForumCategory c = (ForumCategory)cats.next();
                    int depth = c.getCategoryDepth();
            %>
                <option value="*" disabled><span class="cat-label"><%= getSep(sep, depth*4) %><%= c.getName() %></span>

                <%  for (Iterator forums=c.getForums(); forums.hasNext(); ) {
                        Forum f = (Forum)forums.next();
                        if (isSystemAdmin || f.isAuthorized(ForumPermissions.FORUM_ADMIN)) {
                %>
                        <option value="<%= f.getID() %>" class="forum-label"
                         <%= ((f.getID() == destForumID) ? " selected" : "") %>><%= getSep(sep, depth*4) %>&nbsp;&#149;&nbsp;<%= f.getName() %>

                        <%  if (forumID == f.getID()) { %>

                            (current)

                        <%  } %>

                <%      }
                    }
                %>

            <%  } %>
        </select>
    </td>
</tr>
<tr>
    <td width="1%" nowrap>
        <input type="radio" name="newForum" value="true"<%= ((newForum) ? " checked" : "") %> id="rb02">
    </td>
    <td width="99%">
        <label for="rb02">
        Move all data TO a new forum:
        </label>
    </td>
</tr>
<tr>
    <td width="1%" nowrap>&nbsp;</td>
    <td width="99%">
        <%  if (errors.containsKey("destCat")) { %>

            <span class="error">
            <%= errors.get("destCat") %>
            </span>
            <br>

        <%  } %>
        Create forum:
        <input type="text" name="destForumName" size="30" maxlength="100" onfocus="this.form.newForum[1].checked=true;"
         value="<%= ((destForumName!=null) ? destForumName : "") %>">
        in category:
        <select size="1" name="destCat" class="cat-list"
         onfocus="this.form.newForum[1].checked=true;">

            <option value="*">Choose category...
            <option value="*">

            <option value="<%= forumFactory.getRootForumCategory().getID() %>"
             <%= (destCatID == forumFactory.getRootForumCategory().getID()) %>
             ><span class="cat-label">Root Category</span>

            <%  for (Iterator rootForums=forumFactory.getRootForumCategory().getForums(); rootForums.hasNext(); ) {
                    Forum f = (Forum)rootForums.next();
                    if (isSystemAdmin || forumFactory.getRootForumCategory().isAuthorized(ForumPermissions.FORUM_CATEGORY_ADMIN)) {
            %>
                    <option value="<%= f.getID() %>" class="forum-label" disabled><%= getSep(sep,1) %>&nbsp;&#149;&nbsp;<%= f.getName() %>

            <%      }
                }
            %>

            <%  for (Iterator cats=forumFactory.getRootForumCategory().getRecursiveCategories(); cats.hasNext(); ) {
                    ForumCategory c = (ForumCategory)cats.next();
                    if (isSystemAdmin || c.isAuthorized(ForumPermissions.FORUM_CATEGORY_ADMIN)) {
                        int depth = c.getCategoryDepth();
            %>
                    <option value="<%= c.getID() %>"<%= ((destCatID == c.getID()) ? " selected" : "") %>
                     ><span class="cat-label"><%= getSep(sep, depth*4) %><%= c.getName() %></span>

                    <%  for (Iterator forums=c.getForums(); forums.hasNext(); ) {
                            Forum f = (Forum)forums.next();
                    %>
                        <option value="<%= f.getID() %>" class="forum-label" disabled><%= getSep(sep, depth*4) %>&nbsp;&#149;&nbsp;<%= f.getName() %>

                    <%  } %>

            <%      }
                }
            %>
        </select>
    </td>
</tr>
</table>

<p class="subheader">
Data Summary
</p>

<table cellpadding="3" cellspacing="0" border="0" width="100%">
<tr valign="top">
    <td width="1%" nowrap>
        Forum:
    </td>
    <td width="99%">
        <b><%= forum.getName() %></b>

        <%  if (forum.getDescription() != null) { %>

            <br>
            <span class="description">
            <%= forum.getDescription() %>
            </span>

        <%  } %>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        Topics/Messages:
    </td>
    <td width="99%">
        <%= LocaleUtils.getLocalizedNumber(forum.getThreadCount()) %>
        /
        <%= LocaleUtils.getLocalizedNumber(forum.getMessageCount()) %>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        Created:
    </td>
    <td width="99%">
        <%  DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT); %>
        <%= formatter.format(forum.getCreationDate()) %>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        Last Updated:
    </td>
    <td width="99%">
        <%= formatter.format(forum.getModificationDate()) %>
    </td>
</tr>
</table>

<br><br>
<input type="submit" name="do" value="Merge Data">
<p class="warning">
(Note: This might take a few moments, depending on the number of messages moved)
</p>

</form>

<%@ include file="footer.jsp" %>
