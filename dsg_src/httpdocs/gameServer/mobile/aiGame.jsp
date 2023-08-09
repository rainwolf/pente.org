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
<%@ page contentType="text/html; charset=UTF-8" %>
<%
   String loggedInStr = (String) request.getAttribute("name");
   if (loggedInStr == null) {
      response.sendRedirect("../index.jsp");
   } %><%
   com.jivesoftware.base.FilterChain filters =
      new com.jivesoftware.base.FilterChain(
         null, 1, new com.jivesoftware.base.Filter[]{
         new HTMLFilter(), new URLConverter(), new TBEmoticon(), new Newline()},
         new long[]{1, 1, 1, 1});


   DBHandler dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());
   ServletContext ctx = getServletContext();
   Resources resources = (Resources) application.getAttribute(Resources.class.getName());
   TBGameStorer tbGameStorer = resources.getTbGameStorer();
   String gidString = (String) request.getParameter("gid");
   TBGame tbGame = tbGameStorer.loadGame(Long.parseLong(gidString));
   TBSet set = tbGame.getTbSet();

%>gid=<%=gidString%>
<%

   String moves = "";
   String messages = "";
   String moveNums = "";
   String seqNums = "";
   String dates = "";
   String players = ""; //indicates which seat made message
   String tmpMsgs = "";
   for (int i = 0; i < tbGame.getNumMoves(); i++) {
      moves += tbGame.getMove(i) + ",";
   }
   if (!"".equals(moves)) {
      tmpMsgs = moves.substring(0, moves.length() - 1);
      moves = tmpMsgs;
   }

%>moves=<%=moves%>
difficulty=<%=tbGame.getRound()%>
gameName=<%=GridStateFactory.getGameName(tbGame.getGame()) + (tbGame.isRated() ? "-Rated" : "")%>
<%

%>EndOfSettingsParameters
