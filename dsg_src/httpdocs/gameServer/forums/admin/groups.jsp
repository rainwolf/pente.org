<%
   /**
    *	$RCSfile: groups.jsp,v $
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

<% // Security check
   if (!isSystemAdmin && !isGroupAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // get parameters
   int start = ParamUtils.getIntParameter(request, "start", 0);
   int range = ParamUtils.getIntParameter(request, "range", 10);

   // if the start isn't passed in as a parameter, look for it in the session
   if (request.getParameter("start") == null) {
      try {
         start = Integer.parseInt((String) session.getAttribute("admin.groups.start"));
      } catch (Exception e) {
      }
   } else {
      session.setAttribute("admin.groups.start", start + "");
   }

   // get an Iterator of groups
   GroupManager manager = forumFactory.getGroupManager();
   Iterator groups = manager.getGroups();

   // Total group count
   int groupCount = manager.getGroupCount();

   // Remove the user in the session (if we come to this page, the sidebar
   // shouldn't show the specific user options).
   session.removeAttribute("admin.sidebar.groups.currentGroupID");
%>

<% // special onload command to load the sidebar
   onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=users';\"";
%>
<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Groups Summary";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "groups.jsp"}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      <%= LocaleUtils.getLocalizedNumber(groupCount, JiveGlobals.getLocale()) %>
      total group<%= (groupCount == 1) ? "" : "s" %> (<%= range %> displayed per page).
      <% if (groupCount > 0) { %>
      Click the name of a group below to edit its properties.
      <% } %>
   </font>
<p>

      <%  if (isSystemAdmin) { %>
   <font size="-1">
      <a href="createGroup.jsp">Create a new group</a>.
   </font>
      <%  } %>
<p>

      <%	String message = getOneTimeMessage(session,"admin.groups.message");
    if (message != null) {
%>
   <font size="-1"><i><%= message %>
   </i></font>
<p>
      <%	} %>

<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr>
      <td>
         <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
            <tr bgcolor="#eeeeee">
               <td align="center" nowrap><font size="-2" face="verdana"><b>GROUP ID</b></font></td>
               <td align="center" nowrap><font size="-2" face="verdana"><b>GROUP NAME</b></font><img
                  src="images/down.gif" width="8" height="7" border="0" hspace="4"></td>
               <td align="center"><font size="-2" face="verdana"><b>MEMBER COUNT</b></font></td>
               <td align="center"><font size="-2" face="verdana"><b>ADD/REMOVE USERS</b></font></td>
               <td align="center" nowrap><font size="-2" face="verdana"><b>DELETE</b></font></td>
            </tr>
            <% // Get the group list, sort it
               List groupList = new java.util.LinkedList();
               while (groups.hasNext()) {
                  Group g = (Group) groups.next();
                  groupList.add(g);
               }
               Collections.sort(groupList, (o1, o2) -> {
                  Group g1 = (Group) o1;
                  Group g2 = (Group) o2;
                  return g1.getName().toLowerCase().compareTo(g2.getName().toLowerCase());
               });
               boolean hasGroups = !groupList.isEmpty();
            %>
            <% if (!hasGroups) { %>
            <tr bgcolor="#ffffff">
               <td align="center" colspan="5">
                  <font size="-1"><i>No Groups Created</i></font>
               </td>
            </tr>
            <% }
               for (int i = start; (i < groupList.size() && (i < (start + range))); i++) {
                  Group group = (Group) groupList.get(i);
            %>
            <tr bgcolor="#ffffff">
               <td align="center" width="1%"><font size="-1"><%= group.getID() %>
               </font></td>
               <td width="<%= (isSystemAdmin)?"96":"97" %>%"><font size="-1"><a
                  href="editGroup.jsp?group=<%= group.getID() %>"><%= group.getName() %>
               </a><br></font><font size="-2"><%= group.getDescription() %>
               </font></td>
               <td width="1%" align="center"><font size="-1"><%= group.getMemberCount() %>
               </font></td>
               <td align="center" width="1%"
               ><a href="groupMembers.jsp?group=<%= group.getID() %>"
               ><img src="images/button_edit.gif" width="17" height="17" alt="Add or Remove Users..." border="0"
               ></a
               ></td>
               <td align="center" width="1%"
               ><a href="removeGroup.jsp?group=<%= group.getID() %>"
               ><img src="images/button_delete.gif" width="17" height="17" alt="Delete Group..." border="0"
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
      <td width="1%" nowrap><font size="-1"><a href="groups.jsp?start=<%= start-range %>">Previous <%= range %>
         groups</a></font></td>
      <% } else { %>
      <td width="1%" nowrap><font size="-1">&nbsp;</font></td>
      <% } %>

      <% if (start > 0) { %>
      <td width="98%" align="center"><font size="-1"><a href="groups.jsp?start=0">Front Page</a></font></td>
      <% } else { %>
      <td width="98%" nowrap><font size="-1">&nbsp;</font></td>
      <% } %>

      <% if (start + range < groupCount) { %>
      <td width="1%" nowrap><font size="-1"><a href="groups.jsp?start=<%= start+range %>">Next <%= range %>
         groups</a></font></td>
      <% } else { %>
      <td width="1%" nowrap><font size="-1">&nbsp;</font></td>
      <% } %>
   </tr>
</table>

</body>
</html>

