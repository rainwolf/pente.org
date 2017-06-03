<%-- displays results for a swiss format tourney --%>
<%-- assumes String game, TourneyRound round is defined --%>

<br>
<a href="javascript:submitToDatabase('<%= gameName %>', 'Pente.org',
   '<%= tourney.getName() %>', '<%= round.getRound() %>', '1');">View Games in Games History</a></b>
<br>
<br>
<table border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td valign="top">
<b>Matches</b>
<table width="400" border="0" cellspacing="0" cellpadding="0" bordercolor="black">
  <tr bgcolor="<%= bgColor1 %>">
    <th width="25%" align="left"><font color="white">Player 1</font></th>
    <th width="25%" align="center"><font color="white">Result</font></th> 
    <th width="25%" align="left"><font color="white">Player 2</font></th>
    <th width="25%" align="center"><font color="white">Score</font></th>
  </tr>
<%
SwissSection section = (SwissSection) round.getSection(1);
List matches = section.getSwissMatches();
for (Iterator it = matches.iterator(); it.hasNext();) {
    SingleEliminationMatch m = (SingleEliminationMatch) it.next(); %>
    
    <tr>
      <%-- p1 column --%>
      <td>
         <b><a href="../profile?viewName=<%= m.getPlayer1().getName() %>">
           <%= m.getPlayer1().getName() %></a></b>
      </td>
      <%-- result column --%>
      <td align="center">
         <%= m.getResultStr() %>
      </td>
      <%-- p2 column --%>
      <% if (m.isBye()) { %>
      <td colspan="2">&nbsp;</td>
      <% } else { %>
      <td>
         <b><a href="../profile?viewName=<%= m.getPlayer2().getName() %>">
           <%= m.getPlayer2().getName() %></a></b>
      </td>
      <%-- wins column --%>
      <td align="center">
          <%= m.getPlayer1Wins() %> - <%= m.getPlayer2Wins() %>
      </td>
      <% } %>
    </tr>
<%
}
%>
 
</table>
  </td>
  <td valign="top" width="100%" align="center">
<b>Current Standings</b>
<table border="0" cellspacing="0" cellpadding="0" bordercolor="black">
  <tr bgcolor="<%= bgColor1 %>">
    <th align="left"><font color="white">Rank&nbsp;&nbsp;</font></th>
    <th align="left"><font color="white">Name</font></th>
    <th align="left"><font color="white">Score</font></th>
  </tr>
  
<%  int rank = 0; 
    int count = 0;
    int prevWins = 1000;
    boolean cutoff = false;
    for (Iterator it = section.getPlayersRanked(tourney).iterator();
         it.hasNext();) {
        TourneyPlayerData p = (TourneyPlayerData) it.next(); 
        count++;
        if (p.getMatchWins() < prevWins) {
            prevWins = p.getMatchWins();
            rank = count;
            if (!cutoff && count > 4) { 
              cutoff = true; %>
            <tr>
              <td><hr></td>
              <td>&nbsp;Playoff Cutoff&nbsp;</td>
              <td><hr></td>
            </tr>
<%          }
        } %>
        
        <tr>
          <td align="center"><%= rank %></td>
          <td><b><a href="../profile?viewName=<%= p.getName() %>">
              <%= p.getName() %></a></b></td>
          <td><%= p.getMatchWins() %> - <%= p.getMatchLosses() %></td>
        </tr>
<%  } %>
</table>
</td>
</tr>
</table>
