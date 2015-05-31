<%
/**
 *	$RCSfile: pending-list.jsp,v $
 *	$Revision: 1.1 $
 *	$Date: 2003/01/08 22:43:07 $
 */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
				 com.jivesoftware.forum.util.*,
                 com.jivesoftware.forum.action.util.Guest"
    errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%  // comment
    long forumID = ParamUtils.getLongParameter(request,"forum",-1L);
    int tstart = ParamUtils.getIntParameter(request,"tstart",0);
    int mstart = ParamUtils.getIntParameter(request,"mstart",0);
    Forum forum = forumFactory.getForum(forumID);
%>

<%@ include file="header.jsp" %>

<%  // Title of this page and breadcrumbs
    String title = "Pending Submissions";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "pending-list.jsp?forum=" + forumID}
    };
%>
<%@ include file="title.jsp" %>

<p>
Below is a list of pending submissions by forum. To approve, edit or reject a message
or thread, click on its name.
</p>

<p>
<b>Pending Topics</b>
</p>

<%  ResultFilter threadFilter = ResultFilter.createDefaultThreadFilter();
    threadFilter.setModerationRangeMin(forum.getModerationDefaultThreadValue());
    threadFilter.setModerationRangeMax(forum.getModerationDefaultThreadValue());
    threadFilter.setStartIndex(tstart);
    threadFilter.setNumResults(25);
    Iterator threads = forum.getThreads(threadFilter);
    if (!threads.hasNext()) {
%>
    <ul><i>No pending threads in this forum</i></ul>

<%
    }
    else {
%>
    <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
    <tr><td>
    <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#eeeeee">
        <td align="center"><font size="-2" face="verdana"><b>TOPIC</b></font></td>
        <td align="center"><font size="-2" face="verdana"><b>FORUM</b></font></td>
        <td align="center"><font size="-2" face="verdana"><b>AUTHOR</b></font></td>
        <td align="center"><font size="-2" face="verdana"><b>DATE POSTED</b></font></td>
    </tr>

<%      while (threads.hasNext()) {
            ForumThread thread = (ForumThread)threads.next();
            User author = null;
//            if (!thread.getRootMessage().isAnonymous()) {
                author = thread.getRootMessage().getUser();
//            }
%>
        <tr bgcolor="#ffffff">
            <td width="97%">
                <a href="pending.jsp?forum=<%= forumID %>&thread=<%= thread.getID() %>"
                 ><%= thread.getName() %></a>
            </td>
            <td width="1%" nowrap>
                <%= forum.getName() %>
            </td>
            <td width="1%" nowrap>
                <%  if (author != null) { %>
                    <%= author.getUsername() %>
                <%  } else { %>
                    Guest
                <%  } %>
            </td>
            <td width="1%" nowrap>
                <%= SkinUtils.formatDate(request, pageUser, thread.getCreationDate()) %>
            </td>
        </tr>

    <%  } %>

    </table>
    </td></tr>
    </table>

<%  } %>

<p>
<b>Pending Messages</b>
</p>

<%  ResultFilter messageFilter = ResultFilter.createDefaultThreadFilter();
    messageFilter.setModerationRangeMin(forum.getModerationDefaultMessageValue());
    messageFilter.setModerationRangeMax(forum.getModerationDefaultMessageValue());
    messageFilter.setStartIndex(mstart);
    messageFilter.setNumResults(25);
    Iterator messages = forum.getMessages(messageFilter);
    List messageList = new java.util.LinkedList();
    while (messages.hasNext()) {
        ForumMessage msg = (ForumMessage)messages.next();
        if (msg.getID() != msg.getForumThread().getRootMessage().getID()) {
            messageList.add(msg);
        }
    }
    messages = messageList.iterator();
    if (!messages.hasNext()) {
%>
    <ul><i>No pending messages in this forum</i></ul>

<%
    }
    else {
%>
    <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
    <tr><td>
    <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#eeeeee">
        <td align="center"><font size="-2" face="verdana"><b>MESSAGE</b></font></td>
        <td align="center"><font size="-2" face="verdana"><b>TOPIC</b></font></td>
        <td align="center"><font size="-2" face="verdana"><b>FORUM</b></font></td>
        <td align="center"><font size="-2" face="verdana"><b>AUTHOR</b></font></td>
        <td align="center"><font size="-2" face="verdana"><b>DATE POSTED</b></font></td>
    </tr>

<%      while (messages.hasNext()) {
            ForumMessage message = (ForumMessage)messages.next();
            User author = null;
//            if (!message.isAnonymous()) {
                author = message.getUser();
//            }
%>
        <tr bgcolor="#ffffff">
            <td width="40%">
                <a href="pending.jsp?forum=<%= forumID %>&thread=<%= message.getForumThread().getID() %>&message=<%= message.getID() %>"
                 ><%= message.getSubject() %></a>
            </td>
            <td width="20%">
                <%= message.getForumThread().getName() %>
            </td>
            <td width="20%">
                <%= message.getForumThread().getForum().getName() %>
            </td>
            <td width="10%" nowrap>
                <%  if (author != null) { %>
                    <%= author.getUsername() %>
                <%  } else { %>
                    Guest
                <%  } %>
            </td>
            <td width="10%" nowrap>
                <%= SkinUtils.formatDate(request, pageUser, message.getCreationDate()) %>
            </td>
        </tr>

    <%  } %>

    </table>
    </td></tr>
    </table>

<%  } %>

<%@ include file="footer.jsp" %>
