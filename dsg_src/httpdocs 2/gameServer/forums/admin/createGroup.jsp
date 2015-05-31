
<%
/**
 *	$RCSfile: createGroup.jsp,v $
 *	$Revision: 1.1 $
 *	$Date: 2002/08/16 06:52:22 $
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
	String groupName = ParamUtils.getParameter(request,"groupName");
	String groupDescription = ParamUtils.getParameter(request,"groupDescription");
	boolean create = ParamUtils.getBooleanParameter(request,"create");
    
	// global error variables
	String errorMessage = "";
	boolean errorGroupName = (groupName == null);
	boolean errorGroupAlreadyExists = false;
	boolean errors = (errorGroupName);
    
	GroupManager manager = forumFactory.getGroupManager();
    
	// create the group
	if (!errors && create) {
		try {
			Group newGroup = manager.createGroup(groupName);
			if (groupDescription != null) {
				newGroup.setDescription(groupDescription);
			}
		}
		catch (GroupAlreadyExistsException gaee) {
			errorGroupAlreadyExists = true;
		}
	}
    
	// error check
	errors = (errorGroupName || errorGroupAlreadyExists);
    
	// set error messages
	if (errors) {
		if (errorGroupName) {
			errorMessage = "Please specify a group name.";
		}
		else if (errorGroupAlreadyExists) {
			errorMessage = "This group already exists, please choose "
				+ "a different name";
		}
		else {
			errorMessage = "A general error occured while creating a group.";
		}
	}
    
	// Ff a user was successfully created, say so and return (to stop the 
	// jsp from executing
	if (!errors) {
		response.sendRedirect("groups.jsp");
		return;
	}
%>


<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Create Group";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "createGroup.jsp"}
    };
%>
<%@ include file="title.jsp" %>

<font size="-1">
This creates a group with no permissions, no admins, and no users. Once you create
this group, you should edit its properties.
</font>
<p>

<%  if (create && errors) { %>
    <font size="-1" color="#ff0000">
    <%= errorMessage %><p>
    </font>
<%  } %>

<form action="createGroup.jsp" method="post" name="f">
<input type="hidden" name="create" value="true">

<font size="-1"><b>Group Name</b></font>
<ul>
    <input type="text" name="groupName" size="30" maxlength="100"
		 value="<%= (groupName!=null)?groupName:"" %>">
</ul>

<font size="-1"><b>Group Description</b> <i>(optional)</i></font>
<ul>
    <textarea name="groupDescription" wrap="virtual" cols="40" rows="5"
 		><%= (groupDescription!=null)?groupDescription:"" %></textarea>
</ul>

<input type="submit" value="Create Group">
</form>

<script language="JavaScript" type="text/javascript">
<!--
document.f.groupName.focus();
//-->
</script>

</body>
</html>

