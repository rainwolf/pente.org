<%-- displays results for a double elimination format tourney --%>
<%-- assumes Tourney tourney, String game, TourneyRound round is defined --%>

<% for (int j = 0; j < round.getNumSections(); j++) { %>
<br>
<b>Bracket <%= (j + 1)%>
</b> - <a href="javascript:submitToDatabase(
   '<%= gameName %>', 'Pente.org',
   '<%= tourney.getName() %>', '<%= round.getRound() %>', '<%= (j + 1)%>');">View Games in Games History</a></b>
<br>
<table width="100%" border="1" cellspacing="0" cellpadding="1" bordercolor="black">
   <tr bgcolor="<%= bgColor1 %>">
      <th width="25%" align="center"><font color="white">Player 1</font></th>
      <th width="25%" align="center"><font color="white">Result</font></th>
      <th width="25%" align="center"><font color="white">Player 2</font></th>
      <th width="25%" align="center"><font color="white">Score</font></th>
   </tr>
   <%
      SingleEliminationSection section = (SingleEliminationSection) round.getSection(j + 1);
      List matches = section.getSingleEliminationMatches();
      String myTurn = "false";
      for (Iterator it = matches.iterator(); it.hasNext(); ) {
         SingleEliminationMatch m = (SingleEliminationMatch) it.next(); %>

   <tr>
      <%-- p1 column --%>
      <td align="center">
         <b><a href="../profile?viewName=<%= m.getPlayer1().getName() %>">
            <%= m.getPlayer1().getName() %>
         </b></a> (<%= m.getPlayer1().getSeed() %>)
      </td>
      <%-- result column --%>
      <td align="center">
         <%= m.getResultStr() %>
      </td>
      <%-- p2 column --%>
      <% if (m.isBye()) { %>
      <td colspan="2">&nbsp;</td>
      <% } else { %>
      <td align="center">
         <b><a href="../profile?viewName=<%= m.getPlayer2().getName() %>">
            <%= m.getPlayer2().getName() %>
         </b></a> (<%= m.getPlayer2().getSeed() %>)
      </td>
      <%-- wins column --%>
      <td align="center">
         <%= m.getPlayer1Wins() %> - <%= m.getPlayer2Wins() %>
         <% if (tourney.isTurnBased()) {
            TourneyMatch m1 = section.getUnplayedMatch(m.getPlayer1().getPlayerID(), m.getPlayer2().getPlayerID());
            TourneyMatch m2 = section.getUnplayedMatch(m.getPlayer2().getPlayerID(), m.getPlayer1().getPlayerID());
            if (m1 != null || m2 != null) {
               List<TBSet> sets = tbStorer.loadSets(m.getPlayer1().getPlayerID());
               TBSet matchSet = null;
               for (TBSet s : sets) {
                  if (s.getGame1().getEventId() == eid && s.getState() == TBSet.STATE_ACTIVE) {
                     matchSet = s;
                     break;
                  }
               }
               if (matchSet != null) {
         %>
         <% if (m1 != null || m2 != null) { %>
      </td>
   </tr>

   <tr>
      <td colspan="<%=(isTBSingleGame?"4":"2")%>" align="center">
         <% if (true) {
            TBGame game = matchSet.getGame1();
            if (game != null && !game.isCompleted() && !game.isHidden()) {
         %>
         <%@ include file="../tb/listedMobileGame.jsp" %>
         <% }
         } %>
         <% if (!isTBSingleGame) { %>
      </td>
      <td colspan="2" align="center">
         <% } %>
         <% if (true) {
            TBGame game = matchSet.getGame2();
            if (game != null && !game.isCompleted() && !game.isHidden()) {
         %>
         <%@ include file="../tb/listedMobileGame.jsp" %>
         <% }
         } %>
         <% } %>
         <% } %>
         <% } %>
         <% } %>
      </td>
      <% } %>
   </tr>
   <%
      }
   %>
</table>
<% } %>
 