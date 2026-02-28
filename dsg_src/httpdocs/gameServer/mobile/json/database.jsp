<%@ page import="org.pente.gameDatabase.*,
                 org.pente.game.*,
                 org.pente.gameServer.core.*,
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
   ServletContext ctx = getServletContext();
   DSGPlayerStorer dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());
   DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");

   String nm = (String) request.getAttribute("name");
   DSGPlayerData pdata = null;
   if (nm != null) {
      pdata = dsgPlayerStorer.loadPlayer(nm);
   }

   if (pdata == null || !pdata.databaseAccess()) {
%>{"access":false,"blocked":false,"moves":[],"occurrence":[],"games":[]}<%
      return;
   }

   if (request.getAttribute("blocked") != null) {
%>{"access":true,"blocked":true,"moves":[],"occurrence":[],"games":[]}<%
      return;
   }

   GameStorerSearchResponseData data = (GameStorerSearchResponseData) request.getAttribute("responseData");
   Vector searchResultsVector = data.searchResponseMoveData();
   int responseOrder = data.getGameStorerSearchRequestData().getGameStorerSearchResponseOrder() + 1;
   Vector searchResults = data.searchResponseMoveData();
   double total = 0;
   if (responseOrder == 2) {
      for (int i = 0; i < searchResults.size(); i++) {
         GameStorerSearchResponseMoveData moveData = (GameStorerSearchResponseMoveData) searchResults.elementAt(i);
         total += moveData.getGames();
      }
   }
   if (total == 0) {
      total = 100;
   }
   NumberFormat percentFormat = NumberFormat.getPercentInstance();
   percentFormat.setMaximumFractionDigits(1);
   Vector games = data.getGames();
%>{"access":true,"blocked":false,"moves":[<%
   for (int i = 0; i < searchResultsVector.size(); i++) {
      GameStorerSearchResponseMoveData moveData = (GameStorerSearchResponseMoveData) searchResultsVector.elementAt(i);
      if (i > 0) { %>,<% }
%><%=moveData.getMove()%><%
   }
%>],"occurrence":[<%
   for (int i = 0; i < searchResults.size(); i++) {
      GameStorerSearchResponseMoveData moveData = (GameStorerSearchResponseMoveData) searchResults.elementAt(i);
      if (i > 0) { %>,<% }
%><%=jsonStr((responseOrder == 2 ? percentFormat.format((double)(moveData.getGames()) / total) : percentFormat.format(moveData.getPercentage())).replace("%", ""))%><%
   }
%>],"games":[<%
   for (int i = 0; i < games.size(); i++) {
      GameData gameData = (GameData) games.elementAt(i);
      if (i > 0) { %>,<% }
      String p1Link = gameData.getSiteURL();
      String p2Link = gameData.getSiteURL();
      if (gameData.getShortSite().equals("Pente.org")) {
         p1Link = "/gameServer/profile?viewName=" + gameData.getPlayer1Data().getUserIDName();
         p2Link = "/gameServer/profile?viewName=" + gameData.getPlayer2Data().getUserIDName();
      } else if (gameData.getShortSite().equals("IYT")) {
         p1Link = "http://www.itsyourturn.com/iyt.dll?userprofile?userid=" + gameData.getPlayer1Data().getUserID();
         p2Link = "http://www.itsyourturn.com/iyt.dll?userprofile?userid=" + gameData.getPlayer2Data().getUserID();
      } else if (gameData.getShortSite().equals("BK")) {
         p1Link = "http://brainking.com/game/PlayerList?submit=Search&a=ap&utf=" + gameData.getPlayer1Data().getUserIDName();
         p2Link = "http://brainking.com/game/PlayerList?submit=Search&a=ap&utf=" + gameData.getPlayer2Data().getUserIDName();
      }
%>{"gameId":<%=gameData.getGameID()%>,"event":<%=jsonStr(gameData.getEvent())%>,"date":<%=jsonStr(dateFormat.format(gameData.getDate()))%>,"viewUrl":<%=jsonStr("/gameServer/viewLiveGame?mobile&g=" + gameData.getGameID())%>,"site":<%=jsonStr(gameData.getShortSite())%>,"player1":{"name":<%=jsonStr(gameData.getPlayer1Data().getUserIDName())%>,"url":<%=jsonStr(p1Link)%>,"winner":<%=(gameData.getWinner() == GameData.PLAYER1)%>},"player2":{"name":<%=jsonStr(gameData.getPlayer2Data().getUserIDName())%>,"url":<%=jsonStr(p2Link)%>,"winner":<%=(gameData.getWinner() == GameData.PLAYER2)%>}}<%
   }
%>]}