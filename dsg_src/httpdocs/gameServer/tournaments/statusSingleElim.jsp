<%-- displays results for a single elimination format tourney --%>
<%-- assumes Tourney tourney, String game, TourneyRound round is defined --%>

<br>
<a href="javascript:submitToDatabase('<%= gameName %>', 'Pente.org',
'<%= tourney.getName().replace("\'","\\\'") %>', '<%= round.getRound() %>', '1');">View Games in Games History</a></b>
<br>
<br>
<table width="100%" border="1" cellspacing="0" cellpadding="1" bordercolor="black">
   <tr bgcolor="<%= bgColor1 %>">
      <th width="25%" align="center"><font color="white">Player 1</font></th>
      <th width="25%" align="center"><font color="white">Result</font></th>
      <th width="25%" align="center"><font color="white">Player 2</font></th>
      <th width="25%" align="center"><font color="white">Score</font></th>
   </tr>
   <%
      SingleEliminationSection section = (SingleEliminationSection) round.getSection(1);
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
         <% String resultStr = m.getResultStr();
            if (m.getPlayer1Wins() == m.getPlayer2Wins() && m.getPlayer1Wins() + m.getPlayer2Wins() > 0) {
               resultStr = "tied with";
            }
         %>
         <%= resultStr %>
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
//              TourneyMatch m1 = null;
//              TourneyMatch m2 = null;
            if (m1 != null || m2 != null) {
               List<TBSet> sets = tbStorer.loadSets(m.getPlayer1().getPlayerID());
               TBSet matchSet = null;
               for (TBSet s : sets) {
//                   if (s.isTwoGameSet() && s.getGame1().getEventId()==eid && s.getState() == TBSet.STATE_ACTIVE) {
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