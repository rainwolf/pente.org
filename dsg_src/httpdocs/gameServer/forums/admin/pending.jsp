<%
/**
 *	$RCSfile: pending.jsp,v $
 *	$Revision: 1.6.2.1 $
 *	$Date: 2003/03/21 22:15:38 $
 */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
				 com.jivesoftware.forum.util.*"
    errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%  // Permission check
    if (!isSystemAdmin && !isCatAdmin && !isForumAdmin && !isModerator) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }
    
    // get parameters
    long forumID = ParamUtils.getLongParameter(request,"forum",-1L);
    long threadID = ParamUtils.getLongParameter(request,"thread",-1L);
    long messageID = ParamUtils.getLongParameter(request,"message",-1L);
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",1);
    boolean approve = ParamUtils.getBooleanParameter(request,"approve");
    boolean edit = ParamUtils.getBooleanParameter(request,"edit");
    boolean reject = ParamUtils.getBooleanParameter(request,"reject");
    String subject = ParamUtils.getParameter(request,"subject");
    String body = ParamUtils.getParameter(request,"body");
    boolean showEditedByText = ParamUtils.getBooleanParameter(request,"showEditedByText");
    String editedByText = ParamUtils.getParameter(request,"editedByText");
    boolean msgapprove = ParamUtils.getBooleanParameter(request,"msgapprove");
    boolean msgreject = ParamUtils.getBooleanParameter(request,"msgreject");
    
    boolean errors = false;

    TreeWalker treeWalker = null;

    if (edit) {
        // check for errors
        if (subject == null || body == null) {
            errors = true;
        }
        if (!errors) {
            // Load forum, other objects
            Forum forum = forumFactory.getForum(forumID);
            ForumThread thread = forum.getThread(threadID);
            ForumMessage message = thread.getMessage(messageID);
            message.setSubject(subject);
            if (showEditedByText && editedByText != null) {
                message.setBody(body + "\n" + editedByText);
            }
            else {
                message.setBody(body);
            }
            // set the correct moderation values
            if (thread.getRootMessage().getID() == message.getID()) {
                // root message so set both thread and message mod values,
                thread.setModerationValue(forum.getModerationMinThreadValue(), authToken);
                message.setModerationValue(forum.getModerationMinMessageValue(), authToken);
            }
            else {
                message.setModerationValue(forum.getModerationMinMessageValue(), authToken);
            }
            response.sendRedirect("pending.jsp?forum="+ forumID +"&msgapprove=true");
            return;
        }
    }
    
    if (approve) {
        // Load forum, other objects
        Forum forum = forumFactory.getForum(forumID);
        ForumThread thread = forum.getThread(threadID);
        ForumMessage message = thread.getMessage(messageID);
        // set the correct moderation values
        if (thread.getRootMessage().getID() == message.getID()) {
            // root message so set both thread and message mod values,
            thread.setModerationValue(forum.getModerationMinThreadValue(), authToken);
            message.setModerationValue(forum.getModerationMinMessageValue(), authToken);
        }
        else {
            message.setModerationValue(forum.getModerationMinMessageValue(), authToken);
        }
        response.sendRedirect("pending.jsp?forum="+ forumID +"&msgapprove=true");
        return;
    }
    
    if (reject) {
        // Load forum, other objects
        Forum forum = forumFactory.getForum(forumID);
        ForumThread thread = forum.getThread(threadID);
        ForumMessage message = thread.getMessage(messageID);
        if (thread.getRootMessage().getID() == message.getID()) {
            forum.deleteThread(thread);
        }
        else {
            thread.deleteMessage(message);
        }
        response.sendRedirect("pending.jsp?forum="+ forumID +"&msgreject=true");
        return;
    }
    
    // Load the requested forum
    Forum forum = null;
    if (forumID != -1L) {
        forum = forumFactory.getForum(forumID);
    }
    
    // A list of forums to loop through.
    List moderatedForums = null;
    if (forum == null) {
        moderatedForums = moderatedForums(forumFactory, true);
    }
    else {
        moderatedForums = new ArrayList(1);
        moderatedForums.add(0,forum);
    }

    ForumThread thread = null;
    ForumMessage message = null;

    // load the thread & message if IDs were passed in
    if (threadID != -1L) {
        thread = forum.getThread(threadID);
        if (messageID != -1L) {
            message = thread.getMessage(messageID);
        }
        else {
            message = thread.getRootMessage();
        }
    }
    else {
        Iterator forums = moderatedForums.iterator();
        while (forums.hasNext()) {
            forum = (Forum)forums.next();
            // Check this forum for a pending thread or message:
            // Get an iterator of pending messages
            ResultFilter filter = new ResultFilter();
            filter.setNumResults(1);
            // Check only if thread or message mod is enabled:
            if (isThreadModEnabled(forum)) {
                filter.setModerationRangeMin(forum.getModerationDefaultThreadValue());
                filter.setModerationRangeMax(forum.getModerationDefaultThreadValue());
                Iterator threads = forum.getThreads(filter);
                if (threads.hasNext()) {
                    thread = (ForumThread)threads.next();
                    message = thread.getRootMessage();
                    break;
                }
            }
            if (thread == null && isMessageModEnabeld(forum)) {
                filter.setModerationRangeMin(forum.getModerationDefaultMessageValue());
                filter.setModerationRangeMax(forum.getModerationDefaultMessageValue());
                Iterator messages = forum.getMessages(filter);
                if (messages.hasNext()) {
                    message = (ForumMessage)messages.next();
                    thread = message.getForumThread();
                    break;
                }
            }
        }
    }
    if (thread != null) {
        treeWalker = thread.getTreeWalker();
    }

    // Indicates if there is anything to moderate:
    boolean canModerate = (message != null);
%>

<%@ include file="header.jsp" %>

<%  // Title of this page and breadcrumbs
    String title = "View Pending Submissions";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "pending.jsp"}
    };
%>
<%@ include file="title.jsp" %>

<font size="-1">
<%  if (canModerate) { %>
Below is a form you can use to approve, edit or reject pending messages. For
each message below, the thread and forum it is in is listed.
To jump
between pending messages in different forums, use the select box below.
<%  } else { %>
There are no pending messages in any moderated forums.
<%  } %>
</font>

<%  // Only show the form below if there something to moderate
    if (canModerate) {
%>

<p>

<%  if (edit && errors) { %>
    
    <font size="-1" color="#ff0000">
    There were errors editing the message. Please make sure you include
    the subject and body of the message, and 
    <a href="#edit">edit this message</a> again.<p>
    </font>
    
<%  } %>

<%  if (msgapprove) { %>

<font size="-1"><i>Message Approved.</i><p></font>

<%  } else if (msgreject) { %>

<font size="-1"><i>Message Rejected.</i><p></font>

<%  } %>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td>
<%  if (message.getID() != thread.getRootMessage().getID()) { %>
    <b>Pending Message: <%= message.getSubject() %></b>
<%  } else { %>
    <b>Pending Thread: <%= message.getSubject() %></b>
<%  } %>
    </td>
    <td align="right">
    <font size="-1">&nbsp;</font>
    </td>
</tr></table>
<br>

<table cellpadding="2" cellspacing="0" border="0">
<tr>
	<td><font size="-1">Author:</font></td>
    <td rowspan="99"><img src="images/blank.gif" width="5" height="1" border="0"></td>
    <%  String username = "<i>Guest</i>";
        if (message.getUser() != null) {
            username = message.getUser().getUsername();
        }
    %>
	<td><font size="-1"><%= username %></font></td>
</tr>
<tr>
	<td><font size="-1">Posted:</font></td>
	<td><font size="-1">
        <%= SkinUtils.formatDate(request,pageUser,message.getCreationDate()) %>
        (<%= SkinUtils.dateToText(request,pageUser,message.getCreationDate()) %>)
        <%  String ip = message.getProperty("IP");
            if (ip != null) {
        %>  from IP: <%= ip %>
        <%  } %>
        </font>
    </td>
</tr>
<tr>
	<td><font size="-1">Forum:</font></td>
	<td><font size="-1">
        <a href="forumContent.jsp?forum=<%= forum.getID() %>"
         ><%= forum.getName() %></a>
        </font>
    </td>
</tr>
<%  if (message.getID() != thread.getRootMessage().getID()) { %>
<tr>
	<td><font size="-1">Thread:</font></td>
	<td><font size="-1">
        <a href="forumContent.jsp?forum=<%= forum.getID() %>&thread=<%= thread.getID() %>"
         ><%= thread.getName() %></a>
        </font>
    </td>
</tr>
<%  } %>
</table>
<br>

<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td>
<table bgcolor="<%= tblBorderColor %>" cellpadding="6" cellspacing="1" border="0" width="100%">
<tr bgcolor="#ffffff">
    <td>
    <font size="-1">
<%= message.getBody() %>
    </font>
    </td>
</tr>
</table>
</td></tr>
</table>
<br>
<table cellpadding="2" cellspacing="0" border="0">
<tr>
	<td><img src="images/button_approve.gif" width="17" height="17" border="0"></td>
	<td><font size="-1"><a href="pending.jsp?approve=true&forum=<%= forum.getID() %>&thread=<%= thread.getID() %>&message=<%= message.getID() %>"
         onclick="return confirm('Are you sure you want to approve this message?');"
         title="Approve This Message"
         >Approve Message</a></font>
    </td>
    <td><font size="-1">&nbsp;</font></td>
	<td><img src="images/button_edit.gif" width="17" height="17" border="0"></td>
	<td><font size="-1"><a href="#edit"
         title="Edit This Message"
         >Edit Message</a></font>
    </td>
    <td><font size="-1">&nbsp;</font></td>
	<td><img src="images/button_delete.gif" width="17" height="17" border="0"></td>
	<td><font size="-1"><a href="pending.jsp?reject=true&forum=<%= forum.getID() %>&thread=<%= thread.getID() %>&message=<%= message.getID() %>"
         onclick="return confirm('Are you sure you want to reject this message?');"
         title="Reject This Message"
         >Reject Message</a></font>
    </td>
</tr>
</table>

<p><br>

<form action="pending.jsp">
<input type="hidden" name="forum" value="<%= forum.getID() %>">
<input type="hidden" name="thread" value="<%= thread.getID() %>">
<input type="hidden" name="message" value="<%= message.getID() %>">
<input type="hidden" name="edit" value="true">

<a name="edit" style="text-decoration:none;"></a>
<b>Edit Message</b>
<p>
<table cellpadding="2" cellspacing="0" border="0">
<tr><td><font size="-1">Subject</font></td>
    <td><input type="text" name="subject" value="<%= StringUtils.escapeHTMLTags(message.getUnfilteredSubject()) %>" size="65" maxlength="200"></td>
</tr>
<tr><td valign="top"><font size="-1">Message</font></td>
    <td>
    <textarea name="body" cols="60" rows="8" wrap="virtual"><%= StringUtils.escapeHTMLTags(message.getUnfilteredBody()) %></textarea>
    </td>
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
<tr><td><font size="-1">&nbsp;</font></td>
    <td><input type="submit" value="Save Changes and Approve Message"></td>
</tr>
</table>

<br><br><br><br><br>
<br><br><br><br><br>
<br><br><br><br><br>
<br><br><br><br><br>

<%  } // end if canModerate %>

<%@ include file="footer.jsp" %>

<%!
    // Method to determine if thread mod is on for the given forum:
    private static boolean isThreadModEnabled(Forum forum) {
        return (forum.getModerationDefaultThreadValue() < forum.getModerationMinThreadValue());
    }

    // Method to determine if message mod is on for the given forum:
    private static boolean isMessageModEnabeld(Forum forum) {
        return (forum.getModerationDefaultMessageValue() < forum.getModerationMinMessageValue());
    }
%>
