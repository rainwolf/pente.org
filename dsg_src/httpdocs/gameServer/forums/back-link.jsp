<%
/**
 * $RCSfile: back-link.jsp,v $
 * $Revision: 1.5 $
 * $Date: 2002/12/20 05:25:43 $
 */
%>

<%@ page import="com.jivesoftware.base.action.util.RedirectAction" %>

<%  String previousURL = RedirectAction.getPreviousURL(request);
    if (previousURL != null) {
%>
    <table cellpadding="3" cellspacing="2" border="0">
    <tr>
        <td><img src="images/back-to-16x16.gif" width="16" height="16" border="0"></td>
        <td>
            <%-- Go Back --%>
            <a href="<%= previousURL %>"><jive:i18n key="global.go_back" /></a>
        </td>
    </tr>
    </table>
<%  } %>