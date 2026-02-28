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

   if (tbGame != null) {
      TBSet set = tbGame.getTbSet();
      String moves = "";
      String messages = "";
      String moveNums = "";
      String seqNums = "";
      String dates = "";
      String players = "";
      for (int i = 0; i < tbGame.getNumMoves(); i++) {
         moves += tbGame.getMove(i) + ",";
      }
      if (!"".equals(moves)) {
         moves = moves.substring(0, moves.length() - 1);
      }

      DSGPlayerData player1 = dsgPlayerStorer.loadPlayer(tbGame.getPlayer1Pid());
      DSGPlayerData player2 = dsgPlayerStorer.loadPlayer(tbGame.getPlayer2Pid());
      DSGPlayerData visitor = dsgPlayerStorer.loadPlayer(loggedInStr);
      DSGPlayerGameData p1Data = player1.getPlayerGameData(tbGame.getGame());
      DSGPlayerGameData p2Data = player2.getPlayerGameData(tbGame.getGame());

      boolean undoRequested = false;
      boolean canHide = false;
      boolean canUnHide = false;
      String cancelName = null;
      String cancelMsg = null;

      if ("rainwolf".equals(loggedInStr) || loggedInStr.equals(player1.getName()) || loggedInStr.equals(player2.getName())) {
         for (TBMessage m : tbGame.getMessages()) {
            if (m.getMessage().length() == 1) {
               messages += m.getMessage() + ",";
            } else {
               messages += MessageEncoder.encodeMessage(filters.applyFilters(0, m.getMessage())) + ",";
            }
            seqNums += m.getSeqNbr() + ",";
            moveNums += (m.getMoveNum() + (tbGame.getGame() == GridStateFactory.TB_CONNECT6 ? 2 : 0)) + ",";
            dates += m.getDate().getTime() + ",";
            players += (tbGame.getPlayer1Pid() == m.getPid() ? "1" : "2") + ",";
         }
         undoRequested = tbGame.isUndoRequested();
         canHide = tbGame.canHide(visitor.getPlayerID());
         canUnHide = tbGame.canUnHide(visitor.getPlayerID());
      }

      if (!"".equals(messages)) {
         messages = messages.substring(0, messages.length() - 1).replace("\\2", "'");
      }
      if (!"".equals(moveNums)) {
         moveNums = moveNums.substring(0, moveNums.length() - 1);
      }
      if (!"".equals(seqNums)) {
         seqNums = seqNums.substring(0, seqNums.length() - 1);
      }
      if (!"".equals(dates)) {
         dates = dates.substring(0, dates.length() - 1);
      }
      if (!"".equals(players)) {
         players = players.substring(0, players.length() - 1);
      }

      if (set.getCancelPid() != 0) {
         DSGPlayerData cancelPlayer = dsgPlayerStorer.loadPlayer(set.getCancelPid());
         cancelName = cancelPlayer.getName();
         cancelMsg = set.getCancelMsg().replace("\\2", "'");
      }

      String goState = null;
      if (tbGame.getGoState() == TBGame.GO_MARK_DEAD_STONES) {
         goState = "MARK_DEAD_STONES";
      } else if (tbGame.getGoState() == TBGame.GO_EVALUATE_DEAD_STONES) {
         goState = "EVALUATE_DEAD_STONES";
      }

      boolean isDPente = !tbGame.isCompleted() &&
         (tbGame.getGame() == GridStateFactory.TB_DPENTE ||
          tbGame.getGame() == GridStateFactory.TB_DKERYO ||
          tbGame.getGame() == GridStateFactory.TB_SWAP2PENTE ||
          tbGame.getGame() == GridStateFactory.TB_SWAP2KERYO);
%>{"gid":<%=jsonStr(gidString)%>,"private":<%=jsonStr((set.isPrivateGame() ? "" : "non-") + "private")%>,"rated":<%=jsonStr((tbGame.isRated() ? "" : "Not ") + "Rated")%>,"sid":<%=set.getSetId()%>,"moves":<%=jsonStr(moves)%>,"messages":<%=jsonStr(messages)%>,"currentPlayer":<%=jsonStr(tbGame.getCurrentPlayer() == player1.getPlayerID() ? player1.getName() : player2.getName())%>,"messageNums":<%=jsonStr(moveNums)%>,"seqNums":<%=jsonStr(seqNums)%>,"dates":<%=jsonStr(dates)%>,"players":<%=jsonStr(players)%>,"gameName":<%=jsonStr(GridStateFactory.getGameName(tbGame.getGame()))%>,"player1":{"name":<%=jsonStr(player1.getName())%>,"rating":<%=((int) p1Data.getRating())%>},"player2":{"name":<%=jsonStr(player2.getName())%>,"rating":<%=((int) p2Data.getRating())%>}<%
      if (cancelName != null) {
%>,"cancel":{"name":<%=jsonStr(cancelName)%>,"message":<%=jsonStr(cancelMsg)%>}<%
      }
%>,"state":<%=jsonStr(tbGame.getState() == TBGame.STATE_ACTIVE ? "active" : "inactive")%>,"goState":<%=jsonStr(goState)%>,"undoRequested":<%=undoRequested%>,"canHide":<%=canHide%>,"canUnHide":<%=canUnHide%><%
      if (isDPente) {
%>,"dPenteState":<%=jsonStr(String.valueOf(tbGame.getDPenteState()))%>,"swap2pass":<%=tbGame.didSwap2Pass()%><%
      }
%>}<%
   } else {
      GameStorer gameStorer = resources.getGameStorer();
      GameData game = new DefaultGameData();
      gameStorer.loadGame(gid, game);
      PlayerData p1Data = game.getPlayer1Data(), p2Data = game.getPlayer2Data();
      String moveStr = "";
      for (int move : game.getMoves()) {
         moveStr = moveStr + move + ",";
      }
      if (!"".equals(moveStr)) {
         moveStr = moveStr.substring(0, moveStr.length() - 1);
      }
%>{"gid":<%=jsonStr(gidString)%>,"private":<%=jsonStr((game.isPrivateGame() ? "" : "non-") + "private")%>,"rated":<%=jsonStr((game.getRated() ? "" : "Not ") + "Rated")%>,"gameName":<%=jsonStr(String.valueOf(game.getGame()))%>,"moves":<%=jsonStr(moveStr)%>,"player1":{"name":<%=jsonStr(p1Data.getUserIDName())%>,"rating":<%=(p1Data.getRating())%>},"player2":{"name":<%=jsonStr(p2Data.getUserIDName())%>,"rating":<%=(p2Data.getRating())%>},"messages":"","messageNums":""}<%
   }
%>