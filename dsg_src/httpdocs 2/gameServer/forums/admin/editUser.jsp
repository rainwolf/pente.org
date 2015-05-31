
<%
/**
 *	$RCSfile: editUser.jsp,v $
 *	$Revision: 1.3 $
 *	$Date: 2002/11/05 23:03:34 $
 */
%>

<%@ page import="java.util.*,
                 java.net.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
	errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>
 
<%	// Permission check
    if (!isSystemAdmin && !isUserAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }
    
    // get parameters
	
	boolean save = ParamUtils.getBooleanParameter(request,"save");
	String username = ParamUtils.getParameter(request,"user");
    long userID = ParamUtils.getLongParameter(request,"user",-1L);
	String name = ParamUtils.getParameter(request,"name",true);
	String email = ParamUtils.getParameter(request,"email");
	boolean isNameVisible = ParamUtils.getBooleanParameter(request,"isNameVisible");
	boolean isEmailVisible = ParamUtils.getBooleanParameter(request,"isEmailVisible");
	String password = ParamUtils.getParameter(request,"password");
	String confirmPassword = ParamUtils.getParameter(request,"confirmPassword");
    boolean changePassword = ParamUtils.getBooleanParameter(request,"changePassword");
    
    // Get a user manager to get and set user properties
	UserManager userManager = forumFactory.getUserManager();
    
    // Load the user
    User user = null;
    try {
        user = userManager.getUser(userID);
        username = user.getUsername();
    }
    catch (Exception e) {
        try {
            user = userManager.getUser(username);
            userID = user.getID();
        }
        catch (Exception e2) {
            throw new UserNotFoundException("Failed to load specified user.");
        }
    }

    // Put the forum in the session (is needed by the sidebar)
    session.setAttribute("admin.sidebar.users.currentUserID", ""+userID);
    
    // Do error checking
	boolean errors = false;
    String errorMessage = "";
    if (save && email == null) {
        errors = true;
        errorMessage = "Email field can't be blank.";
    }
    
    // save user changes if necessary
	if (!errors && save) {
		if (name != null) {
			user.setName(name);
		}
        user.setEmail(email);
		user.setNameVisible(isNameVisible);
		user.setEmailVisible(isEmailVisible);
		
		// done, so redirect
		response.sendRedirect("editUser.jsp?user="+userID);
		return;
	}
    
    // check for password errors
    if (changePassword) {
        if (password == null) {
            errors = true;
        }
        if (confirmPassword == null) {
            errors = true;
        }
        if (!errors && !password.equals(confirmPassword)) {
            errors = true;
        }
        if (errors) {
            errorMessage = "Invalid new password";
        }
        else {
            // no errors, so set new password
            user.setPassword(password);
            // done, so set a success message, then redirect
            setOneTimeMessage(session,"admin.users.message","Password changed successfully.");
            response.sendRedirect("editUser.jsp?user="+userID);
            return;
        }
    }
    
    // user properties
    username = user.getUsername();
	name = user.getName();
	email = user.getEmail();
	isNameVisible = user.isNameVisible();
	isEmailVisible = user.isEmailVisible();
    
    // Extended user properties
	Iterator userProperties = user.getPropertyNames();
%>

<%  // special onload command to load the sidebar
    onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=users';\"";
%>
<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Edit User";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"User Summary", "users.jsp"},
        {title, "editUser.jsp?user="+userID}
    };
%>
<%@ include file="title.jsp" %>

<font size="-1">
Set the data for the user below.
</font>

<p>

<%  if (errors) { %>
    <font size="-1"><i><%= errorMessage %></i></font>
    <p>
<%  } %>

<%  String message = getOneTimeMessage(session,"admin.users.message");
    if (message != null) {
%>
    <font size="-1"><i><%= message %></i></font>
<%  }
%>

<form action="editUser.jsp">
<input type="hidden" name="save" value="true">
<input type="hidden" name="user" value="<%= userID %>">

<font size="-1"><b>Edit User Fields</b></font><p>
<ul>
    <table cellpadding="3" cellspacing="0" border="0">
    <tr>
    	<td><font size="-1">User ID:</font></td>
    	<td><font size="-1"><%= userID %></font></td>
    </tr>
    <tr>
    	<td><font size="-1">Username:</font></td>
    	<td><font size="-1"><%= username %></font></td>
    </tr>
    <tr>
    	<td><font size="-1">Name:</font></td>
    	<td>
    		<input type="text" name="name" value="<%= (name!=null)?name:"" %>">
    	</td>
    </tr>
    <tr>
    	<td><font size="-1">Email:</font></td>
    	<td>
    		<input type="text" name="email" value="<%= (email!=null)?email:"" %>">
    	</td>
    </tr>
    <tr>
    	<td><font size="-1">Name visible:</font></td>
    	<td>
    		<input type="radio" name="isNameVisible" value="true" id="rb01"<%= isNameVisible?" checked":"" %>>
    		<label for="rb01"><font size="-1">Yes</font></label>
            &nbsp;
    		<input type="radio" name="isNameVisible" value="false" id="rb02"<%= !isNameVisible?" checked":"" %>>
    		<label for="rb02"><font size="-1">No</font></label>
    	</td>
    </tr>
    <tr>
    	<td><font size="-1">Email visible:</font></td>
    	<td>
    		<input type="radio" name="isEmailVisible" value="true" id="rb03"<%= isEmailVisible?" checked":"" %>>
    		<label for="rb03"><font size="-1">Yes</font></label>
            &nbsp;
    		<input type="radio" name="isEmailVisible" value="false" id="rb04"<%= !isEmailVisible?" checked":"" %>>
    		<label for="rb04"><font size="-1">No</font></label>
    	</td>
    </tr>
    <tr>
    	<td colspan="2"><br><input type="submit" value="Save Changes"></td>
    </tr>
    </table>
</ul>
</form>

<form action="editUser.jsp" method="post">
<input type="hidden" name="user" value="<%= userID %>">
<input type="hidden" name="changePassword" value="true">
<font size="-1"><b>Change Password</b></font><p>
<ul>
    <table cellpadding="3" cellspacing="0" border="0">
    <tr>
    	<td><font size="-1">New Password:</font></td>
    	<td><input type="password" name="password" size="20" maxlength="30"></td>
    </tr>
    <tr>
    	<td><font size="-1">Confirm Password:</font></td>
    	<td><input type="password" name="confirmPassword" size="20" maxlength="30"></td>
    </tr>
    <tr>
    	<td colspan="2"><br><input type="submit" value="Change Password"></td>
    </tr>
    </table>
</ul>
</form>

</body>
</html>
