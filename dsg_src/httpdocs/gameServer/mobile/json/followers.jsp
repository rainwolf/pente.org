<%@ page import="org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
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
      response.sendRedirect("empty.jsp");
      return;
   }
   Resources resources = (Resources) application.getAttribute(Resources.class.getName());
   String gameStr = request.getParameter("game");
   int gameInt = 1;
   if (gameStr != null) {
      gameInt = Integer.parseInt(gameStr);
   }
   DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
   DSGPlayerData meData = dsgPlayerStorer.loadPlayer(loggedInStr);
   DSGFollowerStorer followerStorer = resources.getFollowerStorer();
   List<Long> followers = followerStorer.getFollowers(meData.getPlayerID());
   List<Long> following = followerStorer.getFollowing(meData.getPlayerID());
%>{"followers":[<%
   boolean first = true;
   for (long pid : followers) {
      DSGPlayerData playerData = dsgPlayerStorer.loadPlayer(pid);
      DSGPlayerGameData gameData = playerData.getPlayerGameData(gameInt);
      int ratingInt = 1600;
      if (gameData != null) {
         ratingInt = (int) gameData.getRating();
      }
      if (!first) { %>,<% } first = false;
%>{"name":<%=jsonStr(playerData.getName())%>,"donated":<%=(playerData.hasPlayerDonated() ? 1 : 0)%>,"color":<%=(playerData.hasPlayerDonated() ? playerData.getNameColorRGB() : 0)%>,"tourneyWinner":<%=playerData.getTourneyWinner()%>,"rating":<%=ratingInt%>}<%
   }
%>],"following":[<%
   first = true;
   for (long pid : following) {
      DSGPlayerData playerData = dsgPlayerStorer.loadPlayer(pid);
      DSGPlayerGameData gameData = playerData.getPlayerGameData(gameInt);
      int ratingInt = 1600;
      if (gameData != null) {
         ratingInt = (int) gameData.getRating();
      }
      if (!first) { %>,<% } first = false;
%>{"name":<%=jsonStr(playerData.getName())%>,"donated":<%=(playerData.hasPlayerDonated() ? 1 : 0)%>,"color":<%=(playerData.hasPlayerDonated() ? playerData.getNameColorRGB() : 0)%>,"tourneyWinner":<%=playerData.getTourneyWinner()%>,"rating":<%=ratingInt%>}<%
   }
%>]}