<%@ page import="java.io.*" %>
<%@ page import="java.util.*" %>
<%@ page import="java.text.*" %>

<%@ page import="org.pente.game.*" %>
<%@ page import="org.pente.gameServer.core.*" %>
<%@ page import="org.pente.gameServer.client.web.*" %>

<%! private static final NumberFormat statsNF =
		NumberFormat.getPercentInstance(); %>

<% StatsData statsData = (StatsData) request.getAttribute("statsData"); %>

<%
statsNF.setMaximumFractionDigits(1);
String currentPage = null;
String title = "Player Stats";
String context = request.getContextPath();
%>

<% pageContext.setAttribute("title", "Player Rankings"); %>
<%@ include file="begin.jsp" %>

<h3>Player Rankings for <%= GridStateFactory.getDisplayName(statsData.getGame()) %></h3>
<b><a href="statsMain.jsp">New Search</a></b>

<script type="text/javascript">
function sort(field) {
    document.checkForm.sortField.value = field;
    document.checkForm.submit();
}
</script>

<form name="checkForm" action="/gameServer/stats" method="POST" style="margin:0;padding:0">
<input type="hidden" name="game" value="<%= statsData.getGame() %>">
<input type="hidden" name="sortField" value="<%= statsData.getSortField() %>">
<% if (statsData.getIncludeProvisional()) { %>
<input type="hidden" name="includeProvisional" value="ON">
<% } %>
<% if (statsData.getIncludeInactive()) { %>
<input type="hidden" name="includeUnactive" value="ON">
<% } %>
<input type="hidden" name="playerType" value="<%= statsData.getPlayerType() %>">
<input type="hidden" name="length" value="25">
<input type="hidden" name="startNum" value="0">
<input type="hidden" name="command" value="playerStats">
</form>


<table width="100%" border="0" colspacing="1" colpadding="1">

<%
int columns = statsData.getPlayerType() == StatsData.BOTH ? 10 : 9;
%>

  <tr>
    <td>&nbsp;</td>

<%!
    int sortFields[] = new int[] { 4, 2, 0, 1, 7, 5, 6, 3, 8 };
    String sortFieldNames[] = new String[] { "Name", "Rating", "Wins", "Losses", "Draws", "Total", "% Wins", "Streak", "Last Game" };
%>
<%
    for (int i = 0; i < sortFields.length; i++) {
        String color = statsData.getSortField() == sortFields[i] ? textColor2 : "black";
%>
       <td>
         <font face="Verdana,Arial,Helvetica" size="2" color="<%= color %>"><b>
           <a href="javascript:sort(<%= sortFields[i] %>);" title="Sort">
           <span style="color:<%= color %>"><%= sortFieldNames[i] %></span></a>
         </b></font>
       </td>
<%
    }
   
    // print player type label if search on both humans and computers
    if (statsData.getPlayerType() == StatsData.BOTH) { %>
        <td>
          <font face="Verdana,Arial,Helvetica" size="2"><b>
            Type
          </b></font>
        </td>
<%  } %>

</tr>

<%  
    Vector searchResults = statsData.getResults();
    for (int i = 0; i < searchResults.size(); i++) {
        DSGPlayerData d = 
            (DSGPlayerData) searchResults.elementAt(i);
        DSGPlayerGameData dsgPlayerGameData = 
            (DSGPlayerGameData) d.getPlayerGameData(
            statsData.getGame()); %>

        <tr>
          <td>
            <font face="Verdana,Arial,Helvetica" size="2">
              <%= (statsData.getStartNum() + i + 1) %>
            </font>
          </td>
          <td>
            <font face="Verdana,Arial,Helvetica" size="2">
              <%@ include file="playerLink.jspf" %>
            </font>
          </td>
          <td>
            <font face="Verdana,Arial,Helvetica" size="2">
                <%@ include file="ratings.jspf" %>
            </font>
          </td>
          <td>
            <font face="Verdana,Arial,Helvetica" size="2">
              <%= nf.format(dsgPlayerGameData.getWins()) %>
            </font>
          </td>
          <td>
            <font face="Verdana,Arial,Helvetica" size="2">
              <%= nf.format(dsgPlayerGameData.getLosses()) %>
            </font>
          </td>
          <td>
            <font face="Verdana,Arial,Helvetica" size="2">
              <%= nf.format(dsgPlayerGameData.getDraws()) %>
            </font>
          </td>
          <td>
            <font face="Verdana,Arial,Helvetica" size="2">
              <%= nf.format(dsgPlayerGameData.getTotalGames()) %>
            </font>
          </td>
          <td>
            <font face="Verdana,Arial,Helvetica" size="2">
              <%= statsNF.format(dsgPlayerGameData.getPercentageWins()) %>
            </font>
          </td>
          <td>
            <font face="Verdana,Arial,Helvetica" size="2">
              <%= dsgPlayerGameData.getStreak() %>
            </font>
          </td>
          <td>
            <font face="Verdana,Arial,Helvetica" size="2">
              <%= dateFormat.format(dsgPlayerGameData.getLastGameDate()) %>
            </font>
          </td>
          
<%        if (statsData.getPlayerType() == StatsData.BOTH) { %>
          <td>
            <font face="Verdana,Arial,Helvetica" size="2">
              <%= d.isComputer() ? "Computer" : "Human" %>
            </font>
          </td>
<%        } %>

        </tr>
<%  } %>

    <tr>
      <td colspan="<%= columns %>">&nbsp;</td>
    </tr>
    <tr>
<%
    int prevStartNum = statsData.getStartNum() - 25;
    int nextStartNum = statsData.getStartNum() + 25;

    //print previous link
    out.write("<td colspan=\"4\">\n");
    if (statsData.getStartNum() > 0) {
        out.write("<form action=\"" + context + "/gameServer/stats\" method=\"post\">\n");
        out.write("<input type=\"submit\" value=\"<< Previous 25\">\n");
        out.write("<input type=\"hidden\" name=\"command\" value=\"playerStats\">\n");
        out.write("<input type=\"hidden\" name=\"game\" value=\"" + statsData.getGame() + "\">\n");
        out.write("<input type=\"hidden\" name=\"sortField\" value=\"" + statsData.getSortField() + "\">\n");
        out.write("<input type=\"hidden\" name=\"startNum\" value=\"" + prevStartNum + "\">\n");
        out.write("<input type=\"hidden\" name=\"length\" value=\"" + statsData.getLength() + "\">\n");
        if (statsData.getIncludeProvisional()) {
    	    out.write("<input type=\"hidden\" name=\"includeProvisional\" value=\"ON\">\n");
        }
        if (statsData.getIncludeInactive()) {
    	    out.write("<input type=\"hidden\" name=\"includeUnactive\" value=\"ON\">\n");
        }
        out.write("<input type=\"hidden\" name=\"playerType\" value=\"" + statsData.getPlayerType() + "\">\n");
        out.write("</form>\n");
    }
    out.write("</td>\n");

    //print next link
    out.write("<td colspan=\"4\" align=\"right\">\n");
    if (statsData.getStartNum() + statsData.getLength() < statsData.getNumResults()) {
        out.write("<form action=\"" + context + "/gameServer/stats\" method=\"post\">\n");
        out.write("<input type=\"submit\" value=\"Next 25 >>\">\n");
        out.write("<input type=\"hidden\" name=\"command\" value=\"playerStats\">\n");
        out.write("<input type=\"hidden\" name=\"game\" value=\"" + statsData.getGame() + "\">\n");
        out.write("<input type=\"hidden\" name=\"sortField\" value=\"" + statsData.getSortField() + "\">\n");
        out.write("<input type=\"hidden\" name=\"startNum\" value=\"" + nextStartNum + "\">\n");
        out.write("<input type=\"hidden\" name=\"length\" value=\"" + statsData.getLength() + "\">\n");
        if (statsData.getIncludeProvisional()) {
            out.write("<input type=\"hidden\" name=\"includeProvisional\" value=\"ON\">\n");
        }
        if (statsData.getIncludeInactive()) {
            out.write("<input type=\"hidden\" name=\"includeUnactive\" value=\"ON\">\n");
        }
        out.write("<input type=\"hidden\" name=\"playerType\" value=\"" + statsData.getPlayerType() + "\">\n");

        out.write("</form>\n");
    }
    out.write("</td>\n");
    out.write("</tr>\n");

%>

</table>

<%@ include file="end.jsp" %>