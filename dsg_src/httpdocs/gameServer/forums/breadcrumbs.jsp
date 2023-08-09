<%
   /**
    * $RCSfile: breadcrumbs.jsp,v $
    * $Revision: 1.13 $
    * $Date: 2002/12/20 22:49:31 $
    */
%>

<%@ page import="com.jivesoftware.util.ParamUtils,
                 com.jivesoftware.base.*,
                 com.jivesoftware.forum.*"
%>

<%@ taglib uri="jivetags" prefix="jive" %>

<% // Get parameters
   long categoryID = ParamUtils.getLongParameter(request, "categoryID", -1L);
   long forumID = ParamUtils.getLongParameter(request, "forumID", -1L);
   long userID = ParamUtils.getLongParameter(request, "userID", -1L);

   AuthToken authToken = null;
   try {
      authToken = AuthFactory.getAuthToken(request, response);
   } catch (Exception ignored) {
   }

   if (authToken == null) {
      authToken = AuthFactory.getAnonymousAuthToken();
   }
   ForumFactory forumFactory = ForumFactory.getInstance(authToken);
   ForumCategory category = null;
   Forum forum = null;
   User user = null;

   if (categoryID > 1L) {
      try {
         category = forumFactory.getForumCategory(categoryID);
      } catch (Exception ignored) {
      }
   }
   if (forumID > 0L) {
      try {
         forum = forumFactory.getForum(forumID);
      } catch (Exception ignored) {
      }
   }
   if (userID > 0L) {
      try {
         user = forumFactory.getUserManager().getUser(userID);
      } catch (Exception ignored) {
      }
   }

   // if the category is null, use the one from the forum
   if (category == null && forum != null) {
      if (forum.getForumCategory().getID() != forumFactory.getRootForumCategory().getID()) {
         category = forum.getForumCategory();
      }
   }
%>

<span class="jive-breadcrumbs">

<%-- Forum Home --%>
<a href="/gameServer/index.jsp">Home</a> &raquo;
<a href="index.jspa?categoryID=1"><jive:i18n key="global.forum_home"/></a>

<% if (user != null) { %>

    &raquo;
    <a href="<%= request.getContextPath() %>/gameServer/profile?viewName=<%= user.getUsername() %>&start=0"><%= user.getUsername() %></a>

<% } else { %>

    <% if (category != null) { %>
        &raquo;
        <a href="index.jspa?categoryID=<%= category.getID() %>"><%= category.getName() %></a>
    <% } %>
    <% if (forum != null) { %>
        &raquo;
        <a href="forum.jspa?forumID=<%= forumID %>&start=0"><%= forum.getName() %></a>

<% }
}
%>
</span>