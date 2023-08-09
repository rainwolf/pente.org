<%
   /**
    *	$RCSfile: forumContent_lock.jsp,v $
    *	$Revision: 1.1 $
    *	$Date: 2002/08/16 06:52:22 $
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

<% // Permission check
   if (!isSystemAdmin && !isForumAdmin && !isModerator) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Get parameters
   long forumID = ParamUtils.getLongParameter(request, "forum", -1L);
   long[] threadIDs = ParamUtils.getLongParameters(request, "thread", -1L);
   boolean doLock = request.getParameter("lock") != null;
   boolean cancel = request.getParameter("cancel") != null;

   // Load the forum we're working with
   Forum forum = forumFactory.getForum(forumID);

   boolean errors = false;

   // Load a list of threads
   List threads = new LinkedList();
   for (int i = 0; i < threadIDs.length; i++) {
      try {
         ForumThread thread = forum.getThread(threadIDs[i]);
         threads.add(thread);
      } catch (Exception ignored) {
      }
   }
   errors = threads.size() == 0;

   // Cancel back to the forumContent_thread.jsp page
   if (cancel) {
      response.sendRedirect("forumContent.jsp?forum=" + forumID);
      return;
   }

   if (doLock) {
      for (int i = 0; i < threads.size(); i++) {
         ForumThread thread = (ForumThread) threads.get(i);
         boolean locked = "true".equals(thread.getProperty("locked"));
         thread.setProperty("locked", String.valueOf(!locked));
      }
      response.sendRedirect("forumContent.jsp?forum=" + forumID);
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
        {"Lock Thread", ""}
    };
%>
   <%@ include file="title.jsp" %>

      <%  if (errors) { %>

   <font size="-1">
      Error: No threads to lock.
   </font>

<p>
<form action="forumContent.jsp">
   <input type="hidden" name="forum" value="<%= forumID %>">
   <center>
      <input type="submit" value="Back to thread list">
   </center>
</form>

<% } else { // no errors %>

<font size="-1">
   Use the form below to lock or unlock the specified thread. Locking the thread prevents people
   from replying to it.
</font>

<p>

<form action="forumContent_lock.jsp">
   <input type="hidden" name="forum" value="<%= forumID %>">
   <% for (int i = 0; i < threads.size(); i++) {
      ForumThread thread = (ForumThread) threads.get(i);
   %>
   <input type="hidden" name="thread" value="<%= thread.getID() %>">
   <% } %>

   <font size="-1">
      Please confirm you want to lock or unlock the following threads:
      <p>
      <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
         <tr>
            <td>
               <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
                  <tr bgcolor="#eeeeee">
                     <td align="center"><font size="-2" face="verdana"><b>SUBJECT</b></font></td>
                     <td align="center"><font size="-2" face="verdana"><b>ACTION</b></font></td>
                     <td align="center"><font size="-2" face="verdana"><b>REPLIES</b></font></td>
                     <td align="center"><font size="-2" face="verdana"><b>AUTHOR</b></font></td>
                     <td align="center"><font size="-2" face="verdana"><b>LAST MODIFIED</b></font></td>
                  </tr>
                  <% for (int i = 0; i < threads.size(); i++) {
                     ForumThread thread = (ForumThread) threads.get(i);
                     User author = thread.getRootMessage().getUser();
                     boolean isLocked = "true".equals(thread.getProperty("locked"));
                  %>
                  <tr bgcolor="#ffffff">
                     <td width="97%">
                        <% if (isLocked) { %>
                        <img src="images/lock.gif" width="9" height="12" border="0">
                        <% } %>
                        <font size="-1"><%= thread.getName() %>
                        </font>
                     </td>
                     <% if (isLocked) { %>
                     <td align="center" width="1%" nowrap><font size="-2" face="verdana">UNLOCK</font></td>
                     <% } else { %>
                     <td align="center" width="1%" nowrap><font size="-2" face="verdana">LOCK</font></td>
                     <% } %>

                     <td align="center" width="1%" nowrap><font size="-1"><%= thread.getMessageCount() - 1 %>
                     </font></td>
                     <td align="center" width="1%" nowrap>
                        <% if (author != null) { %>
                        <font size="-1"><%= author.getUsername() %>
                        </font>
                        <% } else { %>
                        <font size="-1"><i>Guest</i></font>
                        <% } %>
                     </td>
                     <td align="center" width="1%" nowrap><font
                        size="-1">&nbsp;<%= SkinUtils.formatDate(request, pageUser, thread.getModificationDate()) %>&nbsp;</font>
                     </td>
                  </tr>
                  <% } %>
               </table>
            </td>
         </tr>
      </table>
      <p>
      <center>
         <input type="submit" name="lock" value="Lock/Unlock">
         <input type="submit" name="cancel" value="Cancel">
      </center>
   </font>

</form>

<% } %>

<p>

   <%@ include file="footer.jsp" %>
