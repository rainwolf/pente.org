<%
   /**
    *	$RCSfile: moderation.jsp,v $
    *	$Revision: 1.3 $
    *	$Date: 2003/01/08 22:45:47 $
    */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<% // Permission check
   if (!isSystemAdmin && !isCatAdmin && !isForumAdmin && !isModerator) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // get parameters
   long forumID = ParamUtils.getLongParameter(request, "forum", -1L);
   long threadID = ParamUtils.getLongParameter(request, "thread", -1L);
   long messageID = ParamUtils.getLongParameter(request, "message", -1L);
   boolean savePreset = ParamUtils.getBooleanParameter(request, "savePreset");
   boolean approveThread = ParamUtils.getBooleanParameter(request, "approveThread");
   boolean approveMessage = ParamUtils.getBooleanParameter(request, "approveMessage");
   boolean deleteThread = ParamUtils.getBooleanParameter(request, "deleteThread");
   boolean deleteMessage = ParamUtils.getBooleanParameter(request, "deleteMessage");
   int start = ParamUtils.getIntParameter(request, "start", 0);
   int range = ParamUtils.getIntParameter(request, "range", 10);

   // Get a list of moderated forums where moderation is enabled
   List moderatedForums = moderatedForums(forumFactory, true);
   int forumCount = moderatedForums.size();

   // Last time the user visited this page:
   Date lastVisited = new Date(SkinUtils.getLastVisited(request, response));
%>

<%@ include file="header.jsp" %>

<% // Title of this page and breadcrumbs
   String title = "Moderation Summary";
   String[][] breadcrumbs = {
      {"Main", "main.jsp"},
      {title, "moderation.jsp"}
   };
%>
<%@ include file="title.jsp" %>

<font size="-1">
   Below is a summary of forums you can moderate.
   If there are pending messages in a forum, click on the forum name to approve,
   edit or reject the messages.
</font>

<p>

<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr>
      <td>
         <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
            <tr bgcolor="#eeeeee">
               <td align="center"><font size="-2" face="verdana"><b>NEW</b></font></td>
               <td align="center"><font size="-2" face="verdana"><b>FORUM</b></font></td>
               <td align="center"><font size="-2" face="verdana"><b>PENDING MESSAGES</b></font></td>
               <td align="center"><font size="-2" face="verdana"><b>LAST<br>UPDATED</b></font></td>
            </tr>
            <% Iterator forums = moderatedForums.iterator();
               boolean displayedMessage = false;
               if (!forums.hasNext()) {
                  displayedMessage = true;
            %>
            <tr bgcolor="#ffffff">
               <td align="center" colspan="6"><font size="-1"><i>No forums to moderate.</i></font></td>
            </tr>
            <% }
               // skip the first 0->start forums
               int i = 0;
               while (i++ < start && forums.hasNext()) {
                  forums.next();
               }
               // show "range" number of forums
               i = 0;
               while (i++ < range && forums.hasNext()) {
                  Forum forum = (Forum) forums.next();
                  // indicate that it won't be necessary to print out the message:
                  displayedMessage = true;
                  ResultFilter modThreadFilter = new ResultFilter();
                  modThreadFilter.setModerationRangeMin(forum.getModerationDefaultThreadValue());
                  modThreadFilter.setModerationRangeMax(forum.getModerationDefaultThreadValue());
                  ResultFilter modMessageFilter = new ResultFilter();
                  modMessageFilter.setModerationRangeMin(forum.getModerationDefaultMessageValue());
                  modMessageFilter.setModerationRangeMax(forum.getModerationDefaultMessageValue());
                  int pendingMessageCount = forum.getMessageCount(modMessageFilter);

                  // Set the filter date ranges to the last time they visited this
                  // page
                  modThreadFilter.setModificationDateRangeMax(lastVisited);
                  modMessageFilter.setModificationDateRangeMax(lastVisited);

                  // Get the counts again, using the modified filter.
                  int oldPendingThreadCount = forum.getThreadCount(modThreadFilter);
                  int oldPendingMessageCount = forum.getMessageCount(modMessageFilter);

                  // Indicates if this forum has new pending messages since, the
                  // last visit of the admin
                  boolean newPendingMessages = (oldPendingMessageCount < pendingMessageCount);

                  // Indicates if message moderation is on.
                  boolean isMessageModOn = (
                     forum.getModerationDefaultMessageValue() < forum.getModerationMinMessageValue()
                  );
            %>
            <tr bgcolor="#ffffff">
               <td align="center" width="1%">
                  <% if (newPendingMessages) { %>
                  <img src="images/forum_new.gif" width="12" height="12" border="0">
                  <% } else { %>
                  <img src="images/forum_old.gif" width="12" height="12" border="0">
                  <% } %>
               </td>
               <td width="96%">
                  <font size="-1">
                     <% if (!isMessageModOn || pendingMessageCount == 0) { %>
                     <%= forum.getName() %>
                     <% } else { %>
                     <a href="pending-list.jsp?forum=<%= forum.getID() %>"><%= forum.getName() %>
                     </a>
                     <% } %>
                  </font>
               </td>
               <td width="1%" align="center">
                  <font size="-1">
                     <% if (isMessageModOn) { %>
                     <%= pendingMessageCount %>
                     <% } else { %>
                     N/A
                     <% } %>
                  </font>
               </td>
               <td width="1%" nowrap>
                  <font size="-1"></font>
                  <font size="-1"><%= SkinUtils.formatDate(request, pageUser, forum.getModificationDate()) %>
                  </font>
               </td>
            </tr>
            <% } %>

            <% // We may have not displayed a forum. If so, show a message.
               if (!displayedMessage) {
            %>
            <tr bgcolor="#ffffff">
               <td align="center" colspan="5"><font size="-1"><i>No forums to moderate.</i></font></td>
            </tr>
            <% } %>
         </table>
      </td>
   </tr>
</table>

<p>

   <font size="-2" face="verdana">
      <img src="images/forum_new.gif" width="12" height="12" border="0">
      = New pending messages since your last visit.<br>
   </font>

<p>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr>
      <% if (start > 0) { %>
      <td width="1%" nowrap><font size="-1"><a href="moderation.jsp?start=<%= start-range %>">Previous <%= range %>
         forums</a></font></td>
      <% } else { %>
      <td width="1%" nowrap><font size="-1">&nbsp;</font></td>
      <% } %>

      <% if (start > 0) { %>
      <td width="98%" align="center"><font size="-1"><a href="moderation.jsp?start=0">Front Page</a></font></td>
      <% } else { %>
      <td width="98%" nowrap><font size="-1">&nbsp;</font></td>
      <% } %>

      <% if (start + range < forumCount) { %>
      <td width="1%" nowrap><font size="-1"><a href="moderation.jsp?start=<%= start+range %>">Next <%= range %>
         forums</a></font></td>
      <% } else { %>
      <td width="1%" nowrap><font size="-1">&nbsp;</font></td>
      <% } %>
   </tr>
</table>

<p>

   <%@ include file="footer.jsp" %>
