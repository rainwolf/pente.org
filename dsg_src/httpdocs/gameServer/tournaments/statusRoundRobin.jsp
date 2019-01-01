<%-- displays results for a round-robin format tourney --%>
<%-- assumes Tourney tourney, String game, TourneyRound round is defined --%>

            <br>
            Results are displayed below grouped by section.  The sections
            are displayed as a grid, where every player plays every other player
            twice, once as player 1 (white), and once as player 2 ( black).
            When you read the results, follow the grid from left to right, NOT
            top to bottom.  For example, if you see a 'W' in a white column, that
            means that the player in that row Won as White against the player
            in that column. At the right is the total wins for each player, and
            if a player will advance to the next round, they will be highlighted
            in yellow.  More than one player can advance if there is a tie.<br>
            <br>

<center>
         <% for (Iterator it = round.getSections().iterator(); it.hasNext();) {
               boolean forfeit = false;
               RoundRobinSection section = (RoundRobinSection) it.next(); %>
         
               <b>Section <%= section.getSection() %> - 
                  <a href="javascript:submitToDatabase('<%= gameName %>',
                    'Pente.org',
                    '<%= tourney.getName() %>', 
                    '<%= round.getRound() %>', 
                    '<%= section.getSection() %>');">View Games</a></b>
               <br>
               <% List players = section.getPlayers();
                  long results[][] = section.getResultsMatrix(); 
                  double width = 65.0 / (players.size()); %>
                  
               <table width="500" border="1" cellspacing="0" cellpadding="1" bordercolor="black" >
                 <tr>
                   <td bgcolor="<%= bgColor1 %>" width="25%">
                     <b><font color="white">Player</font></b>
                   </td>
            <% for (int i = 0; i < players.size(); i++) { %>
                   <td align="center" colspan="2" width="<%= width %>%">
                     <font color="#808080"> 
                       <%= ((TourneyPlayerData) players.get(i)).getName() %>
                   </font></td>
            <% } %>
                   <td bgcolor="<%= bgColor1 %>" width="10%">
                     <b><font color="white">Total</font></b>
                   </td>
                 </tr>
            <% for (int i = 0; i < players.size(); i++) {
                 TourneyPlayerData tpd = (TourneyPlayerData) players.get(i); %>
                 <tr>
                   <td <% if (section.isWinner(i)) { %>bgcolor="yellow" <% } %>>
                      <b><a href="../profile?viewName=<%= tpd.getName() %>">
                        <%= tpd.getName() %></b></a> (<%= tpd.getSeed() %>)
                   </td>
                   <% for (int j = 0; j < players.size(); j++) {
                       if (i == j) { %>
                         <td colspan="2" bgcolor="#c0c0c0">&nbsp;</td>
                       <% } else { %>
                         <td align="center">
                           <% String res = "";
                              if (results[i][j * 6] == 1) {
                                  res = "W";
                              } else if (results[i][j * 6] == 2 ||
                                         results[i][j * 6] == 3) {
                                  res = "L";
                              } else {
                                  res = "-";
                              }
                              if (results[i][j * 6 + 1] == 1) {
                                  forfeit = true;
                                  res += "*";
                              }
                              if (!res.equals("-") && !res.endsWith("*")) {
                                  res = "<a href=/gameServer/viewLiveGame?mobile&g=" + results[i][j * 6 + 2] + ">" +
                                      res + "</a>";
                              }
                           %>
                           <%= res %>
                         </td>
                         <td bgcolor="#404040" align="center">
                           <font color="white">
                           <% res = "";
                              if (results[i][j * 6 + 3] == 2) {
                                  res = "W";
                              } else if (results[i][j * 6 + 3] == 1 ||
                                         results[i][j * 4 + 3] == 3) {
                                  res = "L";
                              } else {
                                  res = "-";
                              }
                              if (results[i][j * 6 + 4] == 1) {
                                  res += "*";
                                  forfeit = true;
                              }
                              
                              if (!res.equals("-") && !res.endsWith("*")) {
                                  res = "<a href=/gameServer/viewLiveGame?mobile&g=" + results[i][j * 6 + 5] + "><font color=white>" +
                                      res + "</font></a>";
                              }
                           %>
                           <%= res %>
                         </font></td>
                       <% } %>
                   <% } %>
                   <td <% if (section.isWinner(i)) { %>bgcolor="yellow" <% } %> align="center">
                     <font color="red">
                       <%= results[i][results[i].length - 1] %>
                   </font></td>
                 </tr>
            <% } %>
                </tr>
              </table>
              <% if (forfeit) { %>
                * Game decided by forfeit
                <br>
              <% } %>
              <br>
         <% } %>

<% if (tourney.isTurnBased()) { %>
    <%
    for (Iterator it = round.getSections().iterator(); it.hasNext();) {
        r = 0;
        RoundRobinSection section = (RoundRobinSection) it.next();
        
        %> <p> <br> <table width="100%" cellspacing="0" cellpadding="1">
        <b>Section <%= section.getSection() %>
        <%
        List<TourneyMatch> matches = section.getMatches();
        %> <tr> <%
        for (TourneyMatch m : matches) {
            TourneyMatch m1 = section.getUnplayedMatch(m.getPlayer1().getPlayerID(), m.getPlayer2().getPlayerID());
            TourneyMatch m2 = section.getUnplayedMatch(m.getPlayer2().getPlayerID(), m.getPlayer1().getPlayerID());
            if (m1 != null || m2 != null) {
                List<TBSet> sets = tbStorer.loadSets(m.getPlayer1().getPlayerID());
                for (TBSet s : sets) {
                    if (s.getGame1().getEventId() == eid && 
                            s.getState() == TBSet.STATE_ACTIVE &&
                            s.getGame1().getPlayer2Pid() == m.getPlayer2().getPlayerID()) {
                        if (s.getGame1().getState() == TBGame.STATE_ACTIVE) {
                            TBGame game = s.getGame1(); 
                            %> <td width="33%" align="center"> 
                            <%@ include file="../tb/listedMobileGame.jsp" %>
                            </td> <%
                            r++;
                        }
                        if (r == 3) {
                            %> </tr> <tr> <%
                            r = 0;
                        }
                        if (s.getGame2().getState() == TBGame.STATE_ACTIVE) {
                            TBGame game = s.getGame2();
                            %> <td width="33%" align="center">
                                <%@ include file="../tb/listedMobileGame.jsp" %>
                            </td> <%
                            r++;
                        }
                        if (r == 3) {
                            %> </tr> <tr> <%
                            r = 0;
                        }
                    }
                }
            }
        }
        %> </tr> <%
        %> </table> <br> </p> <%
    }
    %>
</center>
<% } %>
