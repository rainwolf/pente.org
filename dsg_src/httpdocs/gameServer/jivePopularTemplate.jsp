<%@ page import="com.jivesoftware.forum.*,com.jivesoftware.forum.util.SkinUtils" %>
<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<%
   if (forumFactory.getPopularThreads().hasNext()) {
      int i = 0; %>
<table border="0" width="100%" cellspacing="0" cellpadding="0">
   <% for (Iterator it = forumFactory.getPopularThreads(); it.hasNext(); ) {
      ForumThread thread = (ForumThread) it.next();
      Forum forum = thread.getForum(); %>
   <tr <% if (i % 2 == 1) { %>style="background: #e5e5e5;"<% } %>>
      <td><a
         href="<%= request.getContextPath() %>/gameServer/forums/thread.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>"><%= thread.getName() %>
      </a>
      </td>
   </tr>
   <tr <% if (i++ % 2 == 1) { %>style="background: #e5e5e5;"<% } %>>
      <% ForumMessage lastPost = SkinUtils.getLastPost(thread); %>
      <td style="font-size:10px">
         Last Post: <%= dateFormat.format(thread.getModificationDate()) %> by:
         <% if (lastPost != null && lastPost.getUser() != null) { %>
         <a href="/gameServer/forums/thread.jspa?forumID=<%= lastPost.getForumThread().getForum().getID() %>&threadID=<%= lastPost.getForumThread().getID() %>&messageID=<%= lastPost.getID() %>#<%= lastPost.getID() %>"
         ><%= lastPost.getUser().getUsername()  %> &raquo;</a>
         <% } %>
      </td>
   </tr>
   <%
      } %>
</table>
<% } else { %>
<jive:i18n key="populartopics.no_popular_discussions"/>
<% } %>
