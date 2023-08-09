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
<%@ page contentType="text/html; charset=UTF-8" %>
<%
   String loggedInStr = (String) request.getAttribute("name");
   if (loggedInStr == null) {
      response.sendRedirect("../index.jsp");
   } %><%
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
   DateFormat dateFormat = null;
// TimeZone tz = TimeZone.getTimeZone(dsgPlayerData.getTimezone());
   dateFormat = new SimpleDateFormat("MM/dd/yyyy");
   for (WhosOnlineRoom room : rooms) {
//        if ("web".equals(room.getName())) {
//            continue;
//        }
%><%=room.getName() + ":"%><%
   for (DSGPlayerData d : room.getPlayers()) {
      DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(GridStateFactory.TB_PENTE);
%><%=d.getName() + "," + ((dsgPlayerGameData != null) ? (int) Math.round(dsgPlayerGameData.getRating()) : "1600") + "," + (d.hasPlayerDonated() ? d.getNameColorRGB() : 0) + "," + d.getTourneyWinner() + "," + d.getTotalGames() + ";"%><%
   }
%><%="\n"%><%
   } %>
