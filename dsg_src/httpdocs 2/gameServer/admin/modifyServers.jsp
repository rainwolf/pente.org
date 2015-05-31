<%@ page import="java.util.*, java.sql.*,
                 org.pente.gameServer.server.*, 
                 org.pente.gameServer.core.*,
                 org.pente.game.*,
                 org.apache.log4j.* "%>

<%! private static Category log4j = 
        Category.getInstance("org.pente.gameServer.web.client.jsp"); %>
    
<% Resources resources = (Resources) application.getAttribute(
       Resources.class.getName());

   
   // get list of game events for new servers
   List gameEvents = new ArrayList(20);
   Connection con = null;
   PreparedStatement stmt = null;
   ResultSet results = null;
   try {
       con = resources.getDbHandler().getConnection();
       stmt = con.prepareStatement(
           "select game, eid, name from game_event where site_id=2");
       results = stmt.executeQuery();
       while (results.next()) {
           GameEventData e = new SimpleGameEventData();
           e.setGame(results.getInt(1));
           e.setEventID(results.getInt(2));
           e.setName(results.getString(3));
           gameEvents.add(e);
       }
	} finally {
	    if (results != null) {
	        results.close();
	    }
	    if (stmt != null) {
	        stmt.close();
	    }
	    if (con != null) {
	    	resources.getDbHandler().freeConnection(con);
	    }
	}


   String action = request.getParameter("action");
   if (action != null && action.equals("add")) {
       String name = request.getParameter("name");
       int port = Integer.parseInt(request.getParameter("port"));
       String ge[] = request.getParameterValues("event");
       boolean tournament = "Y".equals(request.getParameter("tournament"));
       boolean privateRoom = "Y".equals(request.getParameter("private"));
       ServerData data = new ServerData();
       data.setName(name);
       data.setPort(port);
       for (int i = 0; i < ge.length; i++) {
           int event = Integer.parseInt(ge[i]);
           for (Iterator it = gameEvents.iterator(); it.hasNext();) {
               GameEventData g = (GameEventData) it.next();
               if (g.getEventID() == event) {
                   data.addGameEvent(g);
                   break;
               }
           }
       }
       data.setTournament(tournament);
       data.setPrivateServer(privateRoom);
       
       MySQLServerStorer.addServer(resources.getDbHandler(), data);
	   
       Server server = new Server(resources, data);
       resources.addServer(server);
   }
   else if (action != null && action.equals("remove")) {
       long sid = Long.parseLong(request.getParameter("sid"));

       MySQLServerStorer.removeServer(resources.getDbHandler(), sid);
       
       Server server = resources.removeServer(sid);
       server.destroy();
   }

%>

<html>
<head><title>Modify servers</title></head>
<body>

<b>Add New Server</b>
<form name="addServer" action="modifyServers.jsp" method="post">
  <input type="hidden" name="action" value="add">
  <table border="0" cellspacing="0" cellpadding="0">
    <tr><td valign="top">Name:</td><td><input type="text" name="name"></td></tr>
    <tr><td valign="top">Port:</td><td><input type="text" name="port"></td></tr>
    <tr><td valign="top">Game/Events:</td>
        <td>
          <select size="5" multiple name="event">
          <% for (Iterator it = gameEvents.iterator(); it.hasNext();) {
                 GameEventData e = (GameEventData) it.next(); %>
              <option value="<%= e.getEventID() %>">
                <%= GridStateFactory.getGameName(e.getGame()) %>
                 / <%= e.getName() %></option>
          <% } %>
          </select>
        </td>
    </tr>
    <tr><td valign="top">Tournament:</td>
        <td><input type="radio" name="tournament" value="Y">Yes
            <input type="radio" name="tournament" value="N">No</td>
    </tr>
    <tr><td valign="top">Private:</td>
        <td><input type="radio" name="private" value="Y">Yes
            <input type="radio" name="private" value="N">No</td>
    </tr>
    <tr><td valign="top"><input type="submit" value="Add"></td><td>&nbsp;</td></tr>
  </table>
</form>

<%@ include file="servers.jsp" %>

<br>
<a href=".">Back to admin</a>

</body>
</html>