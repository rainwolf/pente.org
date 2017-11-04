<%@ page import="org.pente.database.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.server.*, org.pente.game.*, org.pente.turnBased.*,
                 org.pente.turnBased.web.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*,
                 java.text.*,
                 java.util.*" %>
<%
List<TBSet> waitingSets = tbGameStorer.loadWaitingSets();
List<TBSet> currentSets = tbGameStorer.loadSets(dsgPlayerData.getPlayerID());
List<TBSet> invitesTo = new ArrayList<TBSet>();
List<TBSet> invitesFrom = new ArrayList<TBSet>();
List<TBGame> myTurn = new ArrayList<TBGame>();
List<TBGame> oppTurn = new ArrayList<TBGame>();
Utilities.organizeGames(dsgPlayerData.getPlayerID(), currentSets,
    invitesTo, invitesFrom, myTurn, oppTurn);

if (myTurn.size() + oppTurn.size() > 0) { 

    List<TBGame> gamesList = new ArrayList<TBGame>(myTurn);
    gamesList.addAll(oppTurn);

    Collections.sort(gamesList, new Comparator<TBGame>() {
    @Override
    public int compare(TBGame o1, TBGame o2) {
        return o1.getGame() - o2.getGame();
    }
});


%>


<script type="text/javascript" src="/gameServer/js/go.js"></script>

<b>Ongoing turn-based games (<%=gamesList.size()%>)</b>

<center>
<table border="0" cellpadding="0" cellspacing="0" width="100%">
  <tr>
    <td>
      <table width="100%" border="0" cellspacing="0" cellpadding="0">
        <!-- <tr bgcolor="<%= bgColor1 %>"> -->
      
            <tr>
<%   int columns = 0;
        for (TBGame game : gamesList) {
            if (game.getTbSet().isPrivateGame() || game.isHidden()) {
                continue;
            } %>
            
              <td>
                <%@ include file="tb/listedMobileGame.jsp" %>
              </td>
              <% if (columns > 1) { 
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
</center>

<br>

<% } %>

