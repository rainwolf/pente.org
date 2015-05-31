<%
/**
 *	$RCSfile: forumContent_edit.jsp,v $
 *	$Revision: 1.2 $
 *	$Date: 2002/10/02 01:20:37 $
 */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
	errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>
 
<%	// Permission check
    if (!isSystemAdmin && !isForumAdmin && !isModerator) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }
    
    // Get parameters
	long forumID = ParamUtils.getLongParameter(request,"forum",-1L);
	long threadID = ParamUtils.getLongParameter(request,"thread",-1L);
    long messageID = ParamUtils.getLongParameter(request,"message",-1L);
    String subject = ParamUtils.getParameter(request,"subject",true);
    String body = ParamUtils.getParameter(request,"body");
	boolean save = request.getParameter("saveButton") != null;
    boolean cancel = request.getParameter("cancelButton") != null;
    boolean showEditedByText = ParamUtils.getBooleanParameter(request,"showEditedByText");
    String editedByText = ParamUtils.getParameter(request,"editedByText");
    
    // Load the forum we're working with
    Forum forum = forumFactory.getForum(forumID);
    
    // Optionally load the thread we're working with
    ForumThread thread = forum.getThread(threadID);
    
    // Optionally load the message we're working with
    ForumMessage message = thread.getMessage(messageID);
    
    // Cancel back to the forumContent_thread.jsp page
    if (cancel) {
        response.sendRedirect("forumContent_thread.jsp?forum="+forumID+"&thread="+threadID);
        return;
    }
    
    // Error checks
    boolean errors = false;
    if (save) {
        if (subject == null || body == null) {
            errors = true;
        }
    }
    
    // Save an edit of this message
	if (save && !errors) {
        message.setSubject(subject);
        if (showEditedByText && editedByText != null) {
            message.setBody(body + "\n" + editedByText);
        }
        else {
            message.setBody(body);
        }
        response.sendRedirect("forumContent_thread.jsp?forum="+forumID+"&thread="+threadID);
        return;
	}
%>

<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Manage Content: Edit Message";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Forums", "forums.jsp"},
        {"Edit Forum", "editForum.jsp?forum="+forumID},
        {"Manage Content", "forumContent.jsp?forum="+forumID},
        {"Edit Thread", "forumContent_thread.jsp?forum="+forumID+"&thread="+threadID},
        {"Edit Message", "forumContent_edit.jsp?forum="+forumID+"&thread="+threadID+"&message="+messageID}
    };
%>
<%@ include file="title.jsp" %>

<font size="-1">
Use the form below to edit the contents of this message.
</font>

<table cellpadding="2" cellspacing="0" border="0">
<tr>
	<td>
        <font size="-1">
        <a href="editMessageProps.jsp?forum=<%= forumID %>&thread=<%= threadID %>&message=<%= messageID %>"
         title="Click to edit extended properties of this message"
         ><img src="images/button_edit.gif" width="17" height="17" border="0"></a>
        </font>
    </td>
	<td>
        <font size="-1">
        <a href="editMessageProps.jsp?forum=<%= forumID %>&thread=<%= threadID %>&message=<%= messageID %>"
         title="Click to edit extended properties of this message"
         >Edit Extended Properties</a>
        </font>
    </td>
	<td>
        <font size="-1">
        <a href="forumContent_delete.jsp?forum=<%= forumID %>&thread=<%= threadID %>&message=<%= messageID %>"
         title="Click to delete this message"
         ><img src="images/button_delete.gif" width="17" height="17" border="0"></a>
        </font>
    </td>
	<td>
        <font size="-1">
        <a href="forumContent_delete.jsp?forum=<%= forumID %>&thread=<%= threadID %>&message=<%= messageID %>"
         title="Click to delete this message"
         >Delete This Message</a>
        </font>
    </td>
</tr>
</table>

<p>

<form action="forumContent_edit.jsp" method="post">
<input type="hidden" name="forum" value="<%= forumID %>">
<input type="hidden" name="thread" value="<%= threadID %>">
<input type="hidden" name="message" value="<%= messageID %>">

<table cellpadding="2" cellspacing="0" border="0">
<%  User author = message.getUser();
    String name = null;
    if (author != null) {
        name = author.getName();
    }
%>
<tr>
	<td valign="top" align="right"><font size="-1">Author:</font></td>
    <%  if (author == null) { %>
	<td><font size="-1"><i>Guest</i></font></td>
    <%  } else { %>
	<td><font size="-1">
        <a href="userProfile.jsp?user=<%= author.getID() %>"><%= author.getUsername() %></a>
        <%  if (name != null) { %>
        (<%= name %>)
        <%  } %>
        </font>
    </td>
    <%  } %>
</tr>
<tr>
	<td align="right"><font size="-1">Posted:</font></td>
	<td><font size="-1">
        <%= SkinUtils.formatDate(request,pageUser,message.getCreationDate()) %>
        <%  String ip = message.getProperty("IP");
            if (ip != null) {
        %>
            from IP: <%= ip %>
        <%  } %>
        </font>
    </td>
</tr>
<tr>
	<td align="right"><font size="-1">Replies:</font></td>
	<td><font size="-1"><%= thread.getTreeWalker().getChildCount(message) %></font></td>
</tr>
<tr>
	<td align="right"><font size="-1">Subject:</font></td>
    <%  String subj = message.getSubject(); %>
	<td><input type="text" name="subject" size="50" maxlength="200"
         value="<%= (subj!=null)?subj:"" %>">
    </td>
</tr>
<tr>
	<td valign="top" align="right"><font size="-1">Message:</font></td>
	<td><textarea rows="15" cols="60" name="body" wrap="virtual"><%= message.getUnfilteredBody() %></textarea></td>
</tr>
<tr>
	<td valign="top" align="right"><font size="-1">Edit Stamp:</font></td>
	<td>
        <font size="-1">
        <input type="checkbox" name="showEditedByText" checked id="cb01">
        <label for="cb01">Include the following at the bottom of this message:</label>
        <br>
        </font>
<%  Date editDate = new Date(); %>
<textarea rows="3" cols="60" name="editedByText" wrap="virtual">

[Edited by: <%= pageUser.getUsername() %> on <%= SkinUtils.formatDate(request,pageUser,new Date()) %>]</textarea>
</td>
</tr>
<tr>
	<td valign="top" align="right">&nbsp;</td>
	<td><input type="submit" value="Save and Return" name="saveButton">
        <input type="submit" value="Cancel" name="cancelButton">
    </td>
</tr>
</table>

</form>

<p>

<%@ include file="footer.jsp" %>
