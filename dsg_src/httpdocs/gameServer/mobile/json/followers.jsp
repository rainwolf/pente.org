<%@ page import="org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.mobile.*,
                 com.google.gson.Gson,
                 java.util.*" %>
<%@ page contentType="application/json; charset=UTF-8" %>
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
   out.print(new Gson().toJson(FollowersResponse.build(followers, following, dsgPlayerStorer, gameInt)));
%>