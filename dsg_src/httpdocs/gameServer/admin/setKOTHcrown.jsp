<%@ page import="org.pente.database.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 java.sql.*" %>

<html>
<body>

<%
   String name = request.getParameter("name");
   String name1 = null;
   if (name != null && !name.equals("")) {

      DBHandler dbHandler = null;
      DSGPlayerStorer dsgPlayerStorer = null;

      Connection con = null;
      PreparedStatement stmt = null;
      ResultSet result = null;


      try {
         dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());
         dsgPlayerStorer = (DSGPlayerStorer) application.getAttribute(DSGPlayerStorer.class.getName());
         con = dbHandler.getConnection();

         DSGPlayerData data = dsgPlayerStorer.loadPlayer(name);
         if (data == null) {
%> <b>Player data not found</b><br><br> <%
} else {
   // add player to list of donors
   Resources resources = (Resources) application.getAttribute(Resources.class.getName());
   CacheDSGPlayerStorer d = (CacheDSGPlayerStorer) resources.getDsgPlayerStorer();

   stmt = con.prepareStatement("select name_lower from player where pid in (select pid from dsg_player_game where tourney_winner='4')");
   ResultSet rs = stmt.executeQuery();
   if (rs.next()) {
      name1 = rs.getString("name_lower");
   }

   stmt = con.prepareStatement("update dsg_player_game set tourney_winner='0' where tourney_winner='4'");
   stmt.executeUpdate();


   stmt = con.prepareStatement("update dsg_player_game set tourney_winner='4' where pid=? and game=1 and computer='N'");
   stmt.setLong(1, data.getPlayerID());
   stmt.executeUpdate();

   d.refreshPlayer(name);
   d.refreshPlayer(name1);

%> <b>Crown change from <%=name1%> to <%=name%> successful</b><br><br> <%
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
%>


<form name="addDonor" method="get" action="setKOTHcrown.jsp">
   <br>
   New KOTH winner: <input type="text" name="name">
   <input type="submit" value="submit">
   <br>
   (make sure the winner played at least 1 rated game)

</form>

<br>
<a href=".">Back to admin</a>

</body>
</html>

