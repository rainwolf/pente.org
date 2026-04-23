<%@ page import="org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.mobile.*,
                 com.google.gson.Gson,
                 java.util.*"
%><%@ page import="org.pente.gameServer.client.web.SessionListener"%><%@ page import="org.pente.gameServer.client.web.WhosOnlineRoom"%><%@ page import="org.pente.gameServer.client.web.WhosOnline"%><%@ page import="java.util.stream.Collectors"%>
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
   List<ServerData> servers = globalResources.getServerData().stream()
           .filter(data -> !data.getName().toLowerCase().contains("arena"))
           .collect(Collectors.toList());
   out.print(new Gson().toJson(LiveServersResponse.build(servers, rooms)));
%>