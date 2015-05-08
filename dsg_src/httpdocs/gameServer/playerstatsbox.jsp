<table border="0" cellpadding="0" cellspacing="0" width="100%">
  <tr>
    <td>
      Click game name to view Completed Games
    </td>
  </tr>
  <tr>
    <td>
      <table width="100%" border="0" cellspacing="0" cellpadding="0">
        <tr bgcolor="<%= bgColor1 %>">
      
<%   
        String sortFieldNames[] = new String[] { "Game", "Rating", "Wins", "Losses", "Draws", "Total", "% Wins", "Streak", "Last Game" };
        for (int i = 0; i < sortFieldNames.length; i++) {
          out.write("<td><font face=\"Verdana,Arial,Helvetica\" size=\"1\" color=\"white\"><b>" + sortFieldNames[i] + "</b></font></td>\n");
        }
      
        out.write("</tr>\n");
        
        Game games[] = GridStateFactory.getDisplayGames();
        for (int i = 0; i < games.length; i++) {
            DSGPlayerGameData dsgPlayerGameData =
                dsgPlayerData.getPlayerGameData(games[i].getId());
            if (dsgPlayerGameData == null ||
                dsgPlayerGameData.getTotalGames() == 0) {
                continue;
            } %>
            
            <tr>
              <td>
              <a href="/gameServer/viewLiveGames?p=<%= dsgPlayerData.getName() %>&g=<%= games[i].getId() %>">
                <%= games[i].getName() %></a>
              </td>
              <td>
                <%@ include file="ratings.jspf" %>
                <% tourneyWinner = dsgPlayerGameData.getTourneyWinner(); %>
                <%@ include file="/gameServer/tournaments/crown.jspf" %>
              </td>
              <td><%= nf.format(dsgPlayerGameData.getWins()) %></td>
              <td><%= nf.format(dsgPlayerGameData.getLosses()) %></td>
              <td><%= dsgPlayerGameData.getDraws() %></td>
              <td><%= nf.format(dsgPlayerGameData.getTotalGames()) %></td>
              <td><%= (dsgPlayerGameData.getWins() + dsgPlayerGameData.getLosses() == 0 ? "" : profileNF.format(dsgPlayerGameData.getPercentageWins())) %></td>
              <td><%= (dsgPlayerGameData.getStreak() > -1 ? "&nbsp;" : "") + dsgPlayerGameData.getStreak() %></td>
              <td><%= dateFormat.format(dsgPlayerGameData.getLastGameDate()) %></td>
            </tr>
   <%   }  %> 
      </table>
    </td>
  </tr>
</table>
<br>

<form method="post" name="profilesearch" id="profilesearch" action="<%= request.getContextPath() %>/gameServer/profile" 
      style="margin:0;padding:0;">
    <div class="buttonwrapper">
    <a class="boldbuttons" href="javascript:document.profilesearch.submit();" 
       style="margin-right:5px;"><span>View Another Player's Profile</span></a>
    <div style="margin-top:5px;">
       <input type="text" name="viewName" size="8">
    </div>
</div>
</form>