<%@ page import="org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.mobile.*,
                 com.google.gson.Gson,
                 java.util.*"
%>
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
   List<WhosOnlineRoom> rooms = new ArrayList(WhosOnline.getPlayers(globalResources, sessionListener));
   out.print(new Gson().toJson(LiveServersResponse.build(globalResources.getServerData(), rooms)));
%>