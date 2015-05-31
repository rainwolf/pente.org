
<%
/**
 *	$RCSfile: forumSearch.jsp,v $
 *	$Revision: 1.1 $
 *	$Date: 2002/08/16 06:52:22 $
 */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
				 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils" %>
 
<%@ include file="global.jsp" %>

<%	////////////////////
	// Security check
	
	// make sure the user is authorized to create forums::
	
%>

<%	//////////////////////
	// get parameters
	
	// paging vars:
	String forumName = ParamUtils.getParameter(request,"q");
    
    boolean errors = false;
    String errorMessage = "";
    if (forumName == null) {
        errors = true;
        errorMessage = "No results. Please enter a valid forum name.";
    }
%>

<html>
<head>
	<title></title>
	<link rel="stylesheet" href="style/global.css">
</head>

<body bgcolor="#ffffff" text="#000000" link="#0000ff" vlink="#800080" alink="#ff0000">

<%	///////////////////////
	// pageTitleInfo variable (used by include/pageTitle.jsp)
	String[] pageTitleInfo = { "Forums", "Forum Search" };
%>
<%	///////////////////
	// pageTitle include
%>

<p>

<%  if (errors) { %>

    <%= errorMessage %>

<%  } else {
        // Try to load the forum based on the forum name
        Forum forum = null;
        try {
            forum = forumFactory.getForum(forumName);
        }
        catch (Exception e) {}
        
        // no results:
        if (forum == null) {
%>

    <i>Forum <b><%= forumName %></b> not found.</i>

<%      }
        // else the user was found:
        else {
            long id = forum.getID();
            String name = forum.getName();
            String description = forum.getDescription();
%>

    Found forum <b><%= forumName %></b>.
    
    <p>
<form>

<table bgcolor="#999999" cellpadding="0" cellspacing="0" border="0" width="100%">
<td>
<table cellpadding="3" cellspacing="1" border="0" width="100%">
<tr bgcolor="#eeeeee">
	<td class="forumCellHeader" width="1%" nowrap>
		<b>ID</b>
	</td>
	<td class="forumCellHeader" width="1%" nowrap>
		<b>Forum Name</b>
	</td>
	<td class="forumCellHeader" width="93%"><b>Description</b></td>
	<td class="forumCellHeader" align="center" width="1%" nowrap><b>Threads /<br>Messages</b></td>
	<td class="forumCellHeader" align="center" width="1%" nowrap><b>Properties</b></td>
	<td class="forumCellHeader" align="center" width="1%" nowrap><b>Permissions</b></td>
	<td class="forumCellHeader" align="center" width="1%" nowrap><b>Filters</b></td>
	<td class="forumCellHeader" align="center" width="1%" nowrap><b>Remove</b></td>
	<td class="forumCellHeader" align="center" width="1%" nowrap><b>Content</b></td>
</tr>
	<tr bgcolor="#ffffff">
		<td class="forumCell" align="center"><b><%= id %></b></td>
		<td class="forumCell">
			<b><a href="forumDetail.jsp?forum=<%= id %>"
			    title="More details..."><%= name %></a></b>
		</td>
		<td class="forumCell"><i><%= (description!=null&&!description.equals(""))?description:"" %></i></td>
		<td align="center" class="forumCell"><%= forum.getThreadCount() %> / <%= forum.getMessageCount() %></td>
		<td align="center">
			<input type="radio" name="edit"
			 onclick="location.href='editForum.jsp?forum=<%= id %>'">
		</td>
		<td align="center">
			<input type="radio" name="perms"
			 onclick="location.href='forumPerms.jsp?forum=<%= id %>'">
		</td>
		<td align="center">
			<input type="radio" name="filters"
			 onclick="location.href='forumFilters.jsp?forum=<%= id %>';">
		</td>
		<td align="center">
			<input type="radio" name="remove"
			 onclick="location.href='removeForum.jsp?forum=<%= id %>';">
		</td>
		<td align="center">
			<input type="radio" name="content"
			 onclick="location.href='forumContent.jsp?forum=<%= id %>';">
		</td>
	</tr>
</table>
</td>
</table>

</form>
    
<%      }
    }
%>

</body>
</html>

