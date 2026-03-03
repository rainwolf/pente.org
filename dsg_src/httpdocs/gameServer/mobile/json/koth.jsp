<%@ page import="org.pente.database.*,
                 org.pente.turnBased.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.mobile.*,
                 org.pente.kingOfTheHill.*,
                 com.google.gson.Gson,
                 java.util.*,
                 org.apache.log4j.*"
%>
<%@ page contentType="application/json; charset=UTF-8" %>
<%! private static Category log4j = Category.getInstance("org.pente.gameServer.web.client.jsp"); %>
<%
   String loginname = request.getParameter("name");
   String name = null;
   if (loginname != null) {
      name = loginname.toLowerCase();
   }
   String gameStr = (String) request.getParameter("game");
   if (gameStr == null) {
      gameStr = (String) request.getAttribute("game");
   }
   int game = 0;
   if (gameStr != null) {
      game = Integer.parseInt(gameStr);
   }

   DBHandler dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());
   Resources resources = (Resources) application.getAttribute(Resources.class.getName());
   CacheKOTHStorer kothStorer = resources.getKOTHStorer();
   DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
   DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
   long myPid = dsgPlayerData.getPlayerID();

   Hill hill = kothStorer.getHill(game);
   out.print(new Gson().toJson(KothResponse.build(hill, game, myPid, dsgPlayerData, dsgPlayerStorer, kothStorer)));
%>