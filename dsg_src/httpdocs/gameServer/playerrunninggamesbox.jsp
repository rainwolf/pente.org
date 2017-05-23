<%@ page import="org.pente.database.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.server.*, org.pente.game.*, org.pente.turnBased.*,
                 org.pente.turnBased.web.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*,
                 java.text.*,
                 java.util.*" %>
<%
Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());
TBGameStorer tbGameStorer = resources.getTbGameStorer();
List<TBSet> waitingSets = tbGameStorer.loadWaitingSets();
List<TBSet> currentSets = tbGameStorer.loadSets(dsgPlayerData.getPlayerID());
List<TBSet> invitesTo = new ArrayList<TBSet>();
List<TBSet> invitesFrom = new ArrayList<TBSet>();
List<TBGame> myTurn = new ArrayList<TBGame>();
List<TBGame> oppTurn = new ArrayList<TBGame>();
Utilities.organizeGames(dsgPlayerData.getPlayerID(), currentSets,
    invitesTo, invitesFrom, myTurn, oppTurn);
%>
<script type="text/javascript" src="/gameServer/js/go.js"></script>

<b>Ongoing turn-based games</b>

<table border="0" cellpadding="0" cellspacing="0" width="100%">
  <tr>
    <td>
      <table width="100%" border="0" cellspacing="0" cellpadding="0">
        <!-- <tr bgcolor="<%= bgColor1 %>"> -->
      
            <tr>
<%   int columns = 0;
        for (TBGame game : myTurn) {
            if (game.getTbSet().isPrivateGame()) {
                continue;
            } %>
            
              <td>
                <%@ include file="tb/listedMobileGame.jsp" %>
              </td>
              <% if (columns > 2) { 
              columns = 0; %>
            </tr>
            <tr>
            <% } else { columns++; } %>

   <%  }  %> 
<%   for (TBGame game : oppTurn) {
            if (game.getTbSet().isPrivateGame()) {
                continue;
            } %>
            
              <td>
                <%@ include file="tb/listedMobileGame.jsp" %>
              </td>
              <% if (columns > 2) { 
              columns = 0; %>
            </tr>
            <tr>
            <% } else { columns++; } %>

   <%  }  %> 
            </tr>
      </table>
    </td>
  </tr>
</table>
<br>

