<%@ page import="org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.mobile.*,
                 com.google.gson.Gson,
                 java.util.*"
%><%@ page import="org.pente.gameServer.client.web.WhosOnlineRoom"%><%@ page import="org.pente.gameServer.client.web.WhosOnline"%><%@ page import="org.pente.gameServer.client.web.SessionListener"%>
<%@ page contentType="application/json; charset=UTF-8" %>
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
   out.print(new Gson().toJson(WhosonlineAndLiveResponse.build(rooms)));
%>