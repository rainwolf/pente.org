
<%
/**
 *	$RCSfile: groupPerms.jsp,v $
 *	$Revision: 1.1.4.1 $
 *	$Date: 2003/03/26 00:12:26 $
 */
%>

<%@ page import="java.util.*,
                 java.net.URLEncoder,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"%>
 
<%!	////////////////////////
	// global page variables
	
	private final String ADD = "Add";
	private final String REMOVE = "Remove";
%>

<%!	///////////////////
	// global methods
	
	private String getParameterMode( String paramVal ) {
		if( paramVal == null ) {
			return "";
		}
		if( paramVal.equals(ADD) ) { return "add"; }
		else if( paramVal.equals(REMOVE) ) { return "remove"; }
		else {
			return "";
		}
	}
	
	private int[] getIntListboxParams( String[] paramVal ) {
		if( paramVal == null ) { 
			return new int[0]; 
		}
		int[] params = new int[paramVal.length];
		for (int i=0;i<paramVal.length;i++)
		{
			try {
				params[i] = Integer.parseInt(paramVal[i]);
			} catch( NumberFormatException nfe ) {}
		}
		return params;
	}
%>

<%@ include file="global.jsp" %>
 
<%	////////////////////
	// Security check
	
	// make sure the user is authorized to administer users:
	
%>

<%	////////////////////
	// get parameters
	
	long groupID = ParamUtils.getLongParameter(request,"group",-1L);
	boolean doAction = ParamUtils.getBooleanParameter(request,"doAction");
	
	String groupAdminMode = getParameterMode(ParamUtils.getParameter(request,"groupAdminMode"));
	String groupUserMode  = getParameterMode(ParamUtils.getParameter(request,"groupUserMode"));
	
	int[] groupAdminsParam = getIntListboxParams(request.getParameterValues("groupAdmins"));
	//int[] allAdminsParam   = getIntListboxParams(request.getParameterValues("allAdmins"));
	int[] groupUsersParam  = getIntListboxParams(request.getParameterValues("groupUsers"));
	//int[] allUsersParam    = getIntListboxParams(request.getParameterValues("allUsers"));
	String allUsersUsername = ParamUtils.getParameter(request,"allUsersUsername");
	String allAdminsUsername = ParamUtils.getParameter(request,"allAdminsUsername");
%>

<%	/////////////////////
	// other page variables
	
	boolean addGroupAdmin = (groupAdminMode.equals("add"));
	boolean removeGroupAdmin = (groupAdminMode.equals("remove"));
	
	boolean addGroupUser = (groupUserMode.equals("add"));
	boolean removeGroupUser = (groupUserMode.equals("remove"));
%>

<%	///////////////////////
	// error variables
	
	boolean errorGroupNotFound = false;
	boolean errorNoPermission = false;
	boolean errors = false;
%>

<%	//////////////////////////////////
	// global variables
	
	UserManager userManager = forumFactory.getUserManager();
	GroupManager groupManager = forumFactory.getGroupManager();
%>

<%	/////////////////////
	// try to load the group from the passed in group id
	Group group = null;
	try {
		group = groupManager.getGroup(groupID);
	}
	catch( GroupNotFoundException gnfe ) {
		response.sendRedirect("error.jsp?msg="
			+ URLEncoder.encode("Group " + groupID + " not found") );
		return;
	}
%>

<%	/////////////////////
	// this group properties
	
	String groupName = group.getName();
	String groupDescription = group.getDescription();
	int userCount = group.getMemberCount();
	Iterator adminIterator = group.administrators();
	//Iterator allAdmins = userManager.users();
	Iterator userIterator = group.members();
	//Iterator allUsers = userManager.users();
%>


<%	/////////////////////////
	// do an action!
	
	if( doAction ) {
		
		// add a group administrator
		if( addGroupAdmin ) {
			try {
				User admin = userManager.getUser(allAdminsUsername);
				group.addAdministrator(admin);
			}
			catch( UserNotFoundException unfe ) {}
			catch( UnauthorizedException ue   ) {}
		}
		
		// remove a group administrator
		else if( removeGroupAdmin ) {
			for( int i=0; i<groupAdminsParam.length; i++ ) {
				try {
					User admin = userManager.getUser(groupAdminsParam[i]);
					group.removeAdministrator(admin);
				}
				catch( UserNotFoundException unfe ) {}
				catch( UnauthorizedException ue   ) {}
			}
		}
		
		// add a group user
		else if( addGroupUser ) {
			try {
				User user = userManager.getUser(allUsersUsername);
				group.addMember(user);
			}
			catch( UserNotFoundException unfe ) { unfe.printStackTrace(); }
			catch( UnauthorizedException ue   ) { ue.printStackTrace(); }
		}
		
		// remove a group user
		else if( removeGroupUser ) {
			for( int i=0; i<groupUsersParam.length; i++ ) {
				try {
					User user = userManager.getUser(groupUsersParam[i]);
					group.removeMember(user);
				}
				catch( UserNotFoundException unfe ) {}
				catch( UnauthorizedException ue   ) {}
			}
		}
	}
%>

<%	////////////////////
	// if we did something, redirect to this page again (since we're doing POSTS
	// on the form)
	
	// uncommented so i can debug parameters!!
	if( doAction ) {
		response.sendRedirect("groupPerms.jsp?group="+groupID);
		return;
	}
%>

<html>
<head>
	<title></title>
	<link rel="stylesheet" href="style/global.css">
	<script language="JavaScript" type="text/javascript">
	<!--
		function selAllListBox( el, chkbx ) {
			if( chkbx.checked ) {
				for( var i=0; i<el.options.length; i++ ) {
					el.options[i].selected = true;
				}
			}
		}
	//-->
	</script>
</head>

<body bgcolor="#ffffff" text="#000000" link="#0000ff" vlink="#800080" alink="#ff0000">

<%	///////////////////////
	// pageTitleInfo variable (used by include/pageTitle.jsp)
	String[] pageTitleInfo = { "Groups : Group Permissions" };
%>
<%	///////////////////
	// pageTitle include
%>

<p>

<b>Permissions for group:</b>
<%= groupName %>
<p>

<form action="groupPerms.jsp" method="post"> 
<input type="hidden" name="doAction" value="true">
<input type="hidden" name="group" value="<%= groupID %>">

<%-- member table --%>
<table bgcolor="#666666" cellpadding="0" cellspacing="0" width="80%" align="center" border="0">
<td>
<table bgcolor="#666666" cellpadding="3" cellspacing="1" width="100%" align="center" border="0">
<tr bgcolor="#eeeeee">
	<td width="99%">
	Members of this group
	</td>
		<td width="1%" nowrap>
		<a href="createUser.jsp?type=user">Create New User</a>
		</td>
</tr>
<tr bgcolor="#ffffff">
	<td colspan="2">
	
	<%-- table for listboxes of members --%>
	<table cellpadding="3" cellspacing="0" border="0" width="100%">
	<tr>
		<td width="50%" align="center">
			Members of this group:
			<br>
			<select size="5" name="groupUsers" multiple>
			<%	HashMap groupUserMap = new HashMap(); %>
			<%	while( userIterator.hasNext() ) { %>
			<%		User user = (User)userIterator.next(); %>
			<%		long userID = user.getID(); %>
			<%		groupUserMap.put( ""+userID, ""+userID ); %>
				<option value="<%=user.getID()%>"><%= user.getUsername() %>
			<%	} %>
			</select>
			<br>
			(<input type="checkbox" name="" value="" id="cbusr01"
			  onclick="selAllListBox(this.form.groupUsers,this);">
			<label for="cbusr01">Select All</label>)
			<br>
			<input type="submit" name="groupUserMode" value="<%= REMOVE %>">
		</td>
		<td width="50%" align="center">
			Enter a username to add to this group:
			<br>
			<input type="text" name="allUsersUsername" value="">
			<br>
			<input type="submit" name="groupUserMode" value="<%= ADD %>">
		</td>
	</tr>
	</table>
	<%-- /table for listboxes of members --%>
	
	</td>
</tr>
</table>
</td>
</table>
<%-- /member table --%>

<p>

<%-- admin table --%>
<table bgcolor="#666666" cellpadding="0" cellspacing="0" width="80%" align="center" border="0">
<td>
<table bgcolor="#666666" cellpadding="3" cellspacing="1" width="100%" align="center" border="0">
<tr bgcolor="#eeeeee">
	<td width="99%">
	Adminstrators for this group
	</td>
		<td width="1%" nowrap>
		<a href="createUser.jsp?type=admin">Create New Admin</a>
		</td>
</tr>
<tr bgcolor="#ffffff">
	<td colspan="2">
	
	<%-- table for listboxes of admins --%>
	<table cellpadding="3" cellspacing="0" border="0" width="100%">
	<tr>
		<td width="50%" align="center">
			Admins for this group:
			<br>
			<select size="5" name="groupAdmins" multiple>
			<%	HashMap groupAdminMap = new HashMap(); %>
			<%	while( adminIterator.hasNext() ) { %>
			<%		User admin = (User)adminIterator.next(); %>
			<%		long adminID = admin.getID(); %>
			<%		groupAdminMap.put( ""+adminID, ""+adminID ); %>
				<option value="<%=admin.getID()%>"><%= admin.getUsername() %>
			<%	} %>
			</select>
			<br>
			(<input type="checkbox" name="" value="" id="cbadm01"
			  onclick="selAllListBox(this.form.groupAdmins,this);">
			<label for="cbadm01">Select All</label>)
			<br>
			<input type="submit" name="groupAdminMode" value="<%= REMOVE %>">
		</td>
		<td width="50%" align="center">
			Enter a username to add as an admin of this group:
			<br>
			<input type="text" name="allAdminsUsername" value="">
			<br>
			<input type="submit" name="groupAdminMode" value="<%= ADD %>">
		</td>
	</tr>
	</table>
	<%-- /table for listboxes of admins --%>
	
	</td>
</tr>
</table>
</td>
</table>
<%-- /admin table --%>

<p>

</form>

<center>
	<form action="groups.jsp">
	<input type="submit" value="Done">
	</form>
</center>

</body>
</html>



