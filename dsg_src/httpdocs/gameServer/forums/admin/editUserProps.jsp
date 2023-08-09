<%
   /**
    *	$RCSfile: editUserProps.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/09/25 13:31:31 $
    */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<% // Security check
   if (!isSystemAdmin && !isUserAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // get parameters
   long userID = ParamUtils.getLongParameter(request, "user", -1L);
   String propName = ParamUtils.getParameter(request, "propName");
   String propValue = ParamUtils.getParameter(request, "propValue");

   // Get a user manager
   UserManager userManager = forumFactory.getUserManager();

   // Load the specified user
   User user = userManager.getUser(userID);

   // Put the forum in the session (is needed by the sidebar)
   session.setAttribute("admin.sidebar.user.currentUserID", "" + userID);

   if ("true".equals(request.getParameter("addProperty"))) {
      // Add a property
      if (propName != null && propValue != null) {
         user.setProperty(propName, propValue);
         // Done so redirect
         response.sendRedirect("editUserProps.jsp?user=" + userID);
         return;
      }
   }

   if ("true".equals(request.getParameter("delete"))) {
      // Add a property
      if (propName != null) {
         user.deleteProperty(propName);
         // Done so redirect
         response.sendRedirect("editUserProps.jsp?user=" + userID);
         return;
      }
   }
%>

<% // special onload command to load the sidebar
   onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=users';\"";
%>
<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Edit User Properties";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"User Summary", "users.jsp"},
        {title, "editUserProps.jsp?user="+userID}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Edit extended user properties using the form below. Note, saving a property with
      the same name will update the value of the property.
   </font>
<p>

   <font size="-1"><b>Extended Properties for <i><%= user.getUsername() %>
   </i></b></font>
<ul>
   <font size="-1">To edit the value of a property, use the form below.<p></font>
   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#eeeeee">
                  <td align="center"><font size="-2" face="verdana,arial,helvetica,sans-serif"><b>PROPERTY
                     NAME</b></font></td>
                  <td align="center"><font size="-2" face="verdana,arial,helvetica,sans-serif"><b>PROPERTY
                     VALUE</b></font></td>
                  <td align="center"><font size="-2" face="verdana,arial,helvetica,sans-serif"><b>DELETE</b></font></td>
               </tr>
               <% Iterator properties = user.getPropertyNames();
                  if (!properties.hasNext()) {
               %>
               <tr bgcolor="#ffffff">
                  <td align="center" colspan="3"><font size="-1"><i>No properties</i></font></td>
               </tr>
               <% }
                  while (properties.hasNext()) {
                     String propertyName = (String) properties.next();
                     String propertyValue = user.getProperty(propertyName);
               %>
               <tr bgcolor="#ffffff">
                  <td><font size="-1"><%= propertyName %>
                  </font></td>
                  <td><font size="-1"><%= propertyValue %>
                  </font></td>
                  <td align="center"><a
                     href="editUserProps.jsp?user=<%= userID %>&delete=true&propName=<%= propertyName %>"
                  ><img src="images/button_delete.gif" width="17" height="17" alt="Click to delete this property"
                        border="0"></a
                  ></td>
               </tr>
               <% } %>
            </table>
         </td>
      </tr>
   </table>
</ul>

<form action="editUserProps.jsp" method="post">
   <input type="hidden" name="user" value="<%= userID %>">
   <input type="hidden" name="addProperty" value="true">
   <font size="-1"><b>Add or Edit Extended Properties</b></font>
   <ul>
      <table bgcolor="#cccccc" cellpadding="0" cellspacing="0" border="0" width="">
         <tr>
            <td>
               <table bgcolor="#cccccc" cellpadding="3" cellspacing="1" border="0" width="100%">
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Property Name:</font></td>
                     <td><input type="text" name="propName" size="20" maxlength="100"></td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td valign="top"><font size="-1">Property Value:</font></td>
                     <td><textarea cols="40" rows="10" name="propValue" wrap="virtual"></textarea></td>
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

