<%@ page import="org.pente.database.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.game.*,
                 org.pente.turnBased.*,
                 org.pente.turnBased.web.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*,
                 java.text.*,
                 java.util.*" %>
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
   Resources resources = (Resources) application.getAttribute(Resources.class.getName());
   TBGameStorer tbGameStorer = resources.getTbGameStorer();
   String gidString = (String) request.getParameter("gid");

   if (gidString == null || "".equals(gidString)) {
      response.sendRedirect("../index.jsp");
      return;
   }
   TBGame tbGame = tbGameStorer.loadGame(Long.parseLong(gidString));
   if (tbGame == null) {
      response.sendRedirect("../index.jsp");
      return;
   }

   String moves = "";
   for (int i = 0; i < tbGame.getNumMoves(); i++) {
      moves += tbGame.getMove(i) + ",";
   }
   if (!"".equals(moves)) {
      moves = moves.substring(0, moves.length() - 1);
   }
%>{"gid":<%=jsonStr(gidString)%>,"moves":<%=jsonStr(moves)%>,"difficulty":<%=tbGame.getRound()%>,"gameName":<%=jsonStr(GridStateFactory.getGameName(tbGame.getGame()) + (tbGame.isRated() ? "-Rated" : ""))%>}