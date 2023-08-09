<%
   /**
    *	$RCSfile: userProfile.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/09/25 13:31:31 $
    */
%>

<%@ page import="java.util.*,
                 java.net.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<% // Permission check
   // No permission check required.

   // get parameters

   String username = ParamUtils.getParameter(request, "user");
   long userID = ParamUtils.getLongParameter(request, "user", -1L);
   String propName = ParamUtils.getParameter(request, "propName");
   String propValue = ParamUtils.getParameter(request, "propValue");

   // Get a user manager to get and set user properties
   UserManager userManager = forumFactory.getUserManager();

   // Load the user
   User user = null;
   try {
      user = userManager.getUser(userID);
   } catch (Exception e) {
      try {
         user = userManager.getUser(username);
      } catch (Exception e2) {
      }
   }

   // Throw an error if the user was not loaded.
   if (user == null) {
      throw new UserNotFoundException("User " + request.getParameter("user")
         + " not found.");
   }

   if ("true".equals(request.getParameter("saveProperty"))) {
      if (propName != null && propValue != null) {
         user.setProperty(propName, propValue);
         response.sendRedirect("userProfile.jsp?user=" + userID);
         return;
      }
   }

   if ("true".equals(request.getParameter("delete"))) {
      if (propName != null) {
         user.deleteProperty(propName);
         response.sendRedirect("userProfile.jsp?user=" + userID);
         return;
      }
   }
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "User Profile";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "userProfile.jsp?user=" + user.getID()}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Profile for user <b><%= user.getUsername() %>
   </b>:
      <% if (isSystemAdmin) { %>
      (<a href="editUser.jsp?user=<%= userID %>">edit user settings</a>)
      <% } %>
   </font>
<p>
<form action="userProfile.jsp">
   <font size="-1">
      Jump to user (enter user ID or username):
      <input type="text" name="user" size="10" maxlength="100">
      <input type="submit" value="Go">
   </font>
</form>
<p>
      <%  // User properties
    userID = user.getID();
    String name = user.getName();
    String email = user.getEmail();
    Date creationDate = user.getCreationDate();
    Date modifiedDate = user.getModificationDate();
    int numPosts = 0; //userManager.userMessageCount(user);
%>
<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr>
      <td bgcolor="#ffffff" width="1%" nowrap><img src="images/blank.gif" width="25" height="1" border="0"></td>
      <td width="99%">
         <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
            <tr bgcolor="#ffffff">
               <td width="25%"><font size="-1">User ID:</font></td>
               <td width="75%"><font size="-1"><%= user.getID() %>
               </font></td>
            </tr>
            <tr bgcolor="#ffffff">
               <td width="25%"><font size="-1">Name:</font></td>
               <td width="75%"><font size="-1"><%= (name != null) ? name : "<i>Not Set</i>" %>
               </font></td>
            </tr>
            <tr bgcolor="#ffffff">
               <td width="25%"><font size="-1">Email Address:</font></td>
               <td width="75%"><font size="-1"><a href="mailto:<%= email %>"><%= email %>
               </a></font></td>
            </tr>
            <tr bgcolor="#ffffff">
               <td width="25%"><font size="-1">Account Created:</font></td>
               <td width="75%"><font size="-1"><%= SkinUtils.formatDate(request, pageUser, creationDate) %>
               </font></td>
            </tr>
            <tr bgcolor="#ffffff">
               <td width="25%"><font size="-1">Last Updated:</font></td>
               <td width="75%"><font size="-1"><%= SkinUtils.formatDate(request, pageUser, modifiedDate) %>
               </font></td>
            </tr>
            <tr bgcolor="#ffffff">
               <td width="25%"><font size="-1">Number of Posts:</font></td>
               <td width="75%"><font size="-1"><%= numPosts %>
               </font></td>
            </tr>
         </table>
      </td>
   </tr>
</table>
<p>

   <font size="-1">
      Extended Properties for <b><%= user.getUsername() %>
   </b>:
   </font>
<p>
      <%  // Extended properties
    Iterator properties = user.getPropertyNames();
%>
<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr>
      <td bgcolor="#ffffff" width="1%" nowrap><img src="images/blank.gif" width="25" height="1" border="0"></td>
      <td width="99%">
         <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
            <tr bgcolor="#eeeeee">
               <td><font size="-2" face="verdana"><b>PROPERTY NAME</b></font></td>
               <td><font size="-2" face="verdana"><b>PROPERTY VALUE</b></font></td>
               <td><font size="-2" face="verdana"><b>DELETE</b></font></td>
            </tr>
            <% if (!properties.hasNext()) { %>
            <tr bgcolor="#ffffff">
               <td colspan="3"><font size="-1"><i>No Extended Properties</i></font></td>
            </tr>
            <% } %>
            <% while (properties.hasNext()) {
               String propertyName = (String) properties.next();
               String propertyValue = user.getProperty(propertyName);
            %>
            <tr bgcolor="#ffffff">
               <td width="25%"><font size="-1"><%= propertyName %>
               </font></td>
               <td width="74%"><font size="-1"><%= propertyValue %>
               </font></td>
               <td width="1%" align="center">
                  <a href="userProfile.jsp?user=<%= userID %>&delete=true&propName=<%= URLEncoder.encode(propertyName) %>"
                     title="Click to delete property"
                  ><img src="images/button_delete.gif" width="17" height="17" border="0"></a>
               </td>
            </tr>
            <% } %>
         </table>
      </td>
   </tr>
</table>
<p>

   <font size="-1">
      Add Extended Property:
   </font>
<p>
<form action="userProfile.jsp" method="post">
   <input type="hidden" name="user" value="<%= userID %>">
   <input type="hidden" name="saveProperty" value="true">
   <table bgcolor="#cccccc" cellpadding="0" cellspacing="0" border="0" width="100%">
      <tr>
         <td bgcolor="#ffffff" width="1%" nowrap><img src="images/blank.gif" width="25" height="1" border="0"></td>
         <td width="99%">
            <table bgcolor="#cccccc" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Property Name:</font></td>
                  <td><input type="text" name="propName" size="20" maxlength="100"></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Property Value:</font></td>
                  <td><input type="text" name="propValue" size="20" maxlength="200"></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td colspan="2">
                     <input type="submit" value="Save Property">
                  </td>
               </tr>
            </table>
         </td>
      </tr>
   </table>
</form>

</body>
</html>
