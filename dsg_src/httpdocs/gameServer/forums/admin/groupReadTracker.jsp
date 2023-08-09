<%
   /**
    *	$RCSfile: groupReadTracker.jsp,v $
    *	$Revision: 1.3.4.1 $
    *	$Date: 2003/03/26 00:12:26 $
    */
%>

<%@ page import="java.util.*,
                 java.net.URLEncoder,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils" %>

<%@ include file="global.jsp" %>

<%!
   // Returns an html padder:
   private String pad(int count) {
      String padding = "&nbsp;&nbsp;";
      StringBuffer buf = new StringBuffer(padding.length() * count);
      for (int i = 0; i < count; i++) {
         buf.append(padding);
      }
      return buf.toString();
   }
%>

<% // Security check
   if (!isSystemAdmin && !isGroupAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // get parameters
   long groupID = ParamUtils.getLongParameter(request, "group", -1L);
   long[] userIDs = ParamUtils.getLongParameters(request, "user", -1L);
   String objectID = ParamUtils.getParameter(request, "objectID");
   long catID = -1L;
   long forumID = -1L;
   boolean isCat = false;
   boolean isForum = false;
   if (objectID != null) {
      if (objectID.startsWith("c")) {
         try {
            catID = Long.parseLong(objectID.substring(1, objectID.length()));
            isCat = true;
         } catch (Exception ignored) {
         }
      } else if (objectID.startsWith("f")) {
         try {
            forumID = Long.parseLong(objectID.substring(1, objectID.length()));
            isForum = true;
         } catch (Exception ignored) {
         }
      }
   }

   // Get user and group managers
   UserManager userManager = forumFactory.getUserManager();
   GroupManager groupManager = forumFactory.getGroupManager();

   // Load the forum or cat we're viewing:
   ForumCategory category = null;
   Forum forum = null;
   if (catID > -1L) {
      try {
         category = forumFactory.getForumCategory(catID);
      } catch (Exception ignored) {
      }
   }
   if (forumID > -1L) {
      try {
         forum = forumFactory.getForum(forumID);
      } catch (Exception ignored) {
      }
   }

   // Load the group
   Group group = groupManager.getGroup(groupID);

   // Total number of members in this group
   int memberCount = group.getMemberCount();

   // An iterator of categories this group has explict read access to:
   Iterator catsWithGroupRead = categoriesWithGroupRead(forumFactory, group);

   // If the category is null, make the category the first one in the iterator:
   if (category == null && catsWithGroupRead.hasNext()) {
      category = (ForumCategory) catsWithGroupRead.next();
      // reset the iterator
      catsWithGroupRead = categoriesWithGroupRead(forumFactory, group);
   }

   // Load the users
   java.util.List users = new java.util.LinkedList();
   for (int i = 0; i < userIDs.length; i++) {
      try {
         User user = userManager.getUser(userIDs[i]);
         users.add(user);
      } catch (Exception ignored) {
      }
   }

   // Iterator of members
   Iterator members = group.members();
%>

<% // special onload command to load the sidebar
   onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=users';\"";
%>
<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Read Message Summary";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Groups Summary", "groups.jsp"},
        {"Groups Members", "groupMembers.jsp?group="+groupID},
        {title, ""}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      This page details unread topics and messages over all forums this group has read access to.
   </font>

<form action="groupReadTracker.jsp">
   <% for (int i = 0; i < userIDs.length; i++) { %>
   <input type="hidden" name="user" value="<%= userIDs[i] %>">
   <% } %>
   <input type="hidden" name="group" value="<%= groupID %>">
   <font size="-1">
      <% if (category != null || forum != null) { %>
      <% if (forum != null) { %>
      Current Forum: <b><%= forum.getName() %>
   </b>
      <% } else if (category != null) { %>
      Current Category: <b><%= category.getName() %>
   </b>
      <% } %>
      <br>
      <% } %>
      Choose a category or forum to view:
   </font>
   <select size="1" name="objectID"
           onchange="this.form.submit();">
      <% // Loop through all categories this group has read access to:
         int firstCatDepth = -1;
         boolean noCatsToRead = !catsWithGroupRead.hasNext();
         if (noCatsToRead) {
      %>
      <option value="*" style="font-style:italic;"> - No Categories -

            <%  }
    while (catsWithGroupRead.hasNext()) {
        ForumCategory cat = (ForumCategory)catsWithGroupRead.next();
        // used for indents:
        int catDepth = cat.getCategoryDepth();
        if (firstCatDepth == -1) {
            firstCatDepth = catDepth;
        }
%>
      <option value="c<%= cat.getID() %>" style="font-weight:bold;"
         <%= ((isCat && cat.getID()==category.getID())?" selected":"") %>>
            <%= pad(catDepth-firstCatDepth) %>&#149; <%= cat.getName() %>

            <%  // Print out forums in the category:
        for (Iterator forums=cat.getForums(); forums.hasNext();) {
            Forum f = (Forum)forums.next();
    %>
      <option value="f<%= f.getID() %>"
         <%= ((isForum && f.getID()==forum.getID())?" selected":"") %>>
            <%= pad(catDepth-firstCatDepth) %> &nbsp;&nbsp; <%= f.getName() %>

            <%  } %>

            <%  } %>
   </select>
</form>

</body>
</html>
