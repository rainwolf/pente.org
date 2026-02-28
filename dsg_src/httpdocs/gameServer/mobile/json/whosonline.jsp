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
   if (loggedInStr == null) {
      response.sendRedirect("../index.jsp");
      return;
   }
   ServletContext ctx = getServletContext();
   Resources globalResources = (Resources) ctx.getAttribute(Resources.class.getName());
   SessionListener sessionListener = (SessionListener) application.getAttribute(SessionListener.class.getName());
   List<WhosOnlineRoom> rooms = WhosOnline.getPlayers(globalResources, sessionListener);
   List<DSGPlayerData> mobilePlayers = new ArrayList<>();
   for (WhosOnlineRoom room : rooms) {
      if ("Mobile".equals(room.getName())) {
         mobilePlayers = room.getPlayers();
         break;
      }
   }
%>[<%
   boolean first = true;
   for (DSGPlayerData d : mobilePlayers) {
      DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(GridStateFactory.TB_PENTE);
      if (!first) { %>,<% } first = false;
%>{"name":<%=jsonStr(d.getName())%>,"rating":<%=((dsgPlayerGameData != null) ? (int) Math.round(dsgPlayerGameData.getRating()) : 1600)%>,"color":<%=(d.hasPlayerDonated() ? d.getNameColorRGB() : 0)%>,"tourneyWinner":<%=d.getTourneyWinner()%>,"totalGames":<%=d.getTotalGames()%>}<%
   }
%>]