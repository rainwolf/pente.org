<%
   /**
    * $RCSfile: forum-row.jsp,v $
    * $Revision: 1.15 $
    * $Date: 2002/12/20 08:07:29 $
    */
%>

<%@ page import="com.jivesoftware.forum.util.SkinUtils,
                 com.jivesoftware.forum.ForumMessage,
                 com.jivesoftware.forum.action.util.Guest"
%>

<% // This page assumes 3 variables:
   // forum - a Forum object
   // status - an int, used to compute the row number and print a css tag
   // action - an instance of ForumActionSupport (or a subclass).
%>

<tr class="<%= ((status++%2==1)?"jive-odd":"jive-even") %>" valign="top">
   <td class="jive-bullet" width="1%">
      <% if (action.isUnread(forum)) { %>

      <img src="images/unread.gif" width="9" height="9" border="0">

      <% } else if (action.isUpdated(forum)) { %>

      <img src="images/updated.gif" width="9" height="9" border="0">

      <% } else { %>

      <img src="images/read.gif" width="9" height="9" border="0">

      <% } %>
   </td>
   <td class="jive-forum-name" width="97%">

      <!-- Forum Name -->
      <a href="forum.jspa?forumID=<%= forum.getID() %>&start=0"
      ><%= forum.getName() %>
      </a>

      <!-- Forum Description (if it exists) -->
      <% if (forum.getDescription() != null) { %>
      <span class="jive-description">
            <br><%= forum.getDescription() %>
            </span>
      <% } %>

   </td>
   <td class="jive-counts" width="1%" nowrap>

      <!-- Thread and Message Counts -->
      <%= action.getNumberFormat().format(forum.getThreadCount()) %>
      /
      <%= action.getNumberFormat().format(forum.getMessageCount()) %>

   </td>
   <td class="jive-date" width="1%" nowrap>

      <!-- Modification Date and Last Post -->
      <%= action.getDateFormat().format(forum.getModificationDate()) %>

      <!-- Show the last post link if that feature is enabled -->
      <jive:property if="skin.default.showLastPostLink">
         <% ForumMessage lastPost = SkinUtils.getLastPost(forum);
            if (lastPost != null) {
         %>

         <span class="jive-last-post">
                <br>
                <%-- by: {LAST_POST} --%>
                <jive:i18n key="global.last_post_by">
                   <jive:arg>

                      <% if (lastPost.getUser() != null) { %>

                      <a href="thread.jspa?forumID=<%= lastPost.getForumThread().getForum().getID() %>&threadID=<%= lastPost.getForumThread().getID() %>&messageID=<%= lastPost.getID() %>#<%= lastPost.getID() %>"
                      ><%= ((lastPost.getUser().getUsername() != null) ? lastPost.getUser().getUsername() : "Deleted user") %> &raquo;</a>

                      <% } else {
                         Guest guest = new Guest();
                         guest.setMessage(lastPost);
                      %>
                      <span class="jive-guest">
                            <a href="thread.jspa?forumID=<%= lastPost.getForumThread().getForum().getID() %>&threadID=<%= lastPost.getForumThread().getID() %>&messageID=<%= lastPost.getID() %>#<%= lastPost.getID() %>"
                            ><%= guest.getDisplay() %> &raquo;</a>
                            </span>

                      <% } %>

                   </jive:arg>
                </jive:i18n>
                </span>

         <% } %>

      </jive:property>
   </td>
</tr>