<%@ page import="org.pente.gameDatabase.*,
                 org.pente.game.*,
                 org.pente.gameServer.core.*,
                 java.text.*,
                 java.util.*" %>

<%! private static final NumberFormat percentFormat =
   NumberFormat.getPercentInstance();

   static {
      percentFormat.setMaximumFractionDigits(1);
   }

   private static final NumberFormat numberFormat = NumberFormat.getInstance();
   private static final DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
%>
<%@ include file="../colors.jspf" %>
<%
   ServletContext ctx = getServletContext();
   // globalResources = (Resources) ctx.getAttribute(Resources.class.getName());
   // dbHandler = (DBHandler) ctx.getAttribute(DBHandler.class.getName());
   DSGPlayerStorer dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());

   String nm = (String) request.getAttribute("name");
   DSGPlayerData pdata = null;
   if (nm != null) {
      pdata = dsgPlayerStorer.loadPlayer(nm);
   }
   if (pdata != null && pdata.databaseAccess()) {
//    if (true) {


      boolean showGames = true;
      if (request.getAttribute("blocked") != null) {
         showGames = false; %>
<font color="red">
   You have been temporarily blocked from viewing the Games History.
   This means one of several things.<br>
   <ol>
      <li>You are currently playing a rated game at Pente.org, therefore you are not
         allowed to use the Games Database.
      </li>
      <li>Someone else is currently playing a rated game, and is playing
         the same position you searched for. Try again in a minute.
      </li>
      <li>Someone else is currently playing a rated game, and you share
         an IP address with them. Try again in a little while.
      </li>
   </ol>
   Please read <a href="javascript:helpWin('ratedPolicy');">
   Pente.org''s policy for Rated Games</a> for more information.
</font>
<% }

   GameStorerSearchResponseData data = (GameStorerSearchResponseData)
      request.getAttribute("responseData");
   Vector searchResultsVector = data.searchResponseMoveData();
%>
<!--
<%=((String) request.getAttribute("name"))%>
<%="moves="%><%
   if (!searchResultsVector.isEmpty()) {
      GameStorerSearchResponseMoveData moveData = (GameStorerSearchResponseMoveData) searchResultsVector.elementAt(0);
%><%="" + moveData.getMove()%><%
   }
   for (int i = 1; i < searchResultsVector.size(); i++) {
      GameStorerSearchResponseMoveData moveData = (GameStorerSearchResponseMoveData) searchResultsVector.elementAt(i);
%><%="," + moveData.getMove()%><%
   }

   GameStats gameStats = (GameStats) request.getAttribute("gameStats");
   GameStorerSearchRequestFilterData filterData =
      data.getGameStorerSearchRequestData().getGameStorerSearchRequestFilterData();

   SimpleGameStorerSearchRequestFormat requestFormat = new SimpleGameStorerSearchRequestFormat();
   SimpleGameStorerSearchResponseFormat responseFormat = new SimpleGameStorerSearchResponseFormat();
   StringBuffer moves = new StringBuffer();
   requestFormat.formatMoves(data.getGameStorerSearchRequestData(), moves, false, false);

   StringBuffer results = new StringBuffer();
   responseFormat.formatMoveResults(data, results, false);
%>
<%! private static final String headers[] = new String[]{"#", "Move", "Games", "Wins"}; %>
<% int responseOrder = data.getGameStorerSearchRequestData().getGameStorerSearchResponseOrder() + 1;
   for (int i = 0; i < headers.length; i++) {

      String color = "black";
      String header = headers[i];

      // nothing special for #
      if (i > 0) {

         // highlight the current order in red
         if (responseOrder == i) {
            color = textColor2;
%>
<br><font color="<%= color %>"><b><%= header %></b></font></td>
<% }
}
}

   Vector searchResults = data.searchResponseMoveData();
   double total = 0;
   if (responseOrder == 2) {
      for (int i = 0; i < searchResults.size(); i++) {
         GameStorerSearchResponseMoveData moveData = null;
         moveData = (GameStorerSearchResponseMoveData) searchResults.elementAt(i);
         total += moveData.getGames();
      }
   }
   if (total == 0) {
      total = 100;
   }
%><%="occurrence="%><%
   if (!searchResults.isEmpty()) {
      GameStorerSearchResponseMoveData moveData = null;

      moveData = (GameStorerSearchResponseMoveData) searchResults.elementAt(0);

%><%=(responseOrder == 2 ? percentFormat.format((double) (moveData.getGames()) / total) : percentFormat.format(moveData.getPercentage())).replace("%", "") + ""%><%
   }
   for (int i = 1; i < searchResults.size(); i++) {

      GameStorerSearchResponseMoveData moveData = null;

      moveData = (GameStorerSearchResponseMoveData) searchResults.elementAt(i);

%><%=";" + (responseOrder == 2 ? percentFormat.format((double) (moveData.getGames()) / total) : percentFormat.format(moveData.getPercentage())).replace("%", "")%><% } %>
-->
<table width="100%" border="0" cellspacing="0" cellpadding="0">
   <% SimpleGameStorerSearchRequestFormat searchFormat =
      new SimpleGameStorerSearchRequestFormat();
      Vector games = data.getGames();
      for (int i = 0; i < games.size(); i++) {
         GameData gameData = (GameData) games.elementAt(i);
         StringBuffer movesBuf = new StringBuffer(); %>
   <tr bgcolor="<%= bgColor2 %>">
      <td>
         <a href="https://www.pente.org/gameServer/viewLiveGame?mobile&g=<%= gameData.getGameID() %>"><%= gameData.getEvent() %>
         </a><br><%= dateFormat.format(gameData.getDate()) %>
      </td>
      <td align="right">
         <%
            String p1Link = gameData.getSiteURL();
            String p2Link = gameData.getSiteURL();
            if (gameData.getShortSite().equals("Pente.org")) {
               p1Link = request.getContextPath() + "https://www.pente.org/gameServer/profile?viewName=" +
                  gameData.getPlayer1Data().getUserIDName();
               p2Link = request.getContextPath() + "https://www.pente.org/gameServer/profile?viewName=" +
                  gameData.getPlayer2Data().getUserIDName();
            } else if (gameData.getShortSite().equals("IYT")) {
               p1Link = "http://www.itsyourturn.com/iyt.dll?userprofile?userid=" +
                  gameData.getPlayer1Data().getUserID();
               p2Link = "http://www.itsyourturn.com/iyt.dll?userprofile?userid=" +
                  gameData.getPlayer2Data().getUserID();
            } else if (gameData.getShortSite().equals("BK")) {
               p1Link = "http://brainking.com/game/PlayerList?submit=Search&a=ap&utf=" +
                  gameData.getPlayer1Data().getUserIDName();
               p2Link = "http://brainking.com/game/PlayerList?submit=Search&a=ap&utf=" +
                  gameData.getPlayer2Data().getUserIDName();
            } %>
         <a href="<%= p1Link %>"><font
            color="<%= gameData.getWinner() == GameData.PLAYER1 ? "#8b0000" : "black" %>"><%= gameData.getPlayer1Data().getUserIDName() %>
         </font></a> vs <a href="<%= p2Link %>"><font
         color="<%= gameData.getWinner() == GameData.PLAYER2 ? "#8b0000" : "black" %>"><%= gameData.getPlayer2Data().getUserIDName() %>
      </font></a>,
         <!-- <a href="<%= gameData.getSiteURL() %>"><%= gameData.getShortSite() %></a>, -->
      </td>
   </tr>
   <% } %>

</table>

<% } else { %>

<br>
<br>
This feature is currently available to subscribers only.
<br>
<br>

<% } %>

