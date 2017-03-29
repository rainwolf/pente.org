<%@ page import="org.pente.game.*, org.pente.gameServer.tourney.*, org.pente.turnBased.*"
    errorPage="../../five00.jsp" %>

<%! private static final NumberFormat percentNF =
		NumberFormat.getPercentInstance(); %>

<%
String eidStr = request.getParameter("eid");
int eid = Integer.parseInt(eidStr);

Resources resources = (Resources) application.getAttribute(
    Resources.class.getName());
TourneyStorer tourneyStorer = resources.getTourneyStorer();
TBGameStorer tbStorer = resources.getTbGameStorer();
Tourney tourney = tourneyStorer.getTourney(eid);

String roundStr = request.getParameter("round");
int r = Integer.parseInt(roundStr);
TourneyRound round = (TourneyRound) tourney.getRounds().get(r - 1);
String game = GridStateFactory.getGameName(tourney.getGame());
System.out.println("game="+game);
String currentPage = "Tournament";
String title = tourney.getName() + " - Round " + r;
pageContext.setAttribute("title", title);
%>

<%@ include file="../begin.jsp" %>

<table border="0" cellpadding="0" cellspacing="0" width="100%">

  <tr> 
    <td width="100%">
      <h3><%= tourney.getName() %></h3>
      <font face="Verdana, Arial, Helvetica, sans-serif" size="2"> 
        <%@ include file="tourneyDetails.jsp" %>
      </font>
    </td>
    <td valign="top" align="right">
       <%@ include file="/gameServer/rightAd.jsp" %>
    </td>
  </tr>
  <tr>
    <td colspan="2">
      <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
            <% for (Iterator it = tourney.getRounds().iterator(); it.hasNext();) {
                   TourneyRound otherRound = (TourneyRound) it.next(); 
                   if (otherRound.getRound() != round.getRound()) { %>
                     <a href="statusRound.jsp?eid=<%= eidStr %>&round=<%= otherRound.getRound() %>">
                         Round <%= otherRound.getRound() %></a> (<%= otherRound.getNumSections() %> Sections, <%= otherRound.getNumPlayers() %> Players)<br>
              <%   }
                   else { %>
                     <font size="4">Round <%= round.getRound() %>
                     (<%= round.getNumSections() %> Sections, <%= round.getNumPlayers() %> Players)</font>
                     <br>
	               <% int num = round.getNumCompleteMatches();
	                  int tot = round.getNumTotalMatches();
	                  double numD = (double) num;
	                  double totD = (double) tot;
	                  double percent = numD / totD; %>
	                 <b><%= num %></b> / 
	                 <b><%= tot %></b> matches complete 
	                 <b><%= percentNF.format(percent) %></b><br>
              <%   }
               } 

               if (tourney.getFormat() instanceof RoundRobinFormat) { %>
               <%@ include file="statusRoundRobin.jsp" %>
            <% } else if (tourney.getFormat() instanceof DoubleEliminationFormat) { %>
               <%@ include file="statusDoubleElim.jsp" %>
            <% } else if (tourney.getFormat() instanceof SingleEliminationFormat) { %>
               <%@ include file="statusSingleElim.jsp" %>
            <% } else if (tourney.getFormat() instanceof SwissFormat) { %>
               <%@ include file="statusSwiss.jsp" %>
            <% } %>
            
      </font>
    </td>
  </tr>
  <tr><td>&nbsp;</td></tr>

</table>

<%@ include file="../end.jsp" %>
