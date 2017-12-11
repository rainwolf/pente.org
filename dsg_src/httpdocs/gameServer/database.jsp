<%@ page import="org.pente.gameDatabase.*, 
org.pente.game.*, java.text.*, java.util.*" %>

<%! private static final NumberFormat percentFormat =
        NumberFormat.getPercentInstance();
    
    static {
        percentFormat.setMaximumFractionDigits(1);
    }

    private String formatBodyOnLoad(GameStorerSearchResponseData data) {

        String onLoad = "initializeGame(); initSelects('filter_options_data', ";

        GameStorerSearchRequestFilterData filterData = data.getGameStorerSearchRequestData().getGameStorerSearchRequestFilterData();
        String site = filterData.getSite() == null ? "" : filterData.getSite();
        String event = filterData.getEvent() == null ? "" : filterData.getEvent();
        String round = filterData.getRound() == null ? "" : filterData.getRound();
        String section = filterData.getSection() == null ? "" : filterData.getSection();
        
        onLoad += "'" + GridStateFactory.getGameName(filterData.getGame()) + "', ";
        onLoad += "'" + safeSingleQuote(site) + "', ";
        onLoad += "'" + safeSingleQuote(event) + "', ";
        onLoad += "'" + safeSingleQuote(round) + "', ";
        onLoad += "'" + safeSingleQuote(section) + "');";

        return onLoad;
    }
    private String safeSingleQuote(String s) {

        StringBuffer sb = new StringBuffer(s);
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '\'') {
                sb.insert(i, '\\');
                i++;
            }
        }

        return sb.toString();
    }

        
    private String[][] buildBoardImages(String context,
        GameStorerSearchResponseData data) {

        String BLANK_IMAGE = context + "/gameServer/images/blank.gif";
        String DOT_IMAGE = context + "/gameServer/images/dot.gif";
        String PLAYER_IMAGES[] = new String[] { 
            context + "/gameServer/images/white.gif",
            context + "/gameServer/images/black.gif" };
            
        // fill the board with blank spaces
        String boardImages[][] = new String[19][19];
        for (int i = 0; i < boardImages.length; i++) {
            for (int j = 0; j < boardImages[i].length; j++) {
                boardImages[i][j] = BLANK_IMAGE;
            }
        }

        // put dots at tournament rule boundary
        boardImages[9][9] = DOT_IMAGE;
        boardImages[6][6] = DOT_IMAGE;
        boardImages[6][12] = DOT_IMAGE;
        boardImages[12][6] = DOT_IMAGE;
        boardImages[12][12] = DOT_IMAGE;

        // put query result moves on the board
        Vector searchResultsVector = data.searchResponseMoveData();
        for (int i = 0; i < searchResultsVector.size(); i++) {
            GameStorerSearchResponseMoveData moveData = (GameStorerSearchResponseMoveData) searchResultsVector.elementAt(i);
            int x = moveData.getMove() % 19;
            int y = moveData.getMove() / 19;
            boardImages[y][x] = context + "/gameServer/images/light_green.gif";
        }

        return boardImages;
    }

%>

<% GameStorerSearchResponseData data = (GameStorerSearchResponseData)
       request.getAttribute("responseData");
   GameStats gameStats = (GameStats) request.getAttribute("gameStats");
   GameStorerSearchRequestFilterData filterData =
       data.getGameStorerSearchRequestData().getGameStorerSearchRequestFilterData();

    String nm = (String) request.getAttribute("name");
    DSGPlayerData pdata = null;
    if (nm != null) {
        pdata = dsgPlayerStorer.loadPlayer(nm);
    }
    boolean dbAccess = true;
    if (pdata == null) {
        dbAccess = false;
    } else {
        dbAccess = pdata.databaseAccess() || pdata.getRegisterDate().getTime() > System.currentTimeMillis() - 1000L*3600*24*30;
    }
    // dbAccess = true;
%>

<% String searchURL = request.getContextPath() + "/gameServer/controller/search?quick_start=1";
   pageContext.setAttribute("title", "Games History");
   pageContext.setAttribute("googleSide", new Boolean(false));
   pageContext.setAttribute("onLoad", formatBodyOnLoad(data)); %>

<%@ include file="begin.jsp" %>


<table width="100%" border="0" cellspacing="0" cellpadding="0">
<tr>
<td valign="top" colspan="2">
<h3>Game Database</h3>
Welcome to the Pente.org Game Database, the best way to learn Pente strategy
when you aren't Playing Pente! 
The database currently houses a record of Pente games played since
01/2000 at <a href="http://www.pente.org/"><b>Pente.org</b></a>,
a selection of games from <a href="http://www.gamerz.net/pbmserv/"><b>PBeM</b></a>,
a selection of games from <a href="http://www.brainking.com"><b>BrainKing</b></a>, 
and some older Pro-Pente tournament games from <a href="http://www.itsyourturn.com/"><b>IYT</b></a>. <br>
<br>
Read the <b><a href="/help/helpWindow.jsp?file=gamesHistory">
Game Database Instructions</a></b> to get the most information out of this tool.<br>

    <%
    if (dbAccess) { %>


<br>
<table width="390" border="1" cellspacing="0" cellpadding="1" bordercolor="black" bgcolor="<%= bgColor1 %>">
<tr>
<td><b><font color="white"># Games</font></td>
<td><b><font color="white"># Moves</font></td>
<td><b><font color="white"># Players</font></td>
<td><b><font color="white"># Sites</font></td>
</tr>

<tr>
<td><b><font color="white"><%= numberFormat.format((long) gameStats.getNumGames()) %></b></font></td>
<td><b><font color="white"><%= numberFormat.format((long) gameStats.getNumMoves()) %></b></font></td>
<td><b><font color="white"><%= numberFormat.format((long) gameStats.getNumPlayers()) %></b></font></td>
<td><b><font color="white"><%= numberFormat.format((long) gameStats.getNumSites()) %></b></font></td>
</tr>

</table>

</td>

    <%
//    String nm = (String) request.getAttribute("name");
//    DSGPlayerData pdata = null;
    if (nm != null) {
        pdata = dsgPlayerStorer.loadPlayer(nm);
    }
    if (pdata == null || pdata.showAds()) { %>
    <td valign="top" align="center" rowspan="3" width="300">
          <div style="width:160px;height:600px;margin-top:50px">
            <script async src="//pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"></script>
            <!-- penteORGDB -->
            <ins class="adsbygoogle"
                 style="display:inline-block;width:160px;height:600px"
                 data-ad-client="ca-pub-3326997956703582"
                 data-ad-slot="7539819041"></ins>
            <script>
            (adsbygoogle = window.adsbygoogle || []).push({});
            </script>
      </div>
    </td>
    <% } %>
<%--
--%>

</tr>
<tr><td>&nbsp;</td></tr>


<% boolean showGames = true;
   if (request.getAttribute("blocked") != null) {
       showGames = false; %>
<tr>
 <td colspan="2">
  <font color="red">
    You have been temporarily blocked from viewing the Games History.
    This means one of several things.<br>
    <ol>
     <li>You are currently playing a rated game at Pente.org, therefore you are not
      allowed to use the Games Database.</li>
     <li>Someone else is currently playing a rated game, and is playing
      the same position you searched for.  Try again in a minute.</li>
     <li>Someone else is currently playing a rated game, and you share
      an IP address with them.  Try again in a little while.</li>
    </ol>
    Please read <a href="javascript:helpWin('ratedPolicy');">
      Pente.org''s policy for Rated Games</a> for more information.
  </font>
 </td>
</tr>
<% } %>

<tr>
<td width="800" align="left" valign="top">
<table cellspacing="0" cellpadding="0" border="0">
<tr>
<td>&nbsp;</td>
<td></td>
<%
        for (int i = 0; i < 19; i++) {
            char xx[] = new char[1];
            xx[0] = (char) (65 + i);
            if (xx[0] > 72) xx[0]++;
            String coord = new String(xx); %>
<td align="center"><%= coord %></td>
<%      } %>
<td></td>
<td>&nbsp;</td>
</tr>

<%  int borderWidth = 5;
    int tileWidth = 24;
    int totalWidth = 19*tileWidth + 2*borderWidth; %>

<tr><td></td><td colspan="21" width="<%=totalWidth%>"><img src="<%= request.getContextPath() %>/gameServer/images/black_pixel.gif" width="<%=totalWidth%>" height="5"></td><td></td></tr>

<%
        String boardImages[][] = buildBoardImages(request.getContextPath(), data);

        for (int i = 0; i < 19; i++) { %>

<tr>
<td width="19"><%= (19 - i) %></td>
<%-- left column black border --%>
<td width="5"><img src="<%= request.getContextPath() %>/gameServer/images/black_pixel.gif" width="5" height="<%=tileWidth%>"></td> 

<%          for (int j = 0; j < 19; j++) {

                int move = i * 19 + j;
                String moveStr = PGNGameFormat.formatCoordinates(move);
%>
<td><a href="javascript:addMove('<%= moveStr %>')"
<%              if (boardImages[i][j].endsWith("light_green.gif")) { %>
onmouseover="javascript:highlightStat('<%= moveStr %>s');" onmouseout="javascript:unHighlightStat('<%= moveStr %>s');"
<%              } %>
><img name="<%= moveStr %>" src="<%= boardImages[i][j] %>" width="<%=tileWidth%>" height="<%=tileWidth%>" border="0"></td>
<%          } %>

<%-- right column black border --%>
<td width="5"><img src="<%= request.getContextPath() %>/gameServer/images/black_pixel.gif" width="5" height="<%=tileWidth%>"></td>
<td width="19"><%= (19 - i) %></td>
</tr>
<%      } %>


<tr><td></td><td colspan="21" width="<%=totalWidth%>"><img src="<%= request.getContextPath() %>/gameServer/images/black_pixel.gif" width="<%=totalWidth%>" height="5"></td><td></td></tr>

<tr>
<td>&nbsp;</td>
<td></td>
<%
        for (int i = 0; i < 19; i++) {
            char xx[] = new char[1];
            xx[0] = (char) (65 + i);
            if (xx[0] > 72) xx[0]++;
            String coord = new String(xx); %>
<td align="center"><%= coord %></td>
<%      } %>
<td></td>
<td>&nbsp;</td>
</tr>


<tr><td>&nbsp;</td>
<td></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc0" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc1" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc2" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc3" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc4" width="19" height="19"></td>
    <td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc0" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc1" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc2" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc3" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc4" width="19" height="19"></td>
<td></td><td>&nbsp;</td>
</tr>
<tr><td>&nbsp;</td>
<td></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc5" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc6" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc7" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc8" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc9" width="19" height="19"></td>
    <td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc5" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc6" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc7" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc8" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc9" width="19" height="19"></td>
<td></td><td>&nbsp;</td>
</tr>
<tr><td>&nbsp;</td>
<td></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc10" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc11" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc12" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc13" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc14" width="19" height="19"></td>
    <td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc10" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc11" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc12" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc13" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc14" width="19" height="19"></td>
<td></td><td>&nbsp;</td>
</tr>
<tr><td>&nbsp;</td>
<td></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc15" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc16" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc17" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc18" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="wc19" width="19" height="19"></td>
    <td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc15" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc16" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc17" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc18" width="19" height="19"></td>
<td><image src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif" name="bc19" width="19" height="19"></td>
<td></td><td>&nbsp;</td>
</tr>



</table>

<%
        SimpleGameStorerSearchRequestFormat requestFormat = new SimpleGameStorerSearchRequestFormat();
        SimpleGameStorerSearchResponseFormat responseFormat = new SimpleGameStorerSearchResponseFormat();
        StringBuffer moves = new StringBuffer();
        requestFormat.formatMoves(data.getGameStorerSearchRequestData(), moves, false, false);

        StringBuffer results = new StringBuffer();
        responseFormat.formatMoveResults(data, results, false);
%>

<center>
<form name="data_form">
<input type="hidden" name="response_format" value="org.pente.gameDatabase.SimpleHtmlGameStorerSearchResponseFormat">
<input type="hidden" name="moves" value="<%= moves.toString() %>">
<input type="hidden" name="game" value="<%= filterData.getGame() %>">
<input type="hidden" name="results_order" value="<%= data.getGameStorerSearchRequestData().getGameStorerSearchResponseOrder() %>">
<input type="hidden" name="zippedPartNumParam" value="<%= ((SimpleHtmlGameStorerSearchRequestData) data.getGameStorerSearchRequestData()).getStartZippedPartNum() %>">
<table width="100%" border="0">
<tr>
<td width="45%" align="center">
<input type="button" onclick="javascript:firstMove()" value=" << ">
<input type="button" onclick="javascript:backMove()" value="  <  ">
<input type="button" onclick="javascript:forwardMove()" value="  >  ">
<input type="button" onclick="javascript:lastMove()" value=" >> ">
</td><td width="55%" align="center">
<input type="button" onclick="javascript:resetGame()" value="Reset">
<input type="button" onclick="javascript:clearGame('K10')" value="Clear">
<input type="button" onclick="javascript:search()" value="Search">
</td>
</tr>
</table>
</form>
<script language="javascript">
var results = "<%= results.toString() %>";
var numResults = "<%= data.getNumSearchResponseMoves() %>";
var imagePath = "<%= request.getContextPath() %>/gameServer/images/";
</script>
<script language="javascript" src="<%= request.getContextPath() %>/gameServer/js/database.js"></script>
</center></td>
<td align="left" valign="top">
<div align="left" id="statsDiv" style="position:relative<%= !showGames ? ";visibility:hidden" : ""%>">
<table width="100" border="1" cellspacing="0" cellpadding="1" bordercolor="black">
<tr bgcolor="<%= bgColor2 %>">
<td colspan="4"><b>Statistics</b></td>
</tr>

<tr bgcolor="<%= bgColor2 %>">
<%! private static final String headers[] = new String[] { "#", "Move", "Games", "Wins" }; %>

<%      int responseOrder = data.getGameStorerSearchRequestData().getGameStorerSearchResponseOrder() + 1;
        for (int i = 0; i < headers.length; i++) {

            String color = "black";
            String header = headers[i];

            // nothing special for #
            if (i > 0) {

                // highlight the current order in red
                if (responseOrder == i) {
                    color = textColor2;
                }
                // other orders have links
                else {
                    header = "<a href=\"javascript:sortResults('" + (i - 1) + "');\">" + header + "</a>";
                }
            }
%>
<td><font color="<%= color %>"><b><%= header %></b></font></td>
<%      }
        int totalGames = 0;
        int totalWins = 0;

        Vector searchResults = data.searchResponseMoveData();
        for (int i = 0; i <= searchResults.size(); i++) {

            GameStorerSearchResponseMoveData moveData = null;

            // after all results shown, show totals
            if (i == searchResults.size()) {
                moveData = new SimpleGameStorerSearchResponseMoveData();
                moveData.setGames(totalGames);
                moveData.setWins(totalWins);
            }
            else {
                moveData = (GameStorerSearchResponseMoveData) searchResults.elementAt(i);

                totalGames += moveData.getGames();
                totalWins += moveData.getWins();
            } %>
<tr bgcolor="<%= bgColor2 %>">
<%          if (i == searchResults.size()) { %>
<td colspan="2"><b>Total</b></td>
<%          }
            else { %>
<td><%= (i + 1) %></td>
<%              String move = PGNGameFormat.formatCoordinates(moveData.getMove()); %>
<td><img name="<%= move %>s" src="<%= request.getContextPath() %>/gameServer/images/stats_blank.gif">
<a style="font-weight:normal;" href="javascript:addMove('<%= move %>');"
   onmouseover="javascript:highlightMove('<%= move %>');"
   onmouseout="javascript:unHighlightMove('<%= move %>');"><%= move %></a></td>
<%          } %>

<td><%= numberFormat.format((long) moveData.getGames()) %></td>
<td><%= percentFormat.format(moveData.getPercentage()) %></td></tr>

<%      } %>
</table>
</div>



</td></tr>

<%
        
        String player1Name = filterData.getPlayer1Name();
        if (player1Name == null) {
            player1Name = "";
        }
        String player2Name = filterData.getPlayer2Name();
        if (player2Name == null) {
            player2Name = "";
        }

        String afterDate = "";
        if (filterData.getAfterDate() != null) {
            // subtract 1 day since javascript adds one day
            Calendar cal = Calendar.getInstance();
            cal.setTime(filterData.getAfterDate());
            cal.add(Calendar.DATE, -1);
            afterDate = SimpleGameStorerSearchRequestFilterFormat.shortDateFormat.format(cal.getTime());
        }
        String beforeDate = "";
        if (filterData.getBeforeDate() != null) {
            beforeDate = SimpleGameStorerSearchRequestFilterFormat.shortDateFormat.format(filterData.getBeforeDate());
        }

        String selectNames[] = new String[] { "Game", "Site", "Event", "Round", "Section", "Type" };
        StringBuffer table[][] = new StringBuffer[6][4];
        for (int i = 0; i < table.length; i++) {
            for (int j = 0; j < table[i].length; j++) {
                table[i][j] = new StringBuffer();
            }
        }

        for (int i = 0; i < selectNames.length - 1; i++) {

            table[i][0].append("<b><font color=\"white\">");
            table[i][0].append(selectNames[i]);
            table[i][0].append("</font></b>");
            table[i][1].append("<select style=\"width: 250px\" name=\"");
            table[i][1].append(selectNames[i].toLowerCase() + "Select");
            table[i][1].append("\" onchange=\"javascript:");
            table[i][1].append(selectNames[i].toLowerCase() + "SelectChange();\"");
            table[i][1].append(" tabindex=\"" + (i + 1) + "\">\r\n");
            table[i][1].append("<option>");
            for (int j = 0; j < 30; j++) {
                table[i][1].append("&nbsp;");
            }
            table[i][1].append("</option>\r\n");
            for (int j = 0; j < 4; j++) {
                table[i][1].append("<option>&nbsp;</option>\r\n");
            }
            table[i][1].append("</select>");
        }

        table[5][0].append("<b><font color=\"white\">"+selectNames[5]+"</font></b>");
        table[5][1].append("<select style=\"width: 250px\" name=\"");
        table[5][1].append(selectNames[5].toLowerCase() + "Select\"");
        table[5][1].append(" tabindex=\"" + selectNames.length + "\">\r\n");
        table[5][1].append("<option value=\"all\">live and turn-based</option>");
        table[5][1].append("<option value=\"live\" "+(filterData.isOnlyLive()?"selected":"")+">live only</option>");
        table[5][1].append("<option value=\"turn_based\" "+(filterData.isOnlyTurnBased()?"selected":"")+">turn-based only</option>");
        table[5][1].append("</select>");

        int tabIdx = selectNames.length+1;

        table[0][2].append("<b><font color=\"white\">Player 1 Name</font></b></td>");
        table[0][3].append("<input type=\"text\" name=\"" + SimpleGameStorerSearchRequestFilterFormat.PLAYER_1_NAME_PARAM +  "\" value=\"" + player1Name + "\" placeholder=\"comma-separated list/wildcards\" size=\"25\" tabindex=\""+(tabIdx++)+"\">");
        table[0][3].append("&nbsp<select name=\"" + SimpleGameStorerSearchRequestFilterFormat.P1RATING_PARAM + "\" tabindex=\""+(tabIdx++)+"\">");
        table[0][3].append("<option value=\"0\">0</option>");
        for (int i = 1600; i < 2800; i += 100) {
            table[0][3].append("<option value=\"" + i + "\"");
            if (filterData.getRatingP1Above() == i) {
                table[0][3].append(" selected");
            }
            table[0][3].append(">above " + i + "</option>");
        }
        table[0][3].append("</select>");

        table[1][2].append("<b><font color=\"white\">Player 2 Name</font></b>");
        table[1][3].append("<input type=\"text\" name=\"" + SimpleGameStorerSearchRequestFilterFormat.PLAYER_2_NAME_PARAM + "\" value=\"" + player2Name + "\" placeholder=\"comma-separated list/wildcards\" size=\"25\" tabindex=\""+(tabIdx++)+"\">");
        table[1][3].append("&nbsp<select name=\"" + SimpleGameStorerSearchRequestFilterFormat.P2RATING_PARAM + "\" tabindex=\""+(tabIdx++)+"\">");
        table[1][3].append("<option value=\"0\">0</option>");
        for (int i = 1600; i < 2800; i += 100) {
            table[1][3].append("<option value=\"" + i + "\"");
            if (filterData.getRatingP2Above() == i) {
                table[1][3].append(" selected");
            }
            table[1][3].append(">above " + i + "</option>");
        }
        table[1][3].append("</select>");

        table[2][2].append("<b><font color=\"white\">After Date</font></b>");
        table[2][3].append("<input type=\"text\" name=\"" + SimpleGameStorerSearchRequestFilterFormat.AFTER_DATE_PARAM + "\" value=\"" + afterDate + "\" placeholder=\"MM/dd/YYYY format\" size=\"25\" maxlength=\"10\" tabindex=\""+(tabIdx++)+"\">");

        table[3][2].append("<b><font color=\"white\">Before Date</font></b>");
        table[3][3].append("<input type=\"text\" name=\"" + SimpleGameStorerSearchRequestFilterFormat.BEFORE_DATE_PARAM + "\" value=\"" + beforeDate + "\" placeholder=\"MM/dd/YYYY format\" size=\"25\" maxlength=\"10\" tabindex=\""+(tabIdx++)+"\">");

        table[4][0].append("&nbsp;");
        table[4][1].append("&nbsp;");
        table[4][2].append("<b><font color=\"white\">Winner</font></b>");
        table[4][3].append("<select name=\"selectWinner\" tabindex=\""+(tabIdx++)+"\">");

        String winnerSelectNames[] = new String[] { "Either player", "Player 1", "Player 2" };
        for (int i = 0; i < winnerSelectNames.length; i++) {
            table[4][3].append("<option value=\"" + i + "\"");
            if (filterData.getWinner() == i) {
                table[4][3].append(" selected");
            }
            table[4][3].append(">" + winnerSelectNames[i] + "</option>");
        }
        table[4][3].append("</select>");

        table[5][0].append("&nbsp;");
        table[5][1].append("&nbsp;");
        table[5][2].append("<label><input id=\"exclude_timeouts\" name=\"exclude_timeouts\" type=\"checkbox\" "+(filterData.isExcludeTimeOuts()?"checked":"")+" tabindex=\""+(tabIdx++)+"\"/> <font color=\"white\">exclude timeouts</font></label>");
        table[5][3].append("<label><input id=\"p1_or_p2\" name=\"p1_or_p2\" type=\"checkbox\" "+(filterData.isP1OrP2()?"checked":"")+" tabindex=\""+(tabIdx++)+"\"/> <font color=\"white\">match player 1 or player 2</font></label>");
%>

<tr><td colspan="2">
<script language="javascript" src="<%= request.getContextPath() %>/gameServer/js/sites.js"></script>
<script language="javascript" src="<%= request.getContextPath() %>/gameServer/js/sitesData.js"></script>
<form name="filter_options_data">
<table align="left" border="1" cellspacing="0" cellpadding="2" bordercolor="#000000" width="100%">
<tr bgcolor="<%= bgColor1 %>">
<td colspan="<%= table.length %>"><font color="white"><b>Filter options</b></font></td>
</tr>
<%      for (int i = 0; i < table.length; i++) { %>
<tr bgcolor="<%= bgColor1 %>">
<%          for (int j = 0; j < table[i].length; j++) { %>
<td><%= table[i][j] %></td>
<%          } %>
</tr>
<%      } %>
</table>
</form>
</td></tr>

<tr><td colspan="2">
<form name="loadGameForm" action="<%= request.getContextPath() %>/gameServer/controller/load_game" method="POST">
<input type="hidden" name="game_id" value="">
<input type="hidden" name="game_format" value="org.pente.game.PGNGameFormat">
</form>
<div id="gamesDiv" style="position:relative<%= !showGames ? ";visibility:hidden" : ""%>">
<form name="filter_data">
<input type="hidden" name="startGameNum" value="<%= filterData.getStartGameNum() %>">
<table border="1" cellpadding="1" cellspacing="0" bordercolor="black" width="100%">
<tr bgcolor="<%= bgColor2 %>">
<td colspan="9"><b>Recent Games</b></td>
</tr>
<tr bgcolor="<%= bgColor2 %>">
<td><b>Txt</b></td>
<td><b>Load</b></td>
<td><b>Player 1</b></td>
<td><b>Player 2</b></td>
<td><b>Site</b></td>
<td><b>Event</b></td>
<td><b>Round</b></td>
<td><b>Section</b></td>
<td><b>Date</b></td>
</tr>

<%      SimpleGameStorerSearchRequestFormat searchFormat =
            new SimpleGameStorerSearchRequestFormat();
        Vector games = data.getGames();
        for (int i = 0; i < games.size(); i++) {
            GameData gameData = (GameData) games.elementAt(i);
            StringBuffer movesBuf = new StringBuffer();
            movesBuf = searchFormat.formatMoves(gameData, movesBuf, false, false); %>
<tr bgcolor="<%= bgColor2 %>">
<td><a href="/gameServer/pgn.jsp?g=<%= gameData.getGameID() %>">Txt</a></td>
<td><a href="javascript:loadGame('<%= movesBuf.toString() %>');">Load</a></td>
<%
String p1Link = gameData.getSiteURL();
String p2Link = gameData.getSiteURL();
if (gameData.getShortSite().equals("Pente.org")) {
     p1Link = request.getContextPath() + "/gameServer/profile?viewName=" +
              gameData.getPlayer1Data().getUserIDName();
     p2Link = request.getContextPath() + "/gameServer/profile?viewName=" +
              gameData.getPlayer2Data().getUserIDName();
}  else if (gameData.getShortSite().equals("IYT")) {
     p1Link = "http://www.itsyourturn.com/iyt.dll?userprofile?userid=" +
              gameData.getPlayer1Data().getUserID();
     p2Link = "http://www.itsyourturn.com/iyt.dll?userprofile?userid=" +
              gameData.getPlayer2Data().getUserID();
} else if (gameData.getShortSite().equals("BK")) {
     p1Link = "http://brainking.com/game/PlayerList?submit=Search&a=ap&utf=" +
              gameData.getPlayer1Data().getUserIDName();
     p2Link = "http://brainking.com/game/PlayerList?submit=Search&a=ap&utf=" +
              gameData.getPlayer2Data().getUserIDName();
} %>
<td><a href="<%= p1Link %>"><font color="<%= gameData.getWinner() == GameData.PLAYER1 ? "#8b0000" : "black" %>"><%= gameData.getPlayer1Data().getUserIDName() %></font></a></td>
<td><a href="<%= p2Link %>"><font color="<%= gameData.getWinner() == GameData.PLAYER2 ? "#8b0000" : "black" %>"><%= gameData.getPlayer2Data().getUserIDName() %></font></a></td>
<td><a href="<%= gameData.getSiteURL() %>"><%= gameData.getShortSite() %></a></td>
<td><a href="/gameServer/viewLiveGame?mobile&g=<%= gameData.getGameID() %>"><%= gameData.getEvent() %></a></font></td>
<td><%= gameData.getRound() == null ? "&nbsp;-" : gameData.getRound() %></td>
<td><%= gameData.getSection() == null ? "&nbsp;-" : gameData.getSection() %></td>
<td><%= dateFormat.format(gameData.getDate()) %></td>
</tr>
<%      } %>

<%
        int actualEnd = filterData.getEndGameNum();
        boolean showNextLink = true;
        if (actualEnd > filterData.getTotalGameNum()) {
            actualEnd = filterData.getTotalGameNum();
            showNextLink = false;
        }
        String viewingCount = numberFormat.format((long) filterData.getStartGameNum() + 1)
                              + "-" + numberFormat.format((long) actualEnd) + " of " +
                              numberFormat.format((long) filterData.getTotalGameNum()) + " matched games";
%>

<tr bgcolor="<%= bgColor2 %>">
<td colspan="9">
<table width="100%">
<tr bgcolor="<%= bgColor2 %>"><td width="25%">
<%      if (filterData.getStartGameNum() > 0) { %>
<a href="javascript:prevGames();">&lt;&lt; Prev Games</a>
<%      } else { %>&nbsp;<% } %>
</td>
<td width="50%" align="center">
<%= viewingCount %></td>
<td width="25%" align="right">
<%      if (showNextLink) { %>
<a href="javascript:nextGames();">Next Games &gt;&gt;</a>
<%      } else { %>&nbsp;<% } %>
</td>
</tr></table>
</td></tr>
<tr bgcolor="<%= bgColor2 %>">
<td colspan="9"><table border="0" cellpadding="0" cellspacing="0" bordercolor="black" width="100%">

<%      boolean showNextDlLink = true;
        boolean showPrevDlLink = false;
        int totalNumDownloads = ((filterData.getTotalGameNum() - 1) / 100) + 1;
        if (filterData.getTotalGameNum() == 0) {
            totalNumDownloads = 0;
        }

        int startNumDownloads = ((SimpleHtmlGameStorerSearchRequestData)
            data.getGameStorerSearchRequestData()).getStartZippedPartNum();
        if (startNumDownloads > totalNumDownloads) {
            startNumDownloads = 1;
        }
        if (startNumDownloads != 1) {
            showPrevDlLink = true;
        }
        int endNumDownloads = startNumDownloads + 10;
        if (endNumDownloads> totalNumDownloads) {
            endNumDownloads = totalNumDownloads + 1;
            showNextDlLink = false;
        }
        int shownDownloads = endNumDownloads - startNumDownloads;
        int prevStartNumDownloads = startNumDownloads - 10;
        if (prevStartNumDownloads < 1) {
            prevStartNumDownloads = 1;
        }
%>

<tr>
<td colspan="13"><b>Download Zipped Games (Grouped in 100's)</b>
</td>
</tr>
<tr>
<td width="16%">Part</td>
<td width="7%">
<%= (showPrevDlLink) ? "<a href=\"javascript:changeDownloads('" + prevStartNumDownloads + "');\">&lt;&lt;</a>" : "&nbsp;" %>
</td>
<%     for (int i = startNumDownloads; i < endNumDownloads; i++) { %>
<td width="7%"><a href="javascript:downloadGames('<%= i %>');"><%= i %></a></td>
<%     } %>
<td width="7%">
<%= (showNextDlLink) ? "<a href=\"javascript:changeDownloads('" + endNumDownloads + "');\">&gt;&gt;</a>" : "&nbsp;" %>
</td>
<%      if (shownDownloads < 10) {
            int width = (10 - shownDownloads) * 7; %>
<td colspan="<%= (10 - shownDownloads) %>" width="<%= width %>%">&nbsp;</td>
<%      } %>

</tr></table></td></tr></table>
</form>
</div>

    <% } else { %>

    <br>
    <br>
This feature is currently available to <a href="../subscriptions">subscribers</a> only.
    <br>
    <br>

    <% } %>

</td></tr>

</table>

<%@ include file="end.jsp" %>
