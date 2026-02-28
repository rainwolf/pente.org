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
%>
<%@ page contentType="application/json; charset=UTF-8" %>
<%!
   private static String jsonStr(String s) {
      if (s == null) return "null";
      return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
   }
%>
<%
   String loggedInStr = (String) request.getAttribute("name");
   boolean go = (request.getParameter("iPhone") != null);
   if (loggedInStr == null) {
      response.sendRedirect("../index.jsp");
      return;
   }
   ServletContext ctx = getServletContext();
   Resources globalResources = (Resources) ctx.getAttribute(Resources.class.getName());
   SessionListener sessionListener = (SessionListener) application.getAttribute(SessionListener.class.getName());
   List<WhosOnlineRoom> rooms = new ArrayList(WhosOnline.getPlayers(globalResources, sessionListener));
%>[<%
   boolean firstServer = true;
   for (Iterator it = globalResources.getServerData().iterator(); it.hasNext(); ) {
      ServerData data = (ServerData) it.next();
      String serverName = data.getName();
      List<DSGPlayerData> serverPlayers = new ArrayList<>();
      boolean empty = true;
      for (WhosOnlineRoom room : rooms) {
         if (serverName.equals(room.getName())) {
            empty = false;
            serverPlayers = room.getPlayers();
            break;
         }
      }
      if (!firstServer) { %>,<% } firstServer = false;
%>{"port":<%=data.getPort()%>,"name":<%=jsonStr(serverName)%>,"playerCount":<%=(empty ? 0 : serverPlayers.size())%>,"players":[<%
      boolean firstPlayer = true;
      for (DSGPlayerData d : serverPlayers) {
         DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(GridStateFactory.TB_PENTE);
         if (!firstPlayer) { %>,<% } firstPlayer = false;
%>{"name":<%=jsonStr(d.getName())%>,"rating":<%=((dsgPlayerGameData != null) ? (int) Math.round(dsgPlayerGameData.getRating()) : 1600)%>,"color":<%=(d.hasPlayerDonated() ? d.getNameColorRGB() : 0)%>,"tourneyWinner":<%=d.getTourneyWinner()%>,"totalGames":<%=d.getTotalGames()%>}<%
      }
%>]}<%
   }
%>]