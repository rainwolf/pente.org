<%@ page import="org.pente.database.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.client.*, 
                 org.pente.gameServer.server.*, 
                 org.pente.gameServer.client.web.*,
                 org.pente.game.*, 
                 org.pente.turnBased.*,
                 org.pente.turnBased.web.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*,
                 java.text.*,
                 java.util.*" 
%><%@ page contentType="text/html; charset=UTF-8" %><%
String loggedInStr = (String) request.getAttribute("name");
 if (loggedInStr == null) {
    response.sendRedirect("../index.jsp");
   } %><%  
    ServletContext ctx = getServletContext();
    Resources globalResources = (Resources) ctx.getAttribute(Resources.class.getName());


SessionListener sessionListener = (SessionListener) application.getAttribute(SessionListener.class.getName());
List<WhosOnlineRoom> rooms = new ArrayList(WhosOnline.getPlayers(globalResources, sessionListener));

        for (Iterator it = globalResources.getServerData().iterator(); it.hasNext();) {
            String serverAndPlayers = "";
            ServerData data = (ServerData) it.next();
            String serverName = data.getName();
            if ("Go".equals(serverName)) {
                continue;
            }
            boolean empty = true;
            for (WhosOnlineRoom room : rooms) {
                if (serverName.equals(room.getName())) {
                    empty = false;
                    serverAndPlayers = data.getPort() + " " + serverName + " (" + room.getPlayers().size() + "):";
                   for (DSGPlayerData d : room.getPlayers()) {
                       DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(GridStateFactory.TB_PENTE); 
                        serverAndPlayers = serverAndPlayers + d.getName() + "," + ((dsgPlayerGameData != null)?(int) Math.round(dsgPlayerGameData.getRating()):"1600") + "," + (d.hasPlayerDonated()?d.getNameColorRGB():0) + "," + d.getTourneyWinner() + "," + d.getTotalGames()+";";
                   }
                    break;
                }
            }
            if (empty) {
                serverAndPlayers = data.getPort() + " " + serverName + " (0)";
            }
            %><%=serverAndPlayers%>
<%
        }
%>