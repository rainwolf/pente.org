<%
   /**
    *	$RCSfile: groupAdmins.jsp,v $
    *	$Revision: 1.2.4.1 $
    *	$Date: 2003/03/26 00:12:26 $
    */
%>

<%@ page import="java.util.*,
                 java.net.URLEncoder,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils" %>

<%@ include file="global.jsp" %>

<% // Security check
   if (!isSystemAdmin && !isGroupAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // get parameters
   long groupID = ParamUtils.getLongParameter(request, "group", -1L);
   boolean add = ParamUtils.getBooleanParameter(request, "add");
   boolean remove = ParamUtils.getBooleanParameter(request, "remove");
   long memberID = ParamUtils.getLongParameter(request, "member", -1L);
   String userList = ParamUtils.getParameter(request, "userList");

   // Get user and group managers
   UserManager userManager = forumFactory.getUserManager();
   GroupManager groupManager = forumFactory.getGroupManager();

   // Load the group
   Group group = groupManager.getGroup(groupID);

   if (add && userList != null) {
      StringTokenizer tokenizer = new StringTokenizer(userList, ",\n\r");
      while (tokenizer.hasMoreTokens()) {
         String token = tokenizer.nextToken().trim();
         // try to load the user by ID first
         User user = null;
         try {
            user = userManager.getUser(Long.parseLong(token));
         } catch (Exception ignored1) {
            // loading by user ID failed, so try by username
            try {
               user = userManager.getUser(token);
            } catch (Exception ignored2) {
            }
         }
         // if user is not null, add the user to the group
         if (user != null) {
            group.addAdministrator(user);
         }
      }
      // done, so redirect
      response.sendRedirect("groupAdmins.jsp?group=" + groupID);
      return;
   }

   if (remove) {
      User admin = userManager.getUser(memberID);
      group.removeAdministrator(admin);
      // done, so redirect
      response.sendRedirect("groupAdmins.jsp?group=" + groupID);
      return;
   }

   // Total number of members in this group
   int memberCount = group.getAdministratorCount();

   // Iterator of members
   Iterator admins = group.administrators();
%>

<% // special onload command to load the sidebar
   onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=users';\"";
%>
<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Group Admins";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Groups Summary", "groups.jsp"},
        {title, "groupAdmins.jsp?group="+groupID}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Designate administrators of this group using the form below.
   </font>

<p>

   <font size="-1"><b>Add Admins to this Group</b></font>
<p>
<form action="groupAdmins.jsp">
   <input type="hidden" name="group" value="<%= groupID %>">
   <input type="hidden" name="add" value="true">
   <ul>
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">
               Enter a list of user IDs or usernames separated by commas (example:
               john, user23, 58).
            </font>
            </td>
         </tr>
         <tr>
            <td><textarea name="userList" cols="30" rows="2"></textarea></td>
         </tr>
         <tr>
            <td><input type="submit" value="Add Admins To Group"></td>
         </tr>
      </table>
   </ul>
</form>
<p>

   <font size="-1"><b>Current Group Admins</b></font>
<p>
<ul>
   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#eeeeee">
                  <td align="center"><font size="-2" face="verdana"><b>USERNAME</b></font></td>
                  <td align="center"><font size="-2" face="verdana"><b>NAME</b></font></td>
                  <td align="center"><font size="-2" face="verdana"><b>REMOVE ADMIN</b></font></td>
               </tr>
               <% if (!admins.hasNext()) { %>
               <tr bgcolor="#ffffff">
                  <td colspan="3" align="center"><font size="-1"><i>No Admins Designated</i></font></td>
               </tr>
               <% }
                  while (admins.hasNext()) {
                     User admin = (User) admins.next();
                     String name = admin.getName();
               %>
               <tr bgcolor="#ffffff">
                  <td><font size="-1"><%= admin.getUsername() %>
                  </font></td>
                  <td><font size="-1">&nbsp;<%= (name != null) ? name : "&nbsp;" %>&nbsp;</font></td>
                  <td align="center"><a
                     href="groupAdmins.jsp?group=<%= groupID %>&remove=true&member=<%= admin.getID() %>"
                     title="Click to remove this admin from the group"
                  ><img src="images/button_delete.gif" width="17" height="17" border="0"></a>
                  </td>
               </tr>
               <% } %>
            </table>
         </td>
      </tr>
   </table>
</ul>
<p>

   </body>
   </html>



