<%@ page import="java.util.*, java.text.*,
                 org.pente.game.*, 
                 org.pente.gameServer.core.*" %>

<%!
private static final NumberFormat profileNF = NumberFormat.getPercentInstance();
%>

<%
DSGPlayerData dsgPlayerData = (DSGPlayerData) request.getAttribute("dsgPlayerData");
List<GameData> wins = (List<GameData>) request.getAttribute("wins");
List<GameData> losses = (List<GameData>) request.getAttribute("losses");
if (wins == null) {
	wins = new ArrayList<GameData>(0);
}
if (losses == null) {
	losses = new ArrayList<GameData>(0);
}
int game = ((Integer) request.getAttribute("game")).intValue();
String gameStr = GridStateFactory.getDisplayName(game);
int totalGames = ((Integer) request.getAttribute("count")).intValue();
int start = ((Integer) request.getAttribute("start")).intValue();

pageContext.setAttribute("title", "Player Profile - Completed games"); %>

<%@ include file="begin.jsp" %>
<%
DateFormat gameDateFormat = null;
{
DSGPlayerData meData = dsgPlayerStorer.loadPlayer(me);
TimeZone tz = TimeZone.getTimeZone(meData.getTimezone());
gameDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm z");
gameDateFormat.setTimeZone(tz);
}
%>

<script type="text/javascript" src="/gameServer/js/go.js"></script>

<h3><%= dsgPlayerData.getName() %>'s Profile - Completed <%= gameStr %> Games</h3>

<img src="/gameServer/rgraph?game=<%= game %>&pid=<%= dsgPlayerData.getPlayerID() %>">

<%
   String error = (String) request.getAttribute("error");
   if (error != null) { %>


  <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
   Loading games failed: <%= error %>
  </font>


<%   
   } else {
%>

<% if (totalGames > 0) { %>

   <font size="-2">
   
   <% int startNum=start/100;
      start+=1;
      int end = start+99;
      if (end > totalGames) end=totalGames; %>

   Viewing <%= start %>-<%= end %> of <%= totalGames %> 

<%   if (totalGames > 100) { %> [ <% 
      for (int i=0;i<totalGames/100+1;i++) {
	    if (i==startNum) { %><%= i+1 %><% }
	    else { %><a href="/gameServer/viewLiveGames?p=<%= dsgPlayerData.getName() %>&g=<%= game %>&s=<%= i*100 %>"><%= i+1 %></a> <% } %>
<%     } %>
   ]</font>
<%   }
   } %>
   
<% if (wins.isEmpty()) { %>
     No wins<br><br>
<% } else { %>
	 <table border="0"  cellspacing="0" cellpadding="0" width="100%">
	   <tr bgcolor="<%= textColor2 %>">
	     <td colspan="8">
	       <font color="white">
	         <b>Wins (<%= wins.size() %>)
	       </font>
	     </td>
	   </tr>
	   <tr>
	     <td><b>Game</b></td>
	     <td><b>Player 1</b></td>
	     <td><b>Rating</b></td>
	     <td><b>Player 2</b></td>
	     <td><b>Rating</b></td>
	     <td><b>Rated</b></td>
	     <td><b>Timer</b></td>
	     <td><b>Completion Date</b></td>
	   </tr>
     <% for (GameData g : wins) { %>
         <tr>
           <td><a href="javascript:goWH('/gameServer/viewLiveGame?g=<%= g.getGameID() %>');">
             <%= gameStr %></a></td>
           <td><% PlayerData d = g.getPlayer1Data(); %><%@include file="vgplayerLink.jspf" %></td>
           <td><%= g.getPlayer1Data().getRating() %> 
               <img src="/gameServer/images/<%= SimpleDSGPlayerGameData.
               		getRatingsGifRatingOnly(g.getPlayer1Data().getRating()) %>"></td>
           <td><% d = g.getPlayer2Data(); %><%@include file="vgplayerLink.jspf" %></td>
           <td><%= g.getPlayer2Data().getRating() %> 
               <img src="/gameServer/images/<%= SimpleDSGPlayerGameData.
               		getRatingsGifRatingOnly(g.getPlayer2Data().getRating()) %>"></td>
           <td><%= g.getRated() ? "Rated" : "Not Rated" %></td>
           <td><% if (g.getTimed()) { %><%= g.getInitialTime() %> / <%= g.getIncrementalTime() %>
               <% } else { %>No<% } %></td>
           <td><%= gameDateFormat.format(g.getDate()) %></td>
         </tr>
     <% } %>
     </table>
     <br>
<%   } %>

<br>
<% if (losses.isEmpty()) { %>
     No losses<br><br>
<% } else { %>
	 <table border="0"  cellspacing="0" cellpadding="0" width="100%">
	   <tr bgcolor="<%= textColor2 %>">
	     <td colspan="8">
	       <font color="white">
	         <b>Losses (<%= losses.size() %>)
	       </font>
	     </td>
	   </tr>
	   <tr>
	     <td><b>Game</b></td>
	     <td><b>Player 1</b></td>
	     <td><b>Rating</b></td>
	     <td><b>Player 2</b></td>
	     <td><b>Rating</b></td>
	     <td><b>Rated</b></td>
	     <td><b>Timer</b></td>
	     <td><b>Completion Date</b></td>
	   </tr>
     <% for (GameData g : losses) { %>
         <tr>
         <td><a href="javascript:goWH('/gameServer/viewLiveGame?g=<%= g.getGameID() %>');">
           <%= gameStr %></a></td>
         <td><% PlayerData d = g.getPlayer1Data(); %><%@include file="vgplayerLink.jspf" %></td>
         <td><%= g.getPlayer1Data().getRating() %> 
             <img src="/gameServer/images/<%= SimpleDSGPlayerGameData.
             		getRatingsGifRatingOnly(g.getPlayer1Data().getRating()) %>"></td>
         <td><% d = g.getPlayer2Data(); %><%@include file="vgplayerLink.jspf" %></td>
         <td><%= g.getPlayer2Data().getRating() %> 
             <img src="/gameServer/images/<%= SimpleDSGPlayerGameData.
             		getRatingsGifRatingOnly(g.getPlayer2Data().getRating()) %>"></td>
         <td><%= g.getRated() ? "Rated" : "Not Rated" %></td>
         <td><% if (g.getTimed()) { %><%= g.getInitialTime() %> / <%= g.getIncrementalTime() %>
             <% } else { %>No<% } %></td>
         <td><%= gameDateFormat.format(g.getDate()) %></td>
       </tr>
     <% } %>
     </table>
     <br>
<%   } %>

<% } %>


<%@ include file="end.jsp" %>