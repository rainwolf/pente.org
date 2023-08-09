<%
   /**
    *	$RCSfile: userSearch.jsp,v $
    *	$Revision: 1.1 $
    *	$Date: 2002/08/16 06:52:22 $
    */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils" %>

<%@ include file="global.jsp" %>

<% // get parameters
   boolean lookup = ParamUtils.getBooleanParameter(request, "lookup");
   long userID = ParamUtils.getLongParameter(request, "user", -1L);
   String username = ParamUtils.getParameter(request, "user");

   // get a user manager so we can lookup the user
   UserManager userManager = forumFactory.getUserManager();

   // The user requested
   User user = null;
   boolean error = false;
   try {
      user = userManager.getUser(userID);
   } catch (Exception e) {
      try {
         user = userManager.getUser(username);
      } catch (Exception e2) {
         error = true;
      }
   }

   // Remove the user in the session (if we come to this page, the sidebar
   // shouldn't show the specific user options).
   session.removeAttribute("admin.sidebar.users.currentUserID");
%>

<% // special onload command to load the sidebar
   onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=users';\"";
%>
<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "User Search";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"User Summary", "users.jsp"},
        {title, "userSearch.jsp?username="+username}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      <% if (error) { %>
      Error: Unable to find user. Please go back to the last page and try again.
      <% } else { %>
      Below is a summary of user info.
      <% } %>
   </font>
<p>

<form action="userSearch.jsp">
   <font size="-1">Jump to user: (enter ID or username)</font>
   <input type="text" name="user" size="20" maxlength="100">
   <input type="submit" value="Go">
</form>
<p>

      <%  if (user != null) { %>

<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr>
      <td>
         <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
            <tr bgcolor="#eeeeee">
               <td align="center" nowrap><font size="-2" face="verdana"><b>USER ID</b></font></td>
               <td align="center" nowrap><font size="-2" face="verdana"><b>USERNAME</b></font></td>
               <td align="center" nowrap><font size="-2" face="verdana"><b>NAME</b></font></td>
               <td align="center" nowrap><font size="-2" face="verdana"><b>EMAIL</b></font></td>
               <td align="center" nowrap><font size="-2" face="verdana"><b>EDIT</b></font></td>
               <td align="center" nowrap><font size="-2" face="verdana"><b>DELETE</b></font></td>
            </tr>
            <%
               String name = user.getName();
               String email = user.getEmail();
            %>
            <tr bgcolor="#ffffff">
               <td align="center" width="2%"><font size="-1"><%= user.getID() %>
               </font></td>
               <td width="30%"><font size="-1"><a href="editUser.jsp?user=<%= user.getID() %>"><%= user.getUsername() %>
               </a></font></td>
               <td width="30%"><font size="-1"><%= (name != null) ? name : "" %>
               </font></td>
               <td width="30%"><font size="-1"><%= (email != null) ? email : "" %>
               </font></td>
               <td align="center" width="4%"
               ><a href="editUser.jsp?user=<%= user.getID() %>"
               ><img src="images/button_edit.gif" width="17" height="17" alt="Edit User Properties..." border="0"
               ></a
               ></td>
               <td align="center" width="4%"
               ><a href="removeUser.jsp?user=<%= user.getID() %>"
               ><img src="images/button_delete.gif" width="17" height="17" alt="Delete User..." border="0"
               ></a
               ></td>
            </tr>
         </table>
      </td>
   </tr>
</table>

<% } %>

<p>

   </body>
   </html>


