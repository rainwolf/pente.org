<%@ page import="java.util.*, org.pente.turnBased.web.*" %>

<% final String sort = request.getParameter("sort");
   if (sort != null && !sort.equals("")) {
      System.out.println("sort=" + sort);
      AdminComparator comp = new AdminComparator(
         Integer.parseInt(sort), dsgPlayerStorer);
      Collections.sort(games, comp);
   }
%>

<table border="1">
   <tr>
      <td><b><a href="<%= pageName %>sort=1">Game</a></b></td>
      <td><b><a href="<%= pageName %>sort=2">State</a></b></td>
      <td><b><a href="<%= pageName %>sort=3">Player 1</b></td>
      <td><b><a href="<%= pageName %>sort=4">Rating</b></td>
      <td><b><a href="<%= pageName %>sort=5">Player 2</b></td>
      <td><b><a href="<%= pageName %>sort=6">Rating</b></td>
      <td><b><a href="<%= pageName %>sort=7">Move</b></td>
      <td><b><a href="<%= pageName %>sort=8">Time/Move</b></td>
      <td><b><a href="<%= pageName %>sort=9">Time Left</b></td>
      <td><b><a href="<%= pageName %>sort=10">Rated</b></td>
   </tr>

   <% for (TBGame game : games) {
      DSGPlayerData p1 = dsgPlayerStorer.loadPlayer(game.getPlayer1Pid());
      DSGPlayerGameData p1g = null;
      if (p1 != null) {
         p1g = p1.getPlayerGameData(game.getGame());
      }
      DSGPlayerData p2 = dsgPlayerStorer.loadPlayer(game.getPlayer2Pid());
      DSGPlayerGameData p2g = null;
      if (p2 != null) {
         p2g = p2.getPlayerGameData(game.getGame());
      }
      DSGPlayerGameData dsgPlayerGameData = p1g; %>
   <tr>
      <td><a href="/gameServer/tb/game?command=load&mobile&gid=<%= game.getGid() %>">
         <%= GridStateFactory.getGameName(game.getGame()) %>
      </a></td>
      <td><%= game.getState() %>
      </td>
      <td><% if (p1 != null) { %><a href="player.jsp?pid=<%= game.getPlayer1Pid() %>"><%= p1.getName()%>
      </a><% } %></td>
      <td><% if (dsgPlayerGameData != null) { %>
         <%@ include file="../../ratings.jspf" %>
         <% } %></td>
      <% dsgPlayerGameData = p2g; %>
      <td><% if (p2 != null) { %><a href="player.jsp?pid=<%= game.getPlayer2Pid() %>"><%= p2.getName()%>
      </a><% } %></td>
      <td><% if (dsgPlayerGameData != null) { %>
         <%@ include file="../../ratings.jspf" %>
         <% } %></td>
      <td><%= game.getNumMoves() + 1 %>
      </td>
      <td><%= game.getDaysPerMove() %>
      </td>
      <td><% if (game.getState() == TBGame.STATE_ACTIVE) { %><%= Utilities.getTimeLeft(game.getTimeoutDate().getTime()) %><% } %></td>
      <td><%= game.isRated() ? "Rated" : "Not Rated" %>
      </td>
   </tr>
   <% } %>

</table>