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
                                  res = "<a href=/gameServer/viewLiveGame?g=" + results[i][j * 6 + 2] + ">" +
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
                                  res = "<a href=/gameServer/viewLiveGame?g=" + results[i][j * 6 + 5] + "><font color=white>" +
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