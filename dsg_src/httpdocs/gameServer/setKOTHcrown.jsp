<%@ page import="org.pente.database.*, 
                 org.pente.gameServer.core.*, 
				 org.pente.gameServer.server.*,
				 java.sql.*" %>

<html>
<body>

<%
    String sendername = request.getParameter("name");
    if (sendername.equals("invictus") || sendername.equals("rainwolf")) {
        String name = request.getParameter("kothname");
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
    			}
    			else {
    					// add player to list of donors
                    Resources resources = (Resources) application.getAttribute(Resources.class.getName());
                    CacheDSGPlayerStorer d = (CacheDSGPlayerStorer) resources.getDsgPlayerStorer();
        
                    stmt = con.prepareStatement("select name_lower from player where pid in (select pid from dsg_player_game where tourney_winner='4')");
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                       name1 = rs.getString("name_lower");
                    }

                    long lastPID = 0, streak = 0;
                    stmt = con.prepareStatement("select pid, max(date), streak from dsg_koth");
                    rs = stmt.executeQuery();
                    if (rs.next()) {
                       lastPID = rs.getLong("pid");
                       if (lastPID == data.getPlayerID()) {
                           streak = rs.getLong("streak");
                       }
                    }

        
					stmt = con.prepareStatement("update dsg_player_game set tourney_winner='0' where tourney_winner='4'");
					stmt.executeUpdate();


					stmt = con.prepareStatement("update dsg_player_game set tourney_winner='4' where pid=? and game=1 and computer='N'");
					stmt.setLong(1, data.getPlayerID());
					stmt.executeUpdate();

                    d.refreshPlayer(name);
                    d.refreshPlayer(name1);

                    stmt = con.prepareStatement("INSERT INTO dsg_koth (pid, date, streak) VALUES (?, NOW(), ?)");
                    stmt.setLong(1, data.getPlayerID());
                    stmt.setLong(2, streak + 1);
                    stmt.executeUpdate();
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
    } else {
        %>
        <%@include file="../four04.jsp" %>
        <%
    }
%>

</body>
</html>

