<%@ page import="java.util.*,
                 java.text.*,
                 org.pente.game.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.client.web.*" %>


<%
// SessionListener sessionListener = (SessionListener)
//     application.getAttribute(SessionListener.class.getName());
// List<WhosOnlineRoom> rooms = WhosOnline.getPlayers(globalResources, sessionListener);
%>
<style type="text/css">
  .box { width:200px; }
</style>

<div class="box">
  <div class="boxhead">
    <h4>Who's Online</h4>
  </div>
  <div class="boxcontents">
    <table width="100%">
      <tr>
        <th></th>
        <th>Player</th>
        <th>Rating</th>
        <th>Games</th>
      </tr>
<% for (int i = 0; i < rooms.size(); i++) {
	  WhosOnlineRoom room = rooms.get(i);
	  String color = i % 2 == 1 ? "style=\"background:white\"" : ""; %>
      <tr <%= color %>><td></td><td colspan="3" style="text-align:center"><b><%= (room.getName().equals("web") ? "Browsing" : room.getName()) %></b></td></tr>

<%       for (DSGPlayerData d : room.getPlayers()) {
//           DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(("Mobile".equals(room.getName())?GridStateFactory.TB_PENTE:GridStateFactory.PENTE)); 
           DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(game); 
           %> 
           <tr <%= color %>>
            <td>&nbsp;&nbsp;</td>
            <td><%@ include file="playerLink.jspf" %>&nbsp;</td>
            <td><%@ include file="ratings.jspf" %>&nbsp;</td>
            <td><%= nf.format(d.getTotalGames()) %></td>
           </tr>
      <% } %>
<% } %>
    </table>
  </div>
</div>