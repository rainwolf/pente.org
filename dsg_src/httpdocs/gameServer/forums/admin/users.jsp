<%
   /**
    *	$RCSfile: users.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/10/24 21:38:51 $
    */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils,
                 com.jivesoftware.util.LocaleUtils"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%! // Global vars
   static final int[] RANGES = {15, 30, 50, 100};
%>

<% // Permission check
   if (!isSystemAdmin && !isUserAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // get parameters
   int start = ParamUtils.getIntParameter(request, "start", 0);
   int range = ParamUtils.getIntParameter(request, "range", 15);

   // If the value of "start" isn't passed in as a parameter, look for it in
   // the session
   if (request.getParameter("start") == null) {
      try {
         start = Integer.parseInt((String) session.getAttribute("admin.users.start"));
      } catch (Exception e) {
      }
   } else {
      session.setAttribute("admin.users.start", start + "");
   }
   // Check for the value of "range" as a property of the admin:
   if (request.getParameter("range") == null) {
      String userRange = pageUser.getProperty("jiveAdminUserRange");
      if (userRange != null) {
         try {
            range = Integer.parseInt(userRange);
         } catch (Exception e) {
            pageUser.setProperty("jiveAdminUserRange", range + "");
         }
      } else {
         pageUser.setProperty("jiveAdminUserRange", range + "");
      }
   } else {
      pageUser.setProperty("jiveAdminUserRange", range + "");
   }

   // get an Iterator of users
   UserManager manager = forumFactory.getUserManager();
   Iterator users = manager.users(start, range);

   // Total user count
   int userCount = manager.getUserCount();

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
    String title = "User Summary";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "users.jsp"}
    };
%>
   <%@ include file="title.jsp" %>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr>
      <td>
         <font size="-1">
            <%= LocaleUtils.getLocalizedNumber(userCount, JiveGlobals.getLocale()) %>
            total user<%= (userCount == 1) ? "" : "s" %> (<%= range %> displayed per page).
         </font>
      </td>
      <form>
         <td align="right">
            <font size="-1">
               Number of users per page:
            </font>
            <select size="1"
                    onchange="location.href='users.jsp?start=<%= start %>&range='+this.options[this.selectedIndex].value;">
               <% for (int i = 0; i < RANGES.length; i++) {
                  String selected = "";
                  if (RANGES[i] == range) {
                     selected = " selected";
                  }
               %>
               <option value="<%= RANGES[i] %>"<%= selected %>><%= RANGES[i] %>
                     <%  } %>
            </select>
         </td>
      </form>
   </tr>
</table>
<p>

      <%	String message = getOneTimeMessage(session,"admin.users.message");
    if (message != null) {
%>
   <font size="-1"><i><%= message %>
   </i></font>
<p>
      <%	} %>

<form action="userSearch.jsp">
   <font size="-1">Jump to user: (enter ID or username)</font>
   <input type="text" name="user" size="20" maxlength="100">
   <input type="submit" value="Go">
</form>
<p>

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
            <% while (users.hasNext()) {
               User user = (User) users.next();
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
            <% } %>
         </table>
      </td>
   </tr>
</table>

<p>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr>
      <% if (start > 0) { %>
      <td width="1%" nowrap><font size="-1"><a href="users.jsp?start=<%= start-range %>">Previous <%= range %> users</a></font>
      </td>
      <% } else { %>
      <td width="1%" nowrap><font size="-1">&nbsp;</font></td>
      <% } %>

      <% if (start > 0) { %>
      <td width="49%" align="right"><font size="-1"><a href="users.jsp?start=0">Front Page</a>&nbsp;</font></td>
      <% } else { %>
      <td width="49%" nowrap align="right"><font size="-1">&nbsp;</font></td>
      <% } %>

      <% int lastPage = ((userCount / (range)) * range); %>
      <td width="49%" nowrap>
         <font size="-1">
            &nbsp;<a href="users.jsp?start=<%= lastPage %>">Last Page</a>
         </font>
      </td>

      <% if (start + range < userCount) { %>
      <td width="1%" nowrap><font size="-1"><a href="users.jsp?start=<%= start+range %>">Next <%= range %>
         users</a></font></td>
      <% } else { %>
      <td width="1%" nowrap><font size="-1">&nbsp;</font></td>
      <% } %>
   </tr>
</table>

</body>
</html>


