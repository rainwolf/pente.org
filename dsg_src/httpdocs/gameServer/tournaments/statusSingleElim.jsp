<%-- displays results for a single elimination format tourney --%>
<%-- assumes Tourney tourney, String game, TourneyRound round is defined --%>

<br>
<a href="javascript:submitToDatabase('<%= game %>', 'Pente.org',
   '<%= tourney.getName() %>', '<%= round.getRound() %>', '1');">View Games in Games History</a></b>
<br>
<br>
<table width="100%" border="1" cellspacing="0" cellpadding="1" bordercolor="black">
  <tr bgcolor="<%= bgColor1 %>">
    <th width="30%" align="left"><font color="white">Player 1</font></th>
    <th width="30%" align="center"><font color="white">Result</font></th>	
    <th width="30%" align="left"><font color="white">Player 2</font></th>
    <th width="10%" align="left"><font color="white">Score</font></th>
  </tr>
<%
SingleEliminationSection section = (SingleEliminationSection) round.getSection(1);
List matches = section.getSingleEliminationMatches();
for (Iterator it = matches.iterator(); it.hasNext();) {
    SingleEliminationMatch m = (SingleEliminationMatch) it.next(); %>
    
    <tr>
      <%-- p1 column --%>
      <td>
         <b><a href="../profile?viewName=<%= m.getPlayer1().getName() %>">
           <%= m.getPlayer1().getName() %></b></a> (<%= m.getPlayer1().getSeed() %>)
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
           <%= m.getPlayer2().getName() %></b></a> (<%= m.getPlayer2().getSeed() %>)
      </td>
      <%-- wins column --%>
      <td>
          <%= m.getPlayer1Wins() %> - <%= m.getPlayer2Wins() %>
      </td>
      <% } %>
    </tr>
<%
}
%>
 
</table>    