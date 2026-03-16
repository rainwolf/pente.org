<%@ page import="org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.mobile.*,
                 com.google.gson.Gson,
                 java.util.*"
%><%@ page import="org.pente.gameServer.client.web.SessionListener"%><%@ page import="org.pente.gameServer.client.web.WhosOnlineRoom"%><%@ page import="org.pente.gameServer.client.web.WhosOnline"%>
<%@ page contentType="application/json; charset=UTF-8" %>
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
   out.print(new Gson().toJson(WhosonlineResponse.build(mobilePlayers)));
%>