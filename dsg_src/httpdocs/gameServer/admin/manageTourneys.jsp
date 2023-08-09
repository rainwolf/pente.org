<%@ page import="java.util.*,
                 java.sql.*,
                 org.pente.gameServer.tourney.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.core.*,
                 org.pente.game.*,
                 org.apache.log4j.*" %>

<%! private static Category log4j =
   Category.getInstance("org.pente.gameServer.web.client.jsp"); %>

<% Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());

   List current = resources.getTourneyStorer().getCurrentTournies();
%>

<html>
<head>
   <title>Manage Tournament</title>
</head>

<body>

<h3>Manage Tournament</h3>

<form name="manage" method="post" action="manageTourney.jsp">
   Manage which tourney:
   <select name="eid">
      <% for (Iterator it = current.iterator(); it.hasNext(); ) {
         Tourney d = (Tourney) it.next(); %>
      <option value="<%= d.getEventID() %>"><%= d.getName() %>
      </option>
      <% } %>
   </select>
   <input type="submit" value="Manage">
</form>

<br>
<a href=".">Back to admin</a>

</body>
</html>