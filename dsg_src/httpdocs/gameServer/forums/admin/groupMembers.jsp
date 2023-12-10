<%
   /**
    *	$RCSfile: groupMembers.jsp,v $
    *	$Revision: 1.4.4.2 $
    *	$Date: 2003/03/26 00:12:26 $
    */
%>

<%@ page import="java.util.*,
                 java.net.URLEncoder,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils" %>

<%! private List getMemberIDList(HttpServletRequest request, String type, int count) {
   java.util.List memberIDList = new java.util.ArrayList(count);
   for (int i = 0; i < count; i++) {
      long id = -1L;
      try {
         id = Long.parseLong(request.getParameter(type + i));
         memberIDList.add(Long.valueOf(id));
      } catch (Exception ignored) {
      }
   }
   return memberIDList;
}
%>

<%@ include file="global.jsp" %>

<% // Security check
   if (!isSystemAdmin && !isGroupAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // get parameters
   long groupID = ParamUtils.getLongParameter(request, "group", -1L);
   boolean add = ParamUtils.getBooleanParameter(request, "add");
   String userList = ParamUtils.getParameter(request, "userList");
   boolean doTopics = request.getParameter("doTopics") != null;
   boolean doSettings = request.getParameter("doSettings") != null;
   boolean doRemove = request.getParameter("doRemove") != null;

   // Get user and group managers
   UserManager userManager = forumFactory.getUserManager();
   GroupManager groupManager = forumFactory.getGroupManager();

   // Load the group
   Group group = groupManager.getGroup(groupID);

   // Total number of members in this group
   int memberCount = group.getMemberCount();

   if (doTopics || doSettings) {
      List memberIDList = null;
      StringBuffer queryString = new StringBuffer(memberCount * 5);
      if (doTopics) {
         memberIDList = getMemberIDList(request, "topics", memberCount);
         queryString.append("groupReadTracker.jsp?group=").append(group.getID());
      } else if (doSettings) {
         memberIDList = getMemberIDList(request, "settings", memberCount);
         queryString.append("memberWatches.jsp?group=").append(group.getID());
      }
      for (int i = 0; i < memberIDList.size(); i++) {
         queryString.append("&user=").append(((Long) memberIDList.get(i)).longValue());
      }
      response.sendRedirect(queryString.toString());
      return;
   }

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
            group.addMember(user);
         }
      }
      // done, so redirect
      response.sendRedirect("groupMembers.jsp?group=" + groupID);
      return;
   }

   if (doRemove) {
      List memberIDList = getMemberIDList(request, "remove", memberCount);
      for (int i = 0; i < memberIDList.size(); i++) {
         long id = ((Long) memberIDList.get(i)).longValue();
         User member = userManager.getUser(id);
         group.removeMember(member);
      }
      // done, so redirect
      response.sendRedirect("groupMembers.jsp?group=" + groupID);
      return;
   }

   // Iterator of members
   Iterator members = group.members();
%>

<% // special onload command to load the sidebar
   onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=users';\"";
%>
<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Group Members";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Groups Summary", "groups.jsp"},
        {title, "groupMembers.jsp?group="+groupID}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Add users to this group using the form below.
   </font>

<p>

   <font size="-1"><b>Add Members to this Group</b></font>
<p>
<form action="groupMembers.jsp">
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
            <td><input type="submit" value="Add Users To Group"></td>
         </tr>
      </table>
   </ul>
</form>
<p>

   <script language="JavaScript" type="text/javascript">
      <!--
      function confirmDel() {
         return confirm('Are you sure you want to remove the user(s)?');
      }

      function selAll(elName, count) {
         for (var i = 0; i < count; i++) {
            var cb = eval('document.memberForm.' + elName + i);
            cb.checked = true;
         }
         return false;
      }

      //-->
   </script>

   <font size="-1"><b>Members of this group (<%= memberCount %>)</b></font>
<p>
<ul>

   <form action="groupMembers.jsp" name="memberForm">
      <input type="hidden" name="group" value="<%= group.getID() %>">

      <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="550">
         <tr>
            <td>
               <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
                  <tr bgcolor="#eeeeee">
                     <td align="center"><font size="-2" face="verdana">&nbsp;</font></td>
                     <td align="center" nowrap><font size="-2" face="verdana"><b>USERNAME</b></font><img
                        src="images/down.gif" width="8" height="7" border="0" hspace="4"></td>
                     <td align="center" nowrap><font size="-2" face="verdana"><b>NAME</b></font></td>
                     <td align="center" nowrap><font size="-2" face="verdana">&nbsp;<b>TOPICS &amp;&nbsp;<br>&nbsp;WATCHES</b>&nbsp;</font>
                     </td>
                     <td align="center" nowrap><font size="-2" face="verdana">&nbsp;<b>REMOVE</b>&nbsp;</font></td>
                  </tr>
                  <% boolean hasMembers = members.hasNext();
                     if (!hasMembers) { %>
                  <tr bgcolor="#ffffff">
                     <td colspan="5" align="center"><font size="-1"><i>No Members</i></font></td>
                  </tr>
                  <% }
                     int count = 0;
                     List memberList = new java.util.LinkedList();
                     while (members.hasNext()) {
                        User member = (User) members.next();
                        memberList.add(member);
                     }
                     Collections.sort(memberList, (o1, o2) -> {
                        User u1 = (User) o1;
                        User u2 = (User) o2;
                        return u1.getUsername().toLowerCase().compareTo(u2.getUsername().toLowerCase());
                     });
                     for (int i = 0; i < memberList.size(); i++) {
                        count++;
                        User member = (User) memberList.get(i);
                        String name = member.getName();
                        // Get the user's presence object
                        Presence presence = forumFactory.getPresenceManager().getPresence(member);
                  %>
                  <tr bgcolor="#ffffff">
                     <td width="1%"><font size="-1">&nbsp;<%= count %>&nbsp;</font></td>
                     <td width="1%" nowrap><font size="-1"><a
                        href="editUser.jsp?user=<%= member.getID() %>"><%= member.getUsername() %>
                     </a></font></td>
                     <td width="95%"><font
                        size="-1">&nbsp;<%= (name != null) ? name : "&nbsp;" %>&nbsp;&nbsp;&nbsp;</font></td>
                     <td width="1%" align="center" bgcolor="#ffffff" nowrap>
                        <input type="checkbox" name="settings<%= (count-1) %>" value="<%= member.getID() %>">
                     </td>
                     <td width="1%" align="center" bgcolor="#eeeeee" nowrap>
                        <input type="checkbox" name="remove<%= (count-1) %>" value="<%= member.getID() %>">
                     </td>
                  </tr>
                  <% } %>
                  <% if (hasMembers) { %>
                  <tr bgcolor="#ffffff">
                     <td colspan="3"><font size="-1">&nbsp;</font></td>
                     <td align="center" bgcolor="#eeeeee" nowrap>
                        <font size="-2" face="verdana">
                           [<a href="#" onclick="return selAll('settings',<%= count %>);">Select All</a>]<br>
                        </font>
                        <input type="submit" name="doSettings" value="View">
                     </td>
                     <td align="center" bgcolor="#eeeeee" nowrap>
                        <font size="-2" face="verdana">
                           [<a href="#" onclick="return selAll('remove',<%= count %>);">Select All</a>]<br>
                        </font>
                        <input type="submit" name="doRemove" value="Remove" onclick="return confirmDel();">
                     </td>
                  </tr>
                  <% } %>
               </table>
            </td>
         </tr>
      </table>

   </form>

</ul>
<p>

   </body>
   </html>



