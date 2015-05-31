<%@ page import="org.pente.gameServer.client.web.*,
                 org.pente.game.*" %>

<% pageContext.setAttribute("title", "Player Rankings"); %>
<%@ include file="begin.jsp" %>

        <table border="0" cellpadding="0" cellspacing="0" width="100%">
          <tr>
            <td valign="top"><h3>Player Rankings</h3>

              You can view player rankings for all games sorted by player name, 
              player rating, number of
              player wins, number of player losses, total games, the players
              streak (how many wins, losses in a row) and percentage wins.<br>
              </font>
              <p><font size="2" face="Verdana, Arial, Helvetica, sans-serif">There
              are also a few other options for viewing the stats. You can
              choose to exclude provisional players, and you can also choose to
              exclude players who are inactive. A player is considered
              inactive if they haven't played in over a week. Finally you
              can filter by human or computer players.</font></p>
              
              <h3>Rankings search criteria</h3>
              
              <form name="checkForm" action="<%= request.getContextPath() %>/gameServer/stats" method="POST">
                <p><font size="2" face="Verdana, Arial, Helvetica, sans-serif"><b>View
                Stats for</b></font><br>
                <select size="1" name="game">
                <% Game games[] = GridStateFactory.getDisplayGames();
                   for (int i = 0; i < games.length; i++) { %>
                   <option <% if (i == 0) { %>selected <% } %>value="<%= games[i].getId() %>"><%= games[i].getName() %></option>
                <% } %>
                </select></p>
                <p><font size="2" face="Verdana, Arial, Helvetica, sans-serif"><b>Sort
                Stats by</b></font><br>
                <select size="1" name="sortField">
                  <option value="0">Wins</option>
                  <option value="1">Losses</option>
                  <option value="7">Draws</option>
                  <option selected value="2">Rating</option>
                  <option value="3">Streak</option>
                  <option value="4">Name</option>
                  <option value="5">Total Games</option>
                  <option value="6">Percentage Wins</option>
                </select></p>
                <p><font size="2" face="Verdana, Arial, Helvetica, sans-serif"><b>Advanced
                search options</b></font><br>
                <input type="CHECKBOX" name="includeProvisional" unchecked value="ON">
                <font size="2" face="Verdana, Arial, Helvetica, sans-serif">Include
                provisional players in stats<br>
                <input type="CHECKBOX" name="includeUnactive" unchecked value="ON">
                Include inactive players in stats<br>
                <br>
                <p><font size="2" face="Verdana, Arial, Helvetica, sans-serif"><b>Filter by player type</b></font><br>
                <select size="1" name="playerType">
                  <option value="<%= StatsData.HUMAN %>">Humans
                  <option value="<%= StatsData.AI %>">Computers
                  <option value="<%= StatsData.BOTH %>">Both
                </select></p>

                <input type="submit" value="Get Stats" name="Submit"></font></p>
                <input type="hidden" name="length" value="25">
                <input type="hidden" name="startNum" value="0">
                <input type="hidden" name="command" value="playerStats">
              </form>
            </td>
			<td valign="top" align="right">
			  <%@ include file="rightAd.jsp" %>
			</td>
          </tr>
        </table>

<%@ include file="end.jsp" %>
