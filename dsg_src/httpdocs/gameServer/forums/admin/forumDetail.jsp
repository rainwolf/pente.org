<%
   /**
    *	$RCSfile: forumDetail.jsp,v $
    *	$Revision: 1.2.4.1 $
    *	$Date: 2003/03/26 00:12:26 $
    */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils" %>

<%! ///////////////////////
   // page variables
   SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d 'at' hh:mm:ss a");
%>

<%@ include file="global.jsp" %>

<% ////////////////////
   // Security check

   // make sure the user is authorized to administer users:

%>

<% ////////////////////
   // get parameters

   long forumID = ParamUtils.getLongParameter(request, "forum", -1L);
%>

<% //////////////////////////////////
   // global error variables

   String errorMessage = "";

   boolean noForumSpecified = (forumID < 0);
   boolean errors = (noForumSpecified);
%>

<html>
<head>
   <title></title>
   <link rel="stylesheet" href="style/global.css">
</head>

<body bgcolor="#ffffff" text="#000000" link="#0000ff" vlink="#800080" alink="#ff0000">

<% ///////////////////////
   // pageTitleInfo variable (used by include/pageTitle.jsp)
   String[] pageTitleInfo = {"Forums : Forum Details"};
%>
<% ///////////////////
   // pageTitle include
%>

<p>

      <%	/////////////////////
	// at this point, we know there is a forum to work with:
	Forum forum = null;
	try {
		forum = forumFactory.getForum(forumID);
	} catch( ForumNotFoundException fnfe ) {
	} catch( UnauthorizedException ue ) {
	}
	
	Date creationDate = forum.getCreationDate();
	String description = forum.getDescription();
	/*
	ForumMessageFilter[] installedFilters = null;
	try {
		installedFilters = forum.getForumMessageFilters();
	}
	catch( UnauthorizedException ue ) {}
	*/
	int messageCount = forum.getMessageCount();
	Date modifiedDate = forum.getModificationDate();
	String forumName = forum.getName();
	Permissions forumPermissions = forum.getPermissions(authToken);
	Iterator propertyNames = forum.getPropertyNames();
	int threadCount = forum.getThreadCount();
	
%>

   <font size="+1">
      Forum: <%= forumName %>
   </font>

<p>

<table bgcolor="#999999" cellspacing="0" cellpadding="0" width="100%" border="0">
   <td>
      <table bgcolor="#999999" cellspacing="1" cellpadding="3" width="100%" border="0">
         <tr bgcolor="#ffffff">
            <td nowrap>Description</td>
            <td><i><%= (description != null && !description.equals("")) ? description : "&nbsp;" %>
            </i></td>
         </tr>
         <tr bgcolor="#ffffff">
            <td nowrap>Number of Threads</td>
            <td><%= threadCount %>
            </td>
         </tr>
         <tr bgcolor="#ffffff">
            <td nowrap>Number of Messages</td>
            <td><%= messageCount %>
            </td>
         </tr>
         <tr bgcolor="#ffffff">
            <td nowrap>Creation Date</td>
            <td><%= dateFormat.format(creationDate) %>
            </td>
         </tr>
         <tr bgcolor="#ffffff">
            <td nowrap>Last Modified Date</td>
            <td><%= dateFormat.format(modifiedDate) %>
            </td>
         </tr>
      </table>
   </td>
</table>

<p>

   <b>Extended Properties:</b>
<p>
      <%	if( !propertyNames.hasNext() ) { %>
<ul><i>No extended properties.</i></ul>
<% } else { %>
<table bgcolor="#999999" cellspacing="0" cellpadding="0" width="95%" align="right">
   <td>
      <table bgcolor="#999999" cellspacing="1" cellpadding="3" width="100%">
         <% while (propertyNames.hasNext()) { %>
         <% String propertyName = (String) propertyNames.next(); %>
         <% String propertyValue = forum.getProperty(propertyName); %>
         <tr bgcolor="#ffffff">
            <td><%= propertyName %>
            </td>
            <td><%= propertyValue %>
            </td>
         </tr>
         <% } %>
      </table>
   </td>
</table>
<br clear="all"><br>
<% } %>


</body>
</html>

