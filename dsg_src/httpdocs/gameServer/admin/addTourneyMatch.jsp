<%@ page import="org.pente.database.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 java.sql.*" %>

<html>
<body>

<%

   String player1 = request.getParameter("player1");
   String player2 = request.getParameter("player2");
   String eventID = request.getParameter("eventID");
   String round = request.getParameter("round");

   if (request.getParameter("save") != null) {


      if (player1 == null || eventID == null || round == null) { %>
<b>Something is null</b><br><br> <%
} else {

   DBHandler dbHandler = null;
   DSGPlayerStorer dsgPlayerStorer = null;

   Connection con = null;
   PreparedStatement stmt = null;
   ResultSet result = null;


   try {
      dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());
      dsgPlayerStorer = (DSGPlayerStorer) application.getAttribute(DSGPlayerStorer.class.getName());
      con = dbHandler.getConnection();

      boolean found = true;
      DSGPlayerData data1 = dsgPlayerStorer.loadPlayer(player1);
      if (data1 == null) {
         found = false;
%> <b>Player 1 data not found</b><br><br> <%
   }
   DSGPlayerData data2 = dsgPlayerStorer.loadPlayer(player2);
   if (data2 == null) {
      found = false;
%> <b>Player 2 data not found</b><br><br> <%
   }

   if (found) {

      stmt = con.prepareStatement(
         "insert into dsg_tournament_results" +
            "(pid1, pid2, event_id, round, section) " +
            "values(?, ?, ?, ?, 1)");

      stmt.setLong(1, data1.getPlayerID());
      stmt.setLong(2, data2.getPlayerID());
      stmt.setString(3, eventID);
      stmt.setString(4, round);
      stmt.executeUpdate();

%> <b>Add tourney match successful</b><br><br> <%

            }
         } catch (Throwable t) {
            throw t;
         } finally {
            if (result != null) {
               result.close();
            }
            if (stmt != null) {
               stmt.close();
            }
            if (con != null) {
               dbHandler.freeConnection(con);
            }
         }
      }
   }
%>


<form name="addTourneyMatch" method="get" action="addTourneyMatch.jsp">
   <input type="hidden" name="save" value="yes">

   Player 1: <input type="text" name="player1"><br>
   Player 2: <input type="text" name="player2"><br>
   Event ID: <input type="text" name="eventID" value="<%= eventID != null ? eventID : "" %>"><br>
   Round: <input type="text" name="round" value="<%= round != null ? round : "" %>"><br>

   <br>
   <input type="submit" value="submit">

</form>

</body>
</html>

