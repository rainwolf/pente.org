
<%
/**
 *	$RCSfile: createUser.jsp,v $
 *	$Revision: 1.1 $
 *	$Date: 2002/08/16 06:52:22 $
 */
%>

<%@ page import="java.util.*,
                 java.net.URLEncoder,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"%>

<%@ include file="global.jsp" %>
 
<%	// Permission check
    if (!isSystemAdmin && !isUserAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }
    
    // error variables for parameters
	
	boolean errorEmail = false;
	boolean errorUsername = false;
	boolean errorNoPassword = false;
	boolean errorNoConfirmPassword = false;
	boolean errorPasswordsNotEqual = false;
	
	// error variables from user creation
	boolean errorUserAlreadyExists = false;
	boolean errorNoPermissionToCreate = false;
	
	// overall error variable
	boolean errors = false;
	
	// creation success variable:
	boolean success = false;
    
    // get parameters
	String name             = ParamUtils.getParameter(request,"name");
	String email            = ParamUtils.getParameter(request,"email");
	String username         = ParamUtils.getParameter(request,"username");
	String password         = ParamUtils.getParameter(request,"password");
	String confirmPassword  = ParamUtils.getParameter(request,"confirmPassword");
	boolean usernameIsEmail = ParamUtils.getBooleanParameter(request,"usernameIsEmail");
	boolean nameVisible     = !ParamUtils.getBooleanParameter(request,"hideName");
	boolean emailVisible    = !ParamUtils.getBooleanParameter(request,"hideEmail");
	boolean doCreate        = ParamUtils.getBooleanParameter(request,"doCreate");
    
    // trim up the passwords so no one can enter a password of spaces
	if( password != null ) {
		password = password.trim();
		if( password.equals("") ) { password = null; }
	}
	if( confirmPassword != null ) {
		confirmPassword = confirmPassword.trim();
		if( confirmPassword.equals("") ) { confirmPassword = null; }
	}
    
    // check for errors
	if( doCreate ) {
		if( email == null ) {
			errorEmail = true;
		}
		if( username == null ) {
			errorUsername = true;
		}
		if( password == null ) {
			errorNoPassword = true;
		}
		if( confirmPassword == null ) {
			errorNoConfirmPassword = true;
		}
		if( password != null && confirmPassword != null
		    && !password.equals(confirmPassword) )
		{
			errorPasswordsNotEqual = true;
		}
		errors = errorEmail || errorUsername || errorNoPassword
		         || errorNoConfirmPassword || errorPasswordsNotEqual;
	}
    
    UserManager userManager = null;
	if( !errors && doCreate ) {
		// get a user manager to edit user properties
		userManager = forumFactory.getUserManager();
		try {
			User newUser = userManager.createUser(username,password,email);
			newUser.setName( name );
			newUser.setEmailVisible( emailVisible );
			newUser.setNameVisible( nameVisible );
			success = true;
		}
		catch( UserAlreadyExistsException uaee ) {
			errorUserAlreadyExists = true;
			errorUsername = true;
			errors = true;
		}
		catch( UnauthorizedException ue ) {
			errorNoPermissionToCreate = true;
			errors = true;
		}
	}
    
    // if a user was successfully created, say so and return (to stop the 
	// jsp from executing
	if( success ) {
		response.sendRedirect("users.jsp?msg="
			+ URLEncoder.encode("User was created successfully"));
		return;
	}
%>

<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Create User";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "createUser.jsp"}
    };
%>
<%@ include file="title.jsp" %>

<font size="-1">
This creates a user with no permissions and default privacy settings.
Once you create this user, you should edit their properties.
</font>

<p>

<%	// print error messages
	if( !success && errors ) {
%>
	<p><font color="#ff0000" size="-1">
	<%	if( errorUserAlreadyExists ) { %>
		The username "<%= username %>" is already taken. Please try 
		another one.
	<%	} else if( errorNoPermissionToCreate ) { %>
		You do not have user creation privileges.
	<%	} else { %>
		An error occured. Please check the following 
		fields and try again.
	<%	} %>
	</font><p>
<%	} %>

<p>

<%-- form --%>
<form action="createUser.jsp" method="post" name="createForm">
<input type="hidden" name="doCreate" value="true">

<font size="-1"><b>New User Information</b></font><p>

<table bgcolor="<%= tblBorderColor %>" cellspacing="0" cellpadding="0" border="0" width="95%" align="right">
<td>
<table bgcolor="<%= tblBorderColor %>" cellspacing="1" cellpadding="3" border="0" width="100%">

<%-- name row --%>
<tr bgcolor="#ffffff">
	<td><font size="-1">Name <i>(optional)</i></font></td>
	<td><input type="text" name="name" size="30"
		 value="<%= (name!=null)?name:"" %>">
	</td>	
</tr>

<%-- user email --%>
<tr bgcolor="#ffffff">
	<td><font size="-1"<%= (errorEmail)?(" color=\"#ff0000\""):"" %>>Email</font></td>
	<td><input type="text" name="email" size="30"
		 value="<%= (email!=null)?email:"" %>">
	</td>
</tr>

<%-- username --%>
<tr bgcolor="#ffffff">
	<td><font size="-1"<%= (!usernameIsEmail&&errorUsername)?" color=\"#ff0000\"":"" %>>
		Username
		<br>&nbsp;(<input type="checkbox" name="usernameIsEmail" 
		  id="cb01"<%= (usernameIsEmail)?" checked":"" %>
		  onclick="this.form.username.value=this.form.email.value;"> 
		<label for="cb01">use email</label>)
		</font>
	</td>
	<td><input type="text" name="username" size="30"
		<%	if( usernameIsEmail ) { %>
		 value="<%= (email!=null)?email:"" %>">
		<%	} else { %>
		 value="<%= (username!=null)?username:"" %>">
		<%	} %>
	</td>
</tr>

<%-- password --%>
<tr bgcolor="#ffffff">
	<td><font size="-1"<%= (errorNoPassword||errorPasswordsNotEqual)?" color=\"#ff0000\"":"" %>
		 >Password</font></td>
	<td><input type="password" name="password" value="" size="20" maxlength="30"></td>
</tr>

<%-- confirm password --%>
<tr bgcolor="#ffffff">
	<td><font size="-1"<%= (errorNoConfirmPassword||errorPasswordsNotEqual)?" color=\"#ff0000\"":"" %>
		 >Password (again)</font></td>
	<td><input type="password" name="confirmPassword" value="" size="20" maxlength="30"></td>
</tr>

</table>
</td>
</table>

<br clear="all"><br>

<input type="submit" value="Create User">
&nbsp;
<input type="submit" value="Cancel"
 onclick="location.href='users.jsp';return false;">

</form>

<script language="JavaScript" type="text/javascript">
<!--
document.createForm.name.focus();
//-->
</script>

<%@ include file="footer.jsp" %>
