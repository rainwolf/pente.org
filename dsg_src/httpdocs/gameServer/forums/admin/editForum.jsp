<%
   /**
    *	$RCSfile: editForum.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/10/17 20:10:34 $
    */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%! // Global vars, methods, etc...

   // Date formatter for creation date/modified date
   SimpleDateFormat dateFormatter = new SimpleDateFormat("");
%>

<% // Permission check
   if (!isSystemAdmin && !isForumAdmin && !isCatAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Get parameters
   long forumID = ParamUtils.getLongParameter(request, "forum", -1L);
   String name = ParamUtils.getParameter(request, "name");
   String description = ParamUtils.getParameter(request, "description", true);
   boolean saveName = ParamUtils.getBooleanParameter(request, "saveName");

   // Load up the forum specified
   Forum forum = forumFactory.getForum(forumID);

   // Get the forum's category:
   ForumCategory category = forum.getForumCategory();

   // Do a perm check to make sure we're a category admin on this category. If
   // we're not a category admin then we need to be a forum admin. If neither,
   // throw an exception
   if (!isSystemAdmin && !category.isAuthorized(ForumPermissions.FORUM_CATEGORY_ADMIN)) {
      if (!forum.isAuthorized(ForumPermissions.FORUM_ADMIN)) {
         throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
      }
   }

   // Put the forum in the session (is needed by the sidebar)
   session.setAttribute("admin.sidebar.forums.currentForumID", "" + forumID);

   // save the name & description if requested
   if (saveName) {
      if (name != null) {
         forum.setName(name);
      }
      if (description != null) {
         forum.setDescription(description);
      }
      setOneTimeMessage(session, "message", "Changes saved.");
      response.sendRedirect("editForum.jsp?forum=" + forumID);
      return;
   }

   name = forum.getName();
   description = forum.getDescription();
%>

<% // special onload command to load the sidebar
   onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=forum';\"";
%>
<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Forum Settings";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Forums", "forums.jsp"},
        {title, "editForum.jsp?forum="+forumID}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Set the name and description of a forum below.
   </font>

<p>

   <font size="-1"><b>General Information</b></font>
<ul>
   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#eeeeee">
                  <td align="center"><font size="-2" face="verdana"><b>FORUM ID</b></font></td>
                  <td align="center"><font size="-2" face="verdana"><b>THREADS</b></font></td>
                  <td align="center"><font size="-2" face="verdana"><b>MESSAGES</b></font></td>
                  <td align="center"><font size="-2" face="verdana"><b>CREATED ON</b></font></td>
                  <td align="center"><font size="-2" face="verdana"><b>LAST MODIFIED</b></font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td align="center" bgcolor="#eeeeee"><font size="-1"><%= forum.getID() %>
                  </font></td>
                  <td align="center"><font size="-1"><%= forum.getThreadCount() %>
                  </font></td>
                  <td align="center"><font size="-1"><%= forum.getMessageCount() %>
                  </font></td>
                  <td align="center"><font size="-1">&nbsp;<%= JiveGlobals.formatDateTime(forum.getCreationDate()) %>&nbsp;</font>
                  </td>
                  <td align="center"><font
                     size="-1">&nbsp;<%= JiveGlobals.formatDateTime(forum.getModificationDate()) %>&nbsp;</font></td>
               </tr>
            </table>
         </td>
      </tr>
   </table>
</ul>

<font size="-1"><b>Edit Name and Description</b></font>
<ul>
   <font size="-1">
      Change the name or description of this forum using the forum below.
   </font>
   <p>
         <%  String message = getOneTimeMessage(session,"message");
        if (message != null) {
    %>
      <font size="-1" color="#339900"><b><i><%= message %>
      </i></b></font>
   <p>
         <%  } %>
   <form action="editForum.jsp" method="post">
      <input type="hidden" name="saveName" value="true">
      <input type="hidden" name="forum" value="<%= forumID %>">
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">Name:</font></td>
            <td><input type="text" name="name" size="40" maxlength="100" value="<%= (name!=null)?name:"" %>"></td>
         </tr>
         <tr>
            <td valign="top"><font size="-1">Description:</font></td>
            <td><textarea name="description" cols="40" rows="5"
                          wrap="virtual"><%= (description != null) ? description : "" %></textarea></td>
         </tr>
         <tr>
            <td>&nbsp;</td>
            <td><input type="submit" value="Save Changes"></td>
         </tr>
      </table>
   </form>
</ul>

<form action="forums.jsp">
   <br>
   <hr size="1">
   <center>
      <input type="submit" value="Back to Forum Listing">
   </center>
</form>

<p>

   <%@ include file="footer.jsp" %>
