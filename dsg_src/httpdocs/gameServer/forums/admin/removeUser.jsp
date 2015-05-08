
<%
/**
 *	$RCSfile: removeUser.jsp,v $
 *	$Revision: 1.1 $
 *	$Date: 2002/08/16 06:52:22 $
 */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"%>

<%@ include file="global.jsp" %>
 
<%  // Permission check
    if (!isSystemAdmin && !isUserAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }
    
    // get parameters
	long userID = ParamUtils.getLongParameter(request,"user",-1L);
	boolean delete = ParamUtils.getBooleanParameter(request,"delete");
    String cancelButton = ParamUtils.getParameter(request,"cancelButton");
    
    // Cancel if requested
    if ("Cancel".equals(cancelButton)) {
        response.sendRedirect("users.jsp");
        return;
    }
    
    // Get a user manager
	UserManager manager = forumFactory.getUserManager();
    
    // Load the requested user
    User user = manager.getUser(userID);
    
    // Put the forum in the session (is needed by the sidebar)
    session.setAttribute("admin.sidebar.users.currentUserID", ""+userID);
    
    // delete forum if requested
    if (delete) {
        manager.deleteUser(user);
        // save a message in the session
        setOneTimeMessage(session,"admin.users.message","User deleted successfully.");
        // done deleting, so redirect back to the user summary page
		response.sendRedirect("users.jsp");
		return;
	}
%>

<%  // special onload command to load the sidebar
    onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=users';\"";
%>
<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Delete User";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"User Summary", "users.jsp"},
        {title, "removeUser.jsp?user="+userID}
    };
%>
<%@ include file="title.jsp" %>

<p>

<%	// determine if the user to be deleted is the current user
    boolean isCurrentUser = (authToken.getUserID() == user.getID());
    if (isCurrentUser) { %>
	<font size="-1" color="#993300">
	<b>Warning! You are about to delete your OWN user account. Doing this
    is NOT recommended.</b>
	</font>
    <p>
<%	} %>

<form action="removeUser.jsp" name="deleteForm">
<input type="hidden" name="delete" value="true">
<input type="hidden" name="user" value="<%= userID %>">

<font size="-1"><b>Confirm User Deletion</b></font><p>
<ul>
    <font size="-1">
	Warning: This will permanently delete the user. Are
	you sure you really want to do this? (It will <b>not</b> delete the
	messages posted by this user. User posted messages will be marked
	as "anonymous" after user deletion.)
    <p>
    </font>
	<input type="submit" value="Delete User">
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
