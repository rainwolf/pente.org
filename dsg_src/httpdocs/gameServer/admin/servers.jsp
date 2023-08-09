<b><a href="modifyServers.jsp">Active servers</a></b><br>
<table border="1">
   <tr>
      <th>Server ID</th>
      <th>Name</th>
      <th>Port</th>
      <th>Game/Events</th>
      <th>Tournament</th>
      <th>Private</th>
      <th>Remove</th>
      <th>Messages</th>
   </tr>

   <% for (Iterator it = resources.getServerData().iterator(); it.hasNext(); ) {
      ServerData data = (ServerData) it.next();
      String privateRoom = data.isPrivateServer() ?
         "<a href='modifyServerAccess.jsp?sid=" + data.getServerId() + "'>Yes" : "No";
   %>
   <tr>
      <td valign="top"><%= data.getServerId() %>
      </td>
      <td valign="top"><%= data.getName() %>
      </td>
      <td valign="top"><%= data.getPort() %>
      </td>
      <%
         String eventData = "";
         for (Iterator it2 = data.getGameEvents().iterator(); it2.hasNext(); ) {
            GameEventData e = (GameEventData) it2.next();
            eventData += GridStateFactory.getGameName(e.getGame()) + "/" +
               e.getName() + "<br>";
         }
      %>
      <td valign="top"><%= eventData %>
      </td>
      <td valign="top"><%= data.isTournament() ? "Yes" : "No" %>
      </td>
      <td valign="top"><%= privateRoom %>
      </td>
      <td valign="top"><a href="modifyServers.jsp?action=remove&sid=<%= data.getServerId() %>">
         Remove</a></td>
      <td valign="top"><a href="modifyServerMessages.jsp?sid=<%= data.getServerId() %>">
         Messages</a></td>
   </tr>
   <% } %>

</table>