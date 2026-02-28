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
   DSGPlayerData meData = globalResources.getDsgPlayerStorer().loadPlayer(loggedInStr);
   SessionListener sessionListener = (SessionListener) application.getAttribute(SessionListener.class.getName());
   List<WhosOnlineRoom> rooms = new ArrayList(WhosOnline.getPlayers(meData.getPlayerID(), globalResources, sessionListener));
   WhosOnlineRoom webRoom = null;
   for (Iterator<WhosOnlineRoom> iterator = rooms.iterator(); iterator.hasNext(); ) {
      WhosOnlineRoom r = iterator.next();
      if ("web".equals(r.getName())) {
         webRoom = r;
         iterator.remove();
         break;
      }
   }
   if (webRoom != null) {
      webRoom.setName("Website");
      rooms.add(webRoom);
   }
%>[<%
   boolean firstRoom = true;
   for (WhosOnlineRoom room : rooms) {
      if (!firstRoom) { %>,<% } firstRoom = false;
%>{"name":<%=jsonStr(room.getName())%>,"players":[<%
      boolean firstPlayer = true;
      for (DSGPlayerData d : room.getPlayers()) {
         DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(GridStateFactory.TB_PENTE);
         if (!firstPlayer) { %>,<% } firstPlayer = false;
%>{"name":<%=jsonStr(d.getName())%>,"rating":<%=((dsgPlayerGameData != null) ? (int) Math.round(dsgPlayerGameData.getRating()) : 1600)%>,"color":<%=(d.hasPlayerDonated() ? d.getNameColorRGB() : 0)%>,"tourneyWinner":<%=d.getTourneyWinner()%>,"totalGames":<%=d.getTotalGames()%>}<%
      }
%>]}<%
   }
%>]