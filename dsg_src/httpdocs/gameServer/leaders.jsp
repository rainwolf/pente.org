<%@ page import="java.util.*,
                 java.text.*,
                 org.pente.game.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.client.web.*" %>

<% int game = 1;
if (pageContext.getAttribute("g") != null) {
	game = (Integer) pageContext.getAttribute("g");
}
else if (request.getParameter("g") != null) {
	try {
		game = Integer.parseInt(request.getParameter("g"));
	} catch (NumberFormatException n) {}
}
%>
<table>
  <tr>
    <th></th>
    <th>Player</th>
    <th>Rating</th>
    <th>Games</th>
  </tr>
  <% List<DSGPlayerData> leaders = leaderboard.getLeaders(game);
     for (int i = 0; i < leaders.size(); i++) {
    	 DSGPlayerData d = leaders.get(i);
    	 DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(game); %> 
       <tr>
        <td><%= (i + 1) %></td>
        <td><%@ include file="playerLink.jspf" %>&nbsp;</td>
        <td><%@ include file="ratings.jspf" %>&nbsp;</td>
        <td><%= nf.format(dsgPlayerGameData.getTotalGames()) %></td>
       </tr>
  <% } %>
</table>
<div style="float:right;padding:2px 2px;">
 <a class="boldbuttons"  href="javascript:moreStats();"><span>More Rankings&rarr;</span></a>
</div>
<br style="clear:both">