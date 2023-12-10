<%@ page import="org.pente.database.*, java.sql.*, java.text.*" %>


<%! private static final DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss"); %>

<html>
<body>


<%

   String event = request.getParameter("event");
   String round = request.getParameter("round");
   String section = request.getParameter("section");

   String command = request.getParameter("command");
   if (command != null && command.equals("update")) {

      String[] gids = request.getParameterValues("gid");
      if (gids != null && event != null && round != null && section != null) {

         Connection con = null;
         PreparedStatement stmt = null;

         DBHandler dbHandler = null;

         try {
            dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());

            con = dbHandler.getConnection();
            stmt = con.prepareStatement("update pente_game " +
               "set event_id = ?, round = ?, section = ? " +
               "where gid = ?");

            for (int i = 0; i < gids.length; i++) {
               stmt.setString(1, event);
               stmt.setString(2, round);
               stmt.setString(3, section);
               stmt.setLong(4, Long.valueOf(gids[i]).longValue());
               stmt.executeUpdate();
            }
         } finally {
            if (stmt != null) {
               stmt.close();
            }
            if (dbHandler != null) {
               dbHandler.freeConnection(con);
            }
         }
      } else { %>
Update failed.
<% }
} else {
   if (event == null) {
      event = "";
   }
   if (round == null) {
      round = "";
   }
   if (section == null) {
      section = "";
   }
}

   String p1 = request.getParameter("p1");
   String p2 = request.getParameter("p2");

%>
<form method="get" action="markTourneyGame.jsp" name="t3">

   <%

      if (p1 != null && p2 != null) {

         Connection con = null;
         PreparedStatement stmt = null;
         ResultSet result = null;

         DBHandler dbHandler = null;

         try {
            dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());

            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
               "select g.gid, p1.name, p2.name, g.play_date, g.winner, g.event_id, g.round, g.section " +
                  "from pente_game g, player p1, player p2 " +
                  "where g.site_id = 2 " +
                  "and p1.pid = g.player1_pid " +
                  "and p2.pid = g.player2_pid " +
                  "and " +
                  "((p1.name = ? and p2.name = ?) or " +
                  " (p1.name = ? and p2.name = ?)) " +
                  "order by play_date");

            stmt.setString(1, p1);
            stmt.setString(2, p2);
            stmt.setString(3, p2);
            stmt.setString(4, p1);

            result = stmt.executeQuery();
   %>

   <table border="1">
      <tr bgcolor="#8b0000">
         <td>&nbsp;</td>
         <td><font color="white"><b>Game ID</b></font></td>
         <td><font color="white"><b>Player 1</b></font></td>
         <td><font color="white"><b>Player 2</b></font></td>
         <td><font color="white"><b>Date</b></font></td>
         <td><font color="white"><b>Winner</b></font></td>
         <td><font color="white"><b>Event ID</b></font></td>
         <td><font color="white"><b>Round</b></font></td>
         <td><font color="white"><b>Section</b></font></td>
      </tr>

      <% int i = 0;
         while (result.next()) {
            String color = (i++ % 2 == 0) ? "white" : "#dcdcdc"; %>
      <tr bgcolor="<%= color %>">
         <td><input type="checkbox" value="<%= result.getLong(1) %>" name="gid"></td>
         <td><%= result.getLong(1) %>
         </td>
         <td><%= result.getString(2) %>
         </td>
         <td><%= result.getString(3) %>
         </td>
         <td><%= dateFormat.format(new java.util.Date(result.getTimestamp(4).getTime())) %>
         </td>
         <td><%= result.getInt(5) %>
         </td>
         <td><%= result.getInt(6) %>
         </td>
         <td><%= result.getString(7) %>
         </td>
         <td><%= result.getString(8) %>
         </td>
      </tr>
      <% }
      %>
   </table>
   <br>
   <%

         } finally {
            if (result != null) {
               result.close();
            }
            if (stmt != null) {
               stmt.close();
            }
            if (dbHandler != null) {
               dbHandler.freeConnection(con);
            }
         }
      } else {
         p1 = "";
         p2 = "";
      }
   %>
   P1 <input type="text" name="p1" value="<%= p1 %>"><br>
   P2 <input type="text" name="p2" value="<%= p2 %>"><br>
   <input type="hidden" name="command" value="">
   <input type="button" value="Lookup" onclick="document.t3.submit()"><br>
   <br>
   Event ID <input type="text" name="event" value="<%= event %>"><br>
   Round <input type="text" name="round" value="<%= round %>"><br>
   Section <input type="text" name="section" value="<%= section %>"><br>
   <input type="button" value="Update" onclick="document.t3.command.value='update'; document.t3.submit()">

</form>

</body>
</html>
