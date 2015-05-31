<%
/**
 *	$RCSfile: tabs.jsp,v $
 *	$Revision: 1.9.4.1 $
 *	$Date: 2003/02/03 03:26:38 $
 */
%>

<%@ page import="com.jivesoftware.forum.*" %>

<%@ include file="global.jsp" %>

<%	// Get the "tab" parameter -- this tells us which tab to show as active
    String tab = request.getParameter("tab");
    // Set a default tab value
	if (tab == null) {
        String sessionTab = (String)session.getAttribute("jive.admin.sidebarTab");
        if (sessionTab == null) {
    		tab = "info";
        }
        else {
            tab = sessionTab;
        }
	}

    // Set the content type and character encoding
    response.setContentType("text/html; charset=" + JiveGlobals.getCharacterEncoding());
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html>
<head>
    <title>Jive Forums Admin 3</title>
    <meta http-equiv="content-type" content="text/html; charset=<%= JiveGlobals.getCharacterEncoding() %>">
    <script language="JavaScript" type="text/javascript">
    <!-- // code for window popups
    function helpwin() {
        window.open('helpwin.jsp','newWindow','width=500,height=550,menubar=yes,location=no,personalbar=no,scrollbars=yes,resize=yes');
    }
    //-->
    </script>
    <link rel="stylesheet" href="style/global.css" type="text/css">
</head>

<body bgcolor="#ffffff" background="images/admin-back.gif">

<table class="jive-admin-header" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr>
    <td><a href="main.jsp" target="main"><img src="images/header.gif" width="220" height="45" border="0"></a></td>
</tr>
</table>

<table class="jive-tabs" cellpadding="0" cellspacing="0" border="0">
<tr>
    <%  int tabCount = 1; %>

    <td class="jive-<%= (tab.equals("info"))?"selected-":"" %>tab" width="1%" nowrap>
        <a href="sidebar.jsp?sidebar=info" target="sidebar"
        >General Info</a>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

    <%  if (isSystemAdmin) {
            tabCount++;
    %>
            <td class="jive-<%= (tab.equals("system"))?"selected-":"" %>tab" width="1%" nowrap>
                <a href="sidebar.jsp?sidebar=system" target="sidebar"
                >Global Settings</a>
            </td>
            <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

    <%  } %>

    <%  if (isSystemAdmin || isModerator || isForumAdmin || isCatAdmin) {
            tabCount++;
    %>
            <td class="jive-<%= (tab.equals("forum"))?"selected-":"" %>tab" width="1%" nowrap>
                <a href="sidebar.jsp?sidebar=forum" target="sidebar"
                >Content</a>
            </td>
            <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

    <%  } %>

    <%  if (!isUserGroupAdminDisabled && (isSystemAdmin || isUserAdmin || isGroupAdmin)) {
            tabCount++;
    %>
            <td class="jive-<%= (tab.equals("users"))?"selected-":"" %>tab" width="1%" nowrap>
                <a href="sidebar.jsp?sidebar=users" target="sidebar"
                >Users &amp; Groups</a>
            </td>
            <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

    <%  } %>

    <%  if (isSystemAdmin || isModerator || isForumAdmin || isCatAdmin) {
            tabCount++;
    %>
            <td class="jive-<%= (tab.equals("moderation"))?"selected-":"" %>tab" width="1%" nowrap>
                <a href="sidebar.jsp?sidebar=moderation" target="sidebar"
                >Moderation</a>
            </td>
            <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

    <%  } %>

    <%  if (isSystemAdmin) {
            tabCount++;
    %>
            <td class="jive-<%= (tab.equals("skins"))?"selected-":"" %>tab" width="1%" nowrap>
                <a href="sidebar.jsp?sidebar=skins" target="sidebar"
                >Skin Settings</a>
            </td>
            <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

    <%  } %>

    <%  if (isSystemAdmin && (Version.getEdition()==Version.Edition.PROFESSIONAL || Version.getEdition()==Version.Edition.ENTERPRISE)) {
            tabCount++;
    %>
            <td class="jive-<%= (tab.equals("reports"))?"selected-":"" %>tab" width="1%" nowrap>
                <a href="sidebar.jsp?sidebar=reports" target="sidebar"
                >Reports</a>
            </td>
            <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

    <%  } %>

    <td class="jive-tab-spring" width="<%= (100-(tabCount*2)) %>%" align="right" nowrap>
        Jive Forums Version: <%= Version.getVersionNumber() %>
    </td>
</tr>
<tr>
    <td class="jive-tab-bar" colspan="99">
        <table cellpadding="3" cellspacing="0" border="0" width="100%">
        <tr>
            <td width="98%">
                <table class="jive-tab-section" cellpadding="3" cellspacing="0" border="0">
                <tr>
                    <%  if (tab.equals("info")) { %>
                        <%  if (isPro || isEnt) { %>
                            <td>
                                <a href="yourperms.jsp" target="main">Your Permissions</a>
                            </td>
                        <%  } %>
                        <td>
                            <a href="systeminfo.jsp" target="main">System Info</a>
                        </td>
                    <%  }
                        else if (tab.equals("system")) {
                    %>
                        <td>
                            <a href="cache.jsp" target="main">Cache Settings</a>
                        </td>
                    <%  }
                        else if (tab.equals("forum")) {
                    %>
                        <td>
                            <a href="#" target="main"
                             onclick="parent.frames['main'].location.href='editForum.jsp?forum=' + prompt('Please enter the forum ID','');return false;"
                             >Jump to Forum</a>
                        </td>
                    <%  }
                        else if (tab.equals("users")) {
                    %>
                        <td>
                            <a href="#" target="main"
                             onclick="var u=prompt('Please enter the username or user ID','');if(u!=null){parent.frames['main'].location.href='editUser.jsp?user='+u;}return false;"
                             >Jump to User</a>
                        </td>
                        <td>
                            <a href="#" target="main"
                             onclick="var g=prompt('Please enter the group name or group ID','');if(g!=null){parent.frames['main'].location.href='editGroup.jsp?group='+g;}return false;"
                             >Jump to Group</a>
                        </td>
                    <%  }
                        else if (tab.equals("moderation")) {
                    %>
                        <td>
                            &nbsp;
                        </td>
                    <%  }
                        else if (tab.equals("skins")) {
                    %>
                        <td>
                            &nbsp;
                        </td>
                    <%  }
                        else if (tab.equals("reports")) {
                    %>
                        <td>
                            &nbsp;
                        </td>

                    <%  } %>
                </tr>
                </table>
            </td>
            <td width="1%"
                ><a href="index.jsp?logout=true" title="Click to logout" target="_parent"
                ><img src="images/logout-16x16.gif" width="16" height="16" border="0"
                ></a></td>
            <td nowrap width="1%" class="jive-tab-logout">
                <a href="index.jsp?logout=true" title="Click to logout" target="_parent">Logout</a>
                <span title="You are logged in as '<%= pageUser.getUsername() %>'">
                [<b><%= pageUser.getUsername() %></b>]
                </span>
            </td>
        </tr>
        </table>
    </td>
</tr>
</table>
<table cellpadding="0" cellspacing="0" border="0" width="100%" bgcolor="#cccccc">
<tr><td><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>
<table cellpadding="0" cellspacing="0" border="0" width="100%" bgcolor="#eeeeee">
<tr><td><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>

</body>
</html>
