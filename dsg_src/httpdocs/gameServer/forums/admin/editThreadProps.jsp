<%
/**
 *	$RCSfile: editThreadProps.jsp,v $
 *	$Revision: 1.2 $
 *	$Date: 2002/10/02 01:20:37 $
 */
%>

<%@ page import="java.net.*,
                 java.util.*,
                 java.text.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
    errorPage="error.jsp"
%>
 
<%@ include file="global.jsp" %>

<%! // Global vars, methods, etc...
    
    // Date formatter for creation date/modified date
    SimpleDateFormat dateFormatter = new SimpleDateFormat("");
%>

<%	// Permission check
    if (!isSystemAdmin && !isForumAdmin && !isModerator) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }
    
    // Get parameters
	long forumID = ParamUtils.getLongParameter(request,"forum",-1L);
	long threadID = ParamUtils.getLongParameter(request,"thread",-1L);
    String propertyName = ParamUtils.getParameter(request,"propName");
    String propertyValue = ParamUtils.getParameter(request,"propValue");
    String redirect = ParamUtils.getParameter(request,"redirect");
    
    if ("true".equals(request.getParameter("doRedirect"))) {
        // Redirect back to the "redirect" page
        response.sendRedirect(URLDecoder.decode(redirect));
        return;
    }
    
    // Load up the forum specified
    Forum forum = forumFactory.getForum(forumID);
    
    // Load the thread
    ForumThread thread = forum.getThread(threadID);
    
    // save the name & description if requested
    if ("true".equals(request.getParameter("saveProperty"))) {
        if (propertyName != null && propertyValue != null) {
            thread.setProperty(propertyName, propertyValue);
        }
        response.sendRedirect("editThreadProps.jsp?forum="+forumID+"&thread="+threadID);
        return;
    }
    
    if ("true".equals(request.getParameter("delete"))) {
        // Add a property
        if (propertyName != null) {
            thread.deleteProperty(propertyName);
            // Done so redirect
            response.sendRedirect("editThreadProps.jsp?forum="+forumID+"&thread="+threadID);
            return;
        }
    }
%>

<%@ include file="header.jsp" %>

<%  // Title of this page and breadcrumbs
    String title = "Thread Extended Properties";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Forums", "forums.jsp"},
        {title, "editThreadProps.jsp?forum="+forumID+"&thread="+threadID}
    };
%>
<%@ include file="title.jsp" %>

<p>

<font size="-1">
Edit the extended properties of a thread using the forms below.
</font>

<p>

<font size="-1"><b>Extended Properties</b></font>
<ul>
<font size="-1">To edit the value of a property, use the form below.<p></font>
<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="">
<tr><td>
<table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
<tr bgcolor="#eeeeee">
    <td align="center"><font size="-2" face="verdana,arial,helvetica,sans-serif"><b>PROPERTY NAME</b></font></td>
    <td align="center"><font size="-2" face="verdana,arial,helvetica,sans-serif"><b>PROPERTY VALUE</b></font></td>
    <td align="center"><font size="-2" face="verdana,arial,helvetica,sans-serif"><b>DELETE</b></font></td>
</tr>
<%  Iterator properties = thread.getPropertyNames();
    if (!properties.hasNext()) {
%>
<tr bgcolor="#ffffff">
    <td align="center" colspan="3"><font size="-1"><i>No properties</i></font></td>
</tr>
<%  }
    while (properties.hasNext()) {
        propertyName = (String)properties.next();
        propertyValue = thread.getProperty(propertyName);
%>
<tr bgcolor="#ffffff">
    <td><font size="-1"><%= propertyName %></font></td>
    <td><font size="-1"><%= propertyValue %></font></td>
    <td align="center"><a href="editThreadProps.jsp?forum=<%= forumID %>&thread=<%= threadID %>&delete=true&propName=<%= URLEncoder.encode(propertyName) %>"
        ><img src="images/button_delete.gif" width="17" height="17" alt="Click to delete this property" border="0"></a
        ></td>
</tr>
<%  } %>
</table>
</td></tr>
</table>
</ul>

<form action="editThreadProps.jsp" method="post">
<input type="hidden" name="forum" value="<%= forumID %>">
<input type="hidden" name="thread" value="<%= threadID %>">
<input type="hidden" name="saveProperty" value="true">
<%  if (redirect != null) { %>
<input type="hidden" name="redirect" value="<%= URLEncoder.encode(redirect) %>">
<%  } %>
<font size="-1"><b>Add or Edit Extended Properties</b></font>
<ul>
<table bgcolor="#cccccc" cellpadding="0" cellspacing="0" border="0" width="">
<tr><td>
<table bgcolor="#cccccc" cellpadding="3" cellspacing="1" border="0" width="100%">
<tr bgcolor="#ffffff">
    <td><font size="-1">Property Name:</font></td>
    <td><input type="text" name="propName" size="20" maxlength="100"></td>
</tr>
<tr bgcolor="#ffffff">
    <td><font size="-1">Property Value:</font></td>
    <td><input type="text" name="propValue" size="20" maxlength="200"></td>
</tr>
<tr bgcolor="#ffffff">
    <td colspan="2">
    <input type="submit" value="Save Property">
    </td>
</tr>
</table>
</td></tr>
</table>
</form>

<p>

<%  if (redirect != null) { %>
<form action="editThreadProps.jsp">
<input type="hidden" name="doRedirect" value="true">
<input type="hidden" name="redirect" value="<%= URLEncoder.encode(redirect) %>">
<center>
    <input type="submit" value="Done / Go Back">
</center>
</form>
<%  } %>

<%@ include file="footer.jsp" %>
