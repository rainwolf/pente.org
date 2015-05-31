<%--
  - $RCSfile: sidebar.jsp,v $
  - $Revision: 1.15.2.2 $
  - $Date: 2003/03/20 23:02:45 $
  -
  - Copyright (C) 2002-2003 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software. Use is subject to license terms.
--%>

<%@ page import="com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils" %>

<%@ include file="global.jsp" %>

<%	// Get parameters

	// "sidebar" (tells which sidebar to display)
	String sidebar = ParamUtils.getParameter(request,"sidebar");
    if (sidebar == null) {
        String sessionSidebar = (String)session.getAttribute("jive.admin.sidebarTab");
        if (sessionSidebar == null) {
            sidebar = "info";
        }
        else {
            sidebar = sessionSidebar;
        }
    }
    else {
        session.setAttribute("jive.admin.sidebarTab",sidebar);
    }

    // Set the content type and character encoding
    response.setContentType("text/html; charset=" + JiveGlobals.getCharacterEncoding());
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html>
<head>
	<title>Jive Forums 3 Admin</title>
    <meta http-equiv="content-type" content="text/html; charset=<%= JiveGlobals.getCharacterEncoding() %>">
    <link rel="stylesheet" href="./style/global.css" type="text/css">
</head>

<body bgcolor="#F1F1ED"
 onload="parent.frames['header'].location.href='tabs.jsp?tab=<%= sidebar %>';"
 >

<table bgcolor="#bbbbbb" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>
<table bgcolor="#dddddd" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>
<table bgcolor="#eeeeee" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="images/blank.gif" width="1" height="5" border="0"></td></tr>
</table>

<%  if ("info".equals(sidebar)) { %>

    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            General Information
        </th>
    </tr>
    <%  if (isPro || isEnt) { %>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="yourperms.jsp" target="main">Your Permissions</a>
        </td>
    </tr>
    <%  } %>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="systeminfo.jsp" target="main">System Info</a>
        </td>
    </tr>
    <%  if (isSystemAdmin) { %>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="datasource.jsp" target="main">Database Info</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="querystats.jsp" target="main">Query Stats</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="logviewer.jsp" target="main">Log Viewer</a>
        </td>
    </tr>
    <%  } %>
    </table>

<%  } else if (isSystemAdmin && "system".equals(sidebar)) { %>


    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            System Settings
        </th>
    </tr>
    <%  if (isPro || isEnt) { %>
        <tr>
            <td class="bullet">&#149;</td>
            <td><a href="perms.jsp?mode=<%= FORUM_MODE %>&permGroup=<%= ADMIN_GROUP %>" target="main">Admins/Moderators</a></td>
        </tr>
    <%  } %>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="cache.jsp" target="main">Cache Settings</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="data.jsp" target="main">Data Import/Export</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="email.jsp" target="main">Email Settings</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="filters.jsp" target="main">Global Filters</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="interceptors.jsp" target="main">Global Interceptors</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="perms.jsp?mode=<%= FORUM_MODE %>&permGroup=<%= CONTENT_GROUP %>" target="main">Global Permissions</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="locale.jsp" target="main">Locale Settings</a></td>
    </tr>
    </table>

    <br>

    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            Global Features
        </th>
    </tr>
    <%  if (isPro || isEnt) { %>
        <tr>
            <td class="bullet">&#149;</td>
            <td><a href="attachSettings.jsp" target="main">Attachment Settings</a></td>
        </tr>
        <tr>
            <td class="bullet">&#149;</td>
            <td><a href="archiveSettings.jsp" target="main">Archiving Settings</a></td>
        </tr>
        <tr>
            <td class="bullet">&#149;</td>
            <td><a href="spellCheck.jsp" target="main">Spell Check Settings</a></td>
        </tr>
    <%  } %>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="searchSettings.jsp" target="main">Search Settings</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="editPasswordReset.jsp" target="main">Password Reset</a></td>
    </tr>
    <%  if ((Version.getEdition()==Version.Edition.LITE)) { %>
        <tr>
            <td class="bullet">&#149;</td>
            <td><a href="stats.jsp" target="main">Basic Stats</a></td>
        </tr>
    <%  } %>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="editWatches.jsp" target="main">Watch Settings</a></td>
    </tr>
    </table>

    <br>

    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            Rewards
        </th>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="manageRewards.jsp" target="main">Manage Rewards</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="rewardReports.jsp" target="main">Reward Reports</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="editRewards.jsp" target="main">Reward Settings</a></td>
    </tr>
    </table>

<%  } else if ((isSystemAdmin || isGroupAdmin || isUserAdmin) && "users".equals(sidebar)) {

        // Get a group object out of the session. The existence of this object
        // means we need to display more links specific to the group
        Group group = null;
        try {
            long groupID = Long.parseLong((String)session.getAttribute("admin.sidebar.groups.currentGroupID"));
            group = forumFactory.getGroupManager().getGroup(groupID);
        }
        catch (Exception e) {}
        boolean showGroupLinks = (group != null);

        // Only show user stuff to system admins or user admins
        if (isSystemAdmin || isUserAdmin) {
            // Get a user object out of the session. The existence of this object
            // means we need to display more links specific to the user
            User user = null;
            try {
                long userID = Long.parseLong((String)session.getAttribute("admin.sidebar.users.currentUserID"));
                user = forumFactory.getUserManager().getUser(userID);
            }
            catch (Exception e) {}
            boolean showUserLinks = (user != null);
%>
    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            Users
        </th>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="users.jsp" target="main">User Summary</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="createUser.jsp" target="main">Create User</a></td>
    </tr>
    </table>

    <br>

    <%  if (showUserLinks) {
            long userID = user.getID();
            String userName = user.getUsername();
            if (userName.length() > 20) {
            	userName = userName.substring(0, 20);
            	userName += "...";
            }
    %>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr><td rowspan="99" width="1%">&nbsp;</td>
        <td colspan="3" width="99%">

        <table bgcolor="#a5abc0" cellpadding="1" cellspacing="0" border="0" width="100%">
        <tr><td>
        <table bgcolor="#d1d9e2" cellpadding="3" cellspacing="0" border="0" width="100%">
        <tr>
            <td colspan="3"><b>User: <%= userName %></b></td>
        </tr>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="editUser.jsp?user=<%= userID %>" target="main">Edit User</a></td>
        </tr>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="editUserProps.jsp?user=<%= userID %>" target="main">Extended Properties</a></td>
        </tr>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="removeUser.jsp?user=<%= userID %>" target="main">Delete User</a></td>
        </tr>
        </table>
        </td></tr>
        </table>

        </td>
    </tr>
    </table><p>
    <%      }
        }
    %>

    <%  if (isSystemAdmin || isGroupAdmin) { %>

        <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
        <tr>
            <th colspan="2">
                Groups
            </th>
        </tr>
        <tr>
            <td class="bullet">&#149;</td>
            <td><a href="groups.jsp?start=0" target="main">Group Summary</a></td>
        </tr>
        <tr>
            <td class="bullet">&#149;</td>
            <td><a href="createGroup.jsp" target="main">Create Group</a></td>
        </tr>
        </table>

        <br>

    <%  } %>

    <%  if (showGroupLinks) {
            long groupID = group.getID();
    %>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr><td rowspan="99" width="1%">&nbsp;</td>
        <td colspan="3" width="99%">

        <table bgcolor="#a5abc0" cellpadding="1" cellspacing="0" border="0" width="100%">
        <tr><td>
        <table bgcolor="#d1d9e2" cellpadding="3" cellspacing="0" border="0" width="100%">
        <tr>
            <td colspan="3"><b>Group: <%= group.getName() %></b></td>
        </tr>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="editGroup.jsp?group=<%= groupID %>" target="main">Group Settings</a></td>
        </tr>
        <%  if (isSystemAdmin) { %>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="groupAdmins.jsp?group=<%= groupID %>" target="main">Group Admins</a></td>
        </tr>
        <%  } %>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="groupMembers.jsp?group=<%= groupID %>" target="main">Group Members</a></td>
        </tr>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="editGroupProps.jsp?group=<%= groupID %>" target="main">Extended Properties</a></td>
        </tr>
        <%  if (isSystemAdmin) { %>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="removeGroup.jsp?group=<%= groupID %>" target="main">Delete Group</a></td>
        </tr>
        <%  } %>
        </table>
        </td></tr>
        </table>

        </td>
    </tr>
    </table><p>
    <%  } %>

    <%  if (isSystemAdmin) { %>

        <br>
        <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
        <tr>
            <th colspan="2">
                Administration
            </th>
        </tr>
        <tr>
            <td class="bullet" valign="top">&#149;</td>
            <td><a href="disable.jsp" target="main">Disable User &amp; Group Admin</a></td>
        </tr>
        </table>
        <br>

    <%  } %>

<%  }
    else if ((isSystemAdmin || isCatAdmin || isForumAdmin || isModerator)
                && "forum".equals(sidebar))
    {
        // Get a forum object out of the session. The existence of this object
        // means we need to display more links specific to the forum
        Forum forum = null;
        String forumName = null;
        try {
            long forumID = Long.parseLong((String)session.getAttribute("admin.sidebar.forums.currentForumID"));
            forum = forumFactory.getForum(forumID);
            forumName = forum.getName();
            if (forumName.length() > 20) {
            	forumName = forumName.substring(0, 20) + "...";
            }
        }
        catch (Exception e) {}
        boolean showForumLinks = (forum != null);
%>
    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            Categories &amp; Forums
        </th>
    </tr>
    <%  if (!isSystemAdmin && !isForumAdmin && !isCatAdmin) { %>
        <tr>
            <td class="bullet">&#149;</td>
            <td><a href="forumContent.jsp" target="main">Category Summary</a></td>
        </tr>
    <%  } else { %>
        <tr>
            <td class="bullet">&#149;</td>
            <td><a href="forums.jsp" target="main">Category Summary</a></td>
        </tr>
    <%  } %>
    </table>

    <br><br>

    <%  if (showForumLinks) {
            long forumID = forum.getID();
    %>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr><td rowspan="99" width="1%">&nbsp;</td>
        <td colspan="3" width="99%">

        <table bgcolor="#a5abc0" cellpadding="1" cellspacing="0" border="0" width="100%">
        <tr><td>
        <table bgcolor="#d1d9e2" cellpadding="3" cellspacing="0" border="0" width="100%">
        <tr>
            <td colspan="3">
                <b>Forum Options</b>
                <font size="-2" face="verdana">
                    <br>
                    <%= forumName %>

            </td>
        </tr>
        <%  if (isSystemAdmin || isForumAdmin) { %>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="editForum.jsp?forum=<%= forumID %>" target="main">Forum Settings</a></td>
        </tr>
        <%  } %>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="forumContent.jsp?forum=<%= forumID %>" target="main">Manage Content</a></td>
        </tr>
        <%  if (isSystemAdmin || isForumAdmin) { %>

            <%  if (isPro || isEnt) { %>
            <tr bgcolor="#eeeeee">
                <td>&nbsp;</td>
                <td>&#149;</td>
                <td><a href="archiveSettings.jsp?forum=<%= forumID %>" target="main">Archiving Settings</a></td>
            </tr>
            <%  } %>

        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="editForumProps.jsp?forum=<%= forumID %>" target="main">Extended Properties</a></td>
        </tr>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td>
                <a href="perms.jsp?forum=<%= forumID %>&mode=<%= FORUM_MODE %>&permGroup=<%= CONTENT_GROUP %>"
                 target="main">Permissions</a></td>
        </tr>
        <%  if (isPro || isEnt) { %>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td>
                <a href="perms.jsp?forum=<%= forumID %>&mode=<%= FORUM_MODE %>&permGroup=<%= ADMIN_GROUP %>"
                 target="main">Admins/Moderators</a></td>
        </tr>
        <%  } %>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="filters.jsp?forum=<%= forumID %>" target="main">Message Filters</a></td>
        </tr>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="interceptors.jsp?forum=<%= forumID %>" target="main">Message Interceptors</a></td>
        </tr>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="forumModeration.jsp?forum=<%= forumID %>" target="main">Moderation</a></td>
        </tr>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="gateways.jsp?forum=<%= forumID %>" target="main">Gateways</a></td>
        </tr>
        <tr bgcolor="#eeeeee">
            <td>&nbsp;</td>
            <td>&#149;</td>
            <td><a href="mergeForums.jsp?forum=<%= forumID %>" target="main">Merge Data</a></td>
        </tr>
        <%  } %>
        </table>
        </td></tr>
        </table>

        </td>
    </tr>
    </table><p>
    <%  } %>

<%  } else if ((isSystemAdmin || isForumAdmin || isCatAdmin || isModerator)
                    && "moderation".equals(sidebar))
        {
%>
    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            Moderation
        </th>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="moderation.jsp" target="main">Moderation Summary</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="pending.jsp" target="main">Review Pending Messages</a></td>
    </tr>
    </table>
    <br>

<%  } else if (isSystemAdmin && "skins".equals(sidebar)) { %>

    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            Skin Settings
        </th>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="skin.jsp?mode=fonts" target="main">Fonts</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="skin.jsp?mode=colors" target="main">Colors</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="skin.jsp?mode=forumtext" target="main">Forum Text</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="skin.jsp?mode=headerandfooter" target="main">Header &amp; Footer</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="skin.jsp?mode=features" target="main">Features</a></td>
    </tr>
    </table>
    <br>

<%  } else if (isSystemAdmin && "reports".equals(sidebar)) { %>

    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            Reports
        </th>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="stats.jsp" target="main">Basic Stats</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="reports.jsp" target="main">Report Configuration</a></td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td><a href="runReports.jsp" target="main">Run Reports</a></td>
    </tr>
    </table>
    <br>

<%  } %>

<br><br>

</body>
</html>

