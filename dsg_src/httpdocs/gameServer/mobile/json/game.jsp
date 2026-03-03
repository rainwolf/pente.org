<%@ page import="org.pente.database.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.mobile.*,
                 org.pente.game.*,
                 org.pente.turnBased.*,
                 org.pente.turnBased.web.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*,
                 com.google.gson.Gson,
                 java.util.*" %>
<%@ page contentType="application/json; charset=UTF-8" %>
<%
   String loggedInStr = (String) request.getAttribute("name");
   if (loggedInStr == null) {
      response.sendRedirect("../index.jsp");
      return;
   }
   loggedInStr = loggedInStr.toLowerCase();

   com.jivesoftware.base.FilterChain filters =
      new com.jivesoftware.base.FilterChain(
         null, 1, new com.jivesoftware.base.Filter[]{
         new HTMLFilter(), new URLConverter(), new TBEmoticon(), new Newline()},
         new long[]{1, 1, 1, 1});

   Resources resources = (Resources) application.getAttribute(Resources.class.getName());
   TBGameStorer tbGameStorer = resources.getTbGameStorer();
   DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
   String gidString = (String) request.getParameter("gid");
   long gid = Long.parseLong(gidString);

   TBGame tbGame = tbGameStorer.loadGame(gid);
   DSGPlayerData visitor = dsgPlayerStorer.loadPlayer(loggedInStr);

   if (tbGame != null) {
      DSGPlayerData player1 = dsgPlayerStorer.loadPlayer(tbGame.getPlayer1Pid());
      DSGPlayerData player2 = dsgPlayerStorer.loadPlayer(tbGame.getPlayer2Pid());
      boolean canSeeMessages = "rainwolf".equals(loggedInStr)
              || loggedInStr.equals(player1.getName())
              || loggedInStr.equals(player2.getName());
      final com.jivesoftware.base.FilterChain f = filters;
      GameResponse.EncodedMessages encodedMsgs = canSeeMessages
              ? GameResponse.EncodedMessages.from(tbGame, m -> m.getMessage().length() == 1
                      ? m.getMessage()
                      : MessageEncoder.encodeMessage(f.applyFilters(0, m.getMessage())))
              : null;
      out.print(new Gson().toJson(GameResponse.build(tbGame, visitor, dsgPlayerStorer, encodedMsgs)));
   } else {
      out.print(new Gson().toJson(GameResponse.buildHistoric(gidString, resources.getGameStorer())));
   }
%>