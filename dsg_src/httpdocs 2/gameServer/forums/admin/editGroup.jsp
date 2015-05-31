
<%
/**
 *	$RCSfile: editGroup.jsp,v $
 *	$Revision: 1.2 $
 *	$Date: 2002/11/05 23:03:34 $
 */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
    errorPage="error.jsp"
%>
 
<%@ include file="global.jsp" %>
 
<%	// Security check
    if (!isSystemAdmin && !isGroupAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }
    
    // get parameters
	long groupID = ParamUtils.getLongParameter(request,"group",-1L);
    String nameParam = ParamUtils.getParameter(request,"group");
	String name = ParamUtils.getParameter(request,"name");
	String description = ParamUtils.getParameter(request,"description",true);
	boolean edit = ParamUtils.getBooleanParameter(request,"edit");
    
	// Get a group manager
	GroupManager groupManager = forumFactory.getGroupManager();
    
    // Load the specified group
	Group group = null;
    try {
        group = groupManager.getGroup(groupID);
        name = group.getName();
    }
    catch (Exception e) {
        try {
            group = groupManager.getGroup(nameParam);
            groupID = group.getID();
        }
        catch (Exception e2) {
            throw new GroupNotFoundException("Failed to load specified group.");
        }
    }

    // Put the forum in the session (is needed by the sidebar)
    session.setAttribute("admin.sidebar.groups.currentGroupID", ""+groupID);
    
    // Total number of groups
    int groupCount = groupManager.getGroupCount();
    
	if (edit) {
        group.setName(name);
        if (description != null) {
            group.setDescription(description);
        }
        // done editing, so redirect
        response.sendRedirect("groups.jsp");
        return;
	}
    
    name = group.getName();
    description = group.getDescription();
    
    // Remove the user in the session (if we come to this page, the sidebar
    // shouldn't show the specific user options).
    session.removeAttribute("admin.sidebar.users.currentUserID");
%>

<%  // special onload command to load the sidebar
    onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=users';\"";
%>
<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Group Settings";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Groups Summary", "groups.jsp"},
        {title, "editGroup.jsp?group="+groupID}
    };
%>
<%@ include file="title.jsp" %>

<font size="-1">
Edit group properties using the form below.
</font>

<form action="editGroup.jsp" method="post">
<input type="hidden" name="group" value="<%= groupID %>">
<input type="hidden" name="edit" value="true">

<font size="-1"><b>Edit Group Fields</b></font>
<ul>
    <table cellpadding="3" cellspacing="0" border="0">
    <tr>
    	<td><font size="-1">Group ID:</font></td>
    	<td><font size="-1"><%= group.getID() %></font></td>
    </tr>
    <tr>
    	<td><font size="-1">Name:</font></td>
    	<td><input type="text" name="name" value="<%= (name!=null)?name:"" %>"></td>
    </tr>
    <tr>
    	<td valign="top"><font size="-1">Description:</font></td>
    	<td><textarea cols="30" rows="5" name="description"><%= (description!=null)?description:"" %></textarea></td>
    </tr>
    <tr>
        <td><font size="-1">&nbsp;</font></td>
        <td><p><input type="submit" value="Save Changes"></td>
    </table>
</ul>
</form>


</body>
</html>

