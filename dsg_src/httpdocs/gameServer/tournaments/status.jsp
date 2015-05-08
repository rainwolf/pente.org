<%@ page import="org.pente.game.*, org.pente.gameServer.tourney.*" %>

<%
String eidStr = request.getParameter("eid");
int eid = Integer.parseInt(eidStr);

Resources resources = (Resources) application.getAttribute(
    Resources.class.getName());

Tourney tourney = resources.getTourneyStorer().getTourney(eid);

String currentPage = "Tournament";
String title = tourney.getName();
pageContext.setAttribute("title", title);
%>

<%@ include file="../begin.jsp" %>

<table border="0" cellpadding="0" cellspacing="0" width="480">

  <tr> 
    <td>
      <h3><%= tourney.getName() %></h3>
    </td>
  </tr>
  <tr>
    <td>
      <font face="Verdana, Arial, Helvetica, sans-serif" size="2"> 
        <%@ include file="tourneyDetails.jsp" %>
        <br>

            View matchups and results, select a round.<br>
         <% for (Iterator it = tourney.getRounds().iterator(); it.hasNext();) {
               TourneyRound round = (TourneyRound) it.next(); %>
         
               <a href="statusRound.jsp?eid=<%= eidStr %>&round=<%= round.getRound() %>">
                   Round <%= round.getRound() %></a> - (<%= round.getNumSections() %> Sections, <%= round.getNumPlayers() %> Players)<br>
         <% } %>
            
      </font>
    </td>
  </tr>
  <tr><td>&nbsp;</td></tr>

</table>

<%@ include file="../end.jsp" %>
