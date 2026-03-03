<%@ page import="org.pente.database.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.mobile.*,
                 org.pente.turnBased.*,
                 com.google.gson.Gson,
                 java.util.*" %>
<%@ page contentType="application/json; charset=UTF-8" %>
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
   out.print(new Gson().toJson(AiGameResponse.build(tbGame)));
%>