<%
/**
 * $RCSfile: accountbox.jsp,v $
 * $Revision: 1.12 $
 * $Date: 2002/12/20 23:05:53 $
 */
%>
<%@page import="com.jivesoftware.base.*, com.jivesoftware.forum.*" %>

<%  AuthToken accountAuthToken = null;
    try {
        accountAuthToken = AuthFactory.getAuthToken(request,response);
    }
    catch (Exception ignored) {}
    if (accountAuthToken != null) { %>

<span class="jive-account-box">
<table class="jive-box" cellpadding="3" cellspacing="0" border="0" width="200">
    <tr>
        <td width="1%"><a href="settings!default.jspa"><img src="images/settings-16x16.gif" width="16" height="16" border="0"></a></td>
        <td width="99%">
            <%-- Your Control Panel --%>
            <a href="settings!tab.jspa">My Settings</a>
        </td>
    </tr>
    <tr>
        <td width="1%"><a href="settings!default.jspa"><img src="images/watch-16x16.gif" width="16" height="16" border="0"></a></td>
        <td width="99%">
            <%-- Watches --%>
            <a href="editwatches!default.jspa">My Watches</a>
        </td>
    </tr>
</table>
</span>

<%  } %>