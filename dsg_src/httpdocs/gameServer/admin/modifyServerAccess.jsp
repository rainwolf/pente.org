<%@ page import="java.util.*,
                 java.sql.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.core.*,
                 org.pente.game.*,
                 org.apache.log4j.*" %>

<%! private static Category log4j =
   Category.getInstance("org.pente.gameServer.web.client.jsp"); %>

<% Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());

   String error = null;
   String serverIDStr = request.getParameter("sid");
   int serverID = Integer.parseInt(serverIDStr);
   ServerData data = resources.getServerData(serverID);

   String action = request.getParameter("action");
   if (action != null && action.equals("Add")) {
      String player = request.getParameter("player");
      DSGPlayerData p = resources.getDsgPlayerStorer().loadPlayer(player);
      if (p == null) {
         error = "Player " + player + " not found.";
      } else if (data.getPlayers().contains(player)) {
         error = "Player already has access.";
      } else {
         data.addPlayer(player);
         MySQLServerStorer.addPlayerAccess(resources.getDbHandler(), data, p);
      }
   } else if (action != null && action.equals("Remove")) {
      String player = request.getParameter("players");
      DSGPlayerData p = resources.getDsgPlayerStorer().loadPlayer(player);
      data.removePlayer(player);
      if (p != null) {
         MySQLServerStorer.removePlayerAccess(resources.getDbHandler(), data, p);
      }

   }

%>

<html>
<head><title>Modify server access</title></head>
<body>

<b>Modify server access</b><br>

<% if (error != null) { %> <h3><font color="red"><%= error %>
</font></h3> <% } %>

Server: <%= data.getName() %><br>
<a href="modifyServers.jsp">Back to Modify Servers</a><br>
<br>
<form name="modifyPlayers" action="modifyServerAccess.jsp" method="post">

   <input type="hidden" name="sid" value="<%= serverIDStr %>">
   <select size="5" name="players">
      <% for (int i = 0; i < data.getPlayers().size(); i++) {
         String player = (String) data.getPlayers().elementAt(i); %>
      <option value="<%= player %>"><%= player %>
      </option>
      <% } %>
   </select>
   <br>
   <input type="submit" name="action" value="Remove">
   <br><br>
   <input type="text" name="player"><br>
   <input type="submit" name="action" value="Add">
</form>

</body>
</html>