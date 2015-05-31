
<%
/**
 *	$RCSfile: removeGroup.jsp,v $
 *	$Revision: 1.1 $
 *	$Date: 2002/08/16 06:52:22 $
 */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"%>

<%@ include file="global.jsp" %>
 
<%  // Security check
    if (!isSystemAdmin && !isGroupAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }
    
    // get parameters
	long groupID = ParamUtils.getLongParameter(request,"group",-1L);
	boolean delete = ParamUtils.getBooleanParameter(request,"delete");
    String cancelButton = ParamUtils.getParameter(request,"cancelButton");
    
    // Cancel if requested
    if ("Cancel".equals(cancelButton)) {
        response.sendRedirect("groups.jsp");
        return;
    }
    
    // Get a user manager
	GroupManager groupManager = forumFactory.getGroupManager();
    
    // Load the requested user
    Group group = groupManager.getGroup(groupID);
    
    // Put the forum in the session (is needed by the sidebar)
    session.setAttribute("admin.sidebar.groups.currentGroupID", ""+groupID);
    
    // delete forum if requested
    if (delete) {
        groupManager.deleteGroup(group);
        // save a message in the session
        setOneTimeMessage(session,"admin.groups.message","Group deleted successfully.");
        // done deleting, so redirect back to the user summary page
		response.sendRedirect("groups.jsp");
		return;
	}
%>

<%  // special onload command to load the sidebar
    onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=users';\"";
%>
<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Delete Group";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Group Summary", "groups.jsp"},
        {title, "removeGroup.jsp?group="+groupID}
    };
%>
<%@ include file="title.jsp" %>

<p>

<form action="removeGroup.jsp" name="deleteForm">
<input type="hidden" name="delete" value="true">
<input type="hidden" name="group" value="<%= groupID %>">

<font size="-1"><b>Confirm Group Deletion</b></font><p>
<ul>
    <font size="-1">
	Warning: This will permanently delete the group. Are
	you sure you really want to do this? (It will <b>not</b> delete the
	users in this group, just the group itself.)
    <p>
    </font>
	<input type="submit" value="Delete Group">
	&nbsp;
	<input type="submit" name="cancelButton" value="Cancel">
</ul>
</form>

<script language="JavaScript" type="text/javascript">
<!--
// activate the "cancel" button -- if the user accidentally hits enter or
// space on this page, the default action would be to cancel, not delete
// the user ;)
document.deleteForm.cancelButton.focus();
//-->
</script>


</body>
</html>
