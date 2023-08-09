<%@ page import="java.util.*,
                 java.sql.*,
                 org.pente.gameServer.tourney.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.core.*,
                 org.pente.game.*,
                 org.apache.log4j.*,
                 org.pente.jive.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.forum.*" %>

<%! private static Category log4j =
   Category.getInstance("org.pente.gameServer.web.client.jsp"); %>

<% Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());

   List current = resources.getTourneyStorer().getCurrentTournies();
   Forum newForum = null;

   if (request.getParameter("create") != null) {
      String eidStr = request.getParameter("eid");
      int eid = Integer.parseInt(eidStr);

      // get details
      Tourney tourney = resources.getTourneyStorer().getTourneyDetails(eid);

      // create new forum under tournaments sub-category
      ForumFactory forumFactory = ForumFactory.getInstance(
         new DSGAuthToken(22000000000002L)); // use dweebo user
      ForumCategory rootCategory = forumFactory.getRootForumCategory();
      ForumCategory tourneyCategory = null;
      for (Iterator it = rootCategory.getCategories(); it.hasNext(); ) {
         ForumCategory fc = (ForumCategory) it.next();
         if (fc.getName().equals("Tournaments")) {
            tourneyCategory = fc;
            break;
         }
      }
      newForum = forumFactory.createForum(tourney.getName(),
         "Annoucements for this tourney and for players to schedule matches.",
         tourneyCategory);

      // allow all registered players to have read access
      PermissionsManager pm = newForum.getPermissionsManager();
      pm.addAnonymousUserPermission(ForumPermissions.READ_FORUM);

      // allow all players in tourney to have read/write access
      List players = resources.getTourneyStorer().getTourneyPlayers(eid);
      for (Iterator it = players.iterator(); it.hasNext(); ) {
         TourneyPlayerData p = (TourneyPlayerData) it.next();
         DSGUser u = new DSGUser(p.getPlayerID());
         pm.addUserPermission(u, ForumPermissions.CREATE_THREAD);
         pm.addUserPermission(u, ForumPermissions.CREATE_MESSAGE);
         pm.addUserPermission(u, ForumPermissions.CREATE_MESSAGE_ATTACHMENT);
      }
   }
%>

<html>
<head>
   <title>Create Tournament Forum</title>
</head>

<body>

<h3>Create Tournament Forum</h3>

<% if (newForum != null) { %>
New forum
<a href="/gameServer/forums/forum.jspa?forumID=<%= newForum.getID() %>&start=0">
   <%= newForum.getName() %>
</a>
created.<br>
<% } %>

<form name="start" method="post" action="createTourneyForum.jsp">
   Create forum for which tourney:
   <select name="eid">
      <% for (Iterator it = current.iterator(); it.hasNext(); ) {
         Tourney d = (Tourney) it.next(); %>
      <option value="<%= d.getEventID() %>"><%= d.getName() %>
      </option>
      <% } %>
   </select>

   <input type="hidden" name="create" value="Y">
   <input type="submit" value="Start">
</form>

<br>
<a href=".">Back to admin</a>

</body>
</html>