<%@ page import="org.pente.game.*, org.pente.gameServer.tourney.*" %>

<% pageContext.setAttribute("title", "Tournaments"); %>

<%
Resources resources = (Resources) application.getAttribute(
       Resources.class.getName());

List signup = resources.getTourneyStorer().getUpcomingTournies();
List currentT = resources.getTourneyStorer().getCurrentTournies();
List completed = resources.getTourneyStorer().getCompletedTournies();
TourneyStorer tourneyStorer = resources.getTourneyStorer();

List<Tourney> completedDetails = new ArrayList();
for (Object d: completed) {
  completedDetails.add(tourneyStorer.getTourney(((Tourney) d).getEventID()));
}
Collections.sort(completedDetails, new Comparator<Tourney>() {
public int compare(Tourney o1, Tourney o2) {
    return o2.getStartDate().compareTo(o1.getStartDate());
}
});         

%>
<%!
private Tourney getLastTBTourney(List<Tourney> completedTournaments, int game) {
    for (Tourney t: completedTournaments) {
        if (t.getGame() == game) {
            return t;
        }
    }
    return null;  
}
%>

<% 
Tourney tbGomoku = getLastTBTourney(completedDetails, GridStateFactory.TB_GOMOKU);
Tourney tbKeryo = getLastTBTourney(completedDetails, GridStateFactory.TB_KERYO);
Tourney tbBoat = getLastTBTourney(completedDetails, GridStateFactory.TB_BOAT_PENTE);
Tourney tbDPente = getLastTBTourney(completedDetails, GridStateFactory.TB_DPENTE);
Tourney tbConnect6 = getLastTBTourney(completedDetails, GridStateFactory.TB_CONNECT6);
Tourney tbPoof = getLastTBTourney(completedDetails, GridStateFactory.TB_POOF_PENTE);
Tourney tbDK = getLastTBTourney(completedDetails, GridStateFactory.TB_DKERYO);
Tourney tbGPente = getLastTBTourney(completedDetails, GridStateFactory.TB_GPENTE);
Tourney tbGo = getLastTBTourney(completedDetails, GridStateFactory.TB_GO);
Tourney tbGo9x9 = getLastTBTourney(completedDetails, GridStateFactory.TB_GO9);
Tourney tbGo13x13 = getLastTBTourney(completedDetails, GridStateFactory.TB_GO13);
%>

<%@ include file="../begin.jsp" %>

<table border="0" cellpadding="0" cellspacing="0" width="100%">
  <tr>
   <td>
     <h3>Pente.org Tournament Area</h3>
     Use the links below to register for or view tournaments.<br>
     <br>

     <br>
   </td>
   <td valign="top">
<%@ include file="/gameServer/rightAd.jsp" %>
   </td>
  </tr>
  <tr> 
    <td colspan="2">
      <h3>Tournaments in Progress</h3>
    </td>
  </tr>
  <tr>
    <td colspan="2"><font face="Verdana, Arial, Helvetica, sans-serif" size="2">
      <% if (currentT.isEmpty()) { %>
        There are currently no tournaments in progress.
      <% }
         else {
             for (Iterator it = currentT.iterator(); it.hasNext();) {
               Tourney d = (Tourney) it.next();
               Tourney t = tourneyStorer.getTourney(d.getEventID());
               boolean live = !t.isTurnBased();
               if (t.getNumRounds() > 0) { %>
                   <b><a href="statusRound.jsp?eid=<%= t.getEventID() %>&round=<%= t.getNumRounds() %>">
                   <% int numPlayers = tourneyStorer.getTourneyPlayerPids(d.getEventID()).size(); %>
                    <%= t.getName() %></a> (<%=(live?"Live":"Turn-Based")%> tournament</b>, round: <%=t.getNumRounds()%>, <%=numPlayers%> player<%=numPlayers==1?"":"s"%><b>)</b><br>
            <% } else { %>
                   <b><a href="status.jsp?eid=<%= t.getEventID() %>">
                   <% int numPlayers = tourneyStorer.getTourneyPlayerPids(d.getEventID()).size(); %>
                    <%= t.getName() %></a> (<%=(live?"Live":"Turn-Based")%> tournament, </b> <%=numPlayers%> player<%=numPlayers==1?"":"s"%><b>)</b><br>
            <% }
             }
         } %>
    </td>
  </tr>
  <tr><td>&nbsp;</td></tr>
  <tr> 
    <td colspan="2">
      <h3>Upcoming Tournaments</h3>
    </td>
  </tr>
  <tr>
    <td colspan="2"><font face="Verdana, Arial, Helvetica, sans-serif" size="2">
      <% if (signup.isEmpty()) { %>
        There are currently no upcoming tournaments.
      <% }
         else {
             for (Iterator it = signup.iterator(); it.hasNext();) {
               Tourney d = (Tourney) it.next();
               Tourney t = tourneyStorer.getTourney(d.getEventID());
                boolean live = !t.isTurnBased();%>
               <b><a href="tournamentConfirm.jsp?eid=<%= t.getEventID() %>">
                   <% int numPlayers = tourneyStorer.getTourneyPlayerPids(d.getEventID()).size(); %>
                    <%= t.getName() %></a> (<%=(live?"Live":"Turn-Based")%> tournament, </b> <%=numPlayers%> player<%=numPlayers==1?"":"s"%><b>)</b><br>
          <% }
         } %>
    </td>
  </tr>
  <tr><td>&nbsp;</td></tr>
  <tr>
     <td><b>Current Tournament Champs!</b></td>
  </tr>
  <tr><td>&nbsp;</td></tr>
  <tr>
  <td>
     <table border="1" cellpadding="2" cellspacing="0" bordercolor="black">
       <tr bgcolor="<%= bgColor1 %>">
        <td><font color="white">TB Pente</font></td>
        <td><font color="white">TB Pente (below 1800)</font></td>
        <td><font color="white">TB Pente (1800 and over)</font></td>
       </tr>
      </tr>
      <tr>
        <td>Remember, remember, the 5th of November</td>
        <td>Summer Renaissance (2017)</td>
        <td>Pente Masters (2017)</td>
      </tr>
      <tr>
        <td><a href="../profile?viewName=brf">brf</a> <img src="/gameServer/images/bcrown.gif"></td>
        <td><a href="../profile?viewName=wilmar">wilmar</a> <img src="/gameServer/images/bcrown.gif"></td>
        <td><a href="../profile?viewName=lupulo">lupulo</a> <img src="/gameServer/images/scrown.gif"></td>
      </tr>
      <tr>
        <td><img src="/gameServer/avatar?name=brf" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=wilmar" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=lupulo" style="width:125px;height:125px;"></td>
      </tr>
     </table>
     </td>
  </tr>
  <tr>
    <td>
      &nbsp
    </td>
  </tr>
    <tr>
        <td>
            <table border="1" cellpadding="2" cellspacing="0" bordercolor="black">
                <tr bgcolor="<%= bgColor1 %>">
                    <td><font color="white">TB Gomoku</font></td>
                    <td><font color="white">TB Boat-Pente</font></td>
                    <td><font color="white">TB Connect6</font></td>
                    <td><font color="white">TB D-Pente</font></td>
                </tr>
                </tr>
                <tr>
                    <td><a href="statusRound.jsp?eid=<%= tbGomoku.getEventID() %>&round=<%= tbGomoku.getNumRounds() %>"><%=tbGomoku.getName()%></a></td>
                    <td><a href="statusRound.jsp?eid=<%= tbBoat.getEventID() %>&round=<%= tbBoat.getNumRounds() %>"><%=tbBoat.getName()%></a></td>
                    <td><a href="statusRound.jsp?eid=<%= tbConnect6.getEventID() %>&round=<%= tbConnect6.getNumRounds() %>"><%=tbConnect6.getName()%></a></td>
                    <td><a href="statusRound.jsp?eid=<%= tbDPente.getEventID() %>&round=<%= tbDPente.getNumRounds() %>"><%=tbDPente.getName()%></a></td>
                </tr>
                <tr>
                    <td><a href="../profile?viewName=<%=tbGomoku.getWinner()%>"><%=tbGomoku.getWinner()%></a> <img src="/gameServer/images/bcrown.gif"></td>
                    <td><a href="../profile?viewName=<%=tbBoat.getWinner()%>"><%=tbBoat.getWinner()%></a> <img src="/gameServer/images/bcrown.gif"></td>
                    <td><a href="../profile?viewName=<%=tbConnect6.getWinner()%>"><%=tbConnect6.getWinner()%></a> <img src="/gameServer/images/bcrown.gif"></td>
                    <td><a href="../profile?viewName=<%=tbDPente.getWinner()%>"><%=tbDPente.getWinner()%></a> <img src="/gameServer/images/bcrown.gif"></td>
                </tr>
                <tr>
                    <td><img src="/gameServer/avatar?name=<%=tbGomoku.getWinner()%>" style="width:125px;height:125px;"></td>
                    <td><img src="/gameServer/avatar?name=<%=tbBoat.getWinner()%>" style="width:125px;height:125px;"></td>
                    <td><img src="/gameServer/avatar?name=<%=tbConnect6.getWinner()%>" style="width:125px;height:125px;"></td>
                    <td><img src="/gameServer/avatar?name=<%=tbDPente.getWinner()%>" style="width:125px;height:125px;"></td>
                </tr>
                <tr bgcolor="<%= bgColor1 %>">
                    <td><font color="white">TB Poof-Pente</font></td>
                    <td><font color="white">TB Keryo-Pente</font></td>
                    <td><font color="white">TB DK-Pente</font></td>
                    <td><font color="white">TB G-Pente</font></td>
                </tr>
                </tr>
                <tr>
                    <td><a href="statusRound.jsp?eid=<%= tbPoof.getEventID() %>&round=<%= tbPoof.getNumRounds() %>"><%=tbPoof.getName()%></a></td>
                    <td><a href="statusRound.jsp?eid=<%= tbKeryo.getEventID() %>&round=<%= tbKeryo.getNumRounds() %>"><%=tbKeryo.getName()%></a></td>
                    <td><a href="statusRound.jsp?eid=<%= tbDK.getEventID() %>&round=<%= tbDK.getNumRounds() %>"><%=tbDK.getName()%></a></td>
                    <td><a href="statusRound.jsp?eid=<%= tbGPente.getEventID() %>&round=<%= tbGPente.getNumRounds() %>"><%=tbGPente.getName()%></a></td>
                </tr>
                <tr>
                    <td><a href="../profile?viewName=<%=tbPoof.getWinner()%>"><%=tbPoof.getWinner()%></a> <img src="/gameServer/images/bcrown.gif"></td>
                    <td><a href="../profile?viewName=<%=tbKeryo.getWinner()%>"><%=tbKeryo.getWinner()%></a> <img src="/gameServer/images/bcrown.gif"></td>
                    <td><a href="../profile?viewName=<%=tbDK.getWinner()%>"><%=tbDK.getWinner()%></a> <img src="/gameServer/images/bcrown.gif"></td>
                    <td><a href="../profile?viewName=<%=tbGPente.getWinner()%>"><%=tbGPente.getWinner()%></a> <img src="/gameServer/images/bcrown.gif"></td>
                </tr>
                <tr>
                    <td><img src="/gameServer/avatar?name=<%=tbPoof.getWinner()%>" style="width:125px;height:125px;"></td>
                    <td><img src="/gameServer/avatar?name=<%=tbKeryo.getWinner()%>" style="width:125px;height:125px;"></td>
                    <td><img src="/gameServer/avatar?name=<%=tbDK.getWinner()%>" style="width:125px;height:125px;"></td>
                    <td><img src="/gameServer/avatar?name=<%=tbGPente.getWinner()%>" style="width:125px;height:125px;"></td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td>
            &nbsp
        </td>
    </tr>
    <tr>
        <td>
            <table border="1" cellpadding="2" cellspacing="0" bordercolor="black">
                <tr bgcolor="<%= bgColor1 %>">
                    <td><font color="white">TB Go</font></td>
                    <td><font color="white">TB Go (9x9)</font></td>
                    <td><font color="white">TB Go (13x13)</font></td>
                </tr>
                <tr>
                    <td><a href="statusRound.jsp?eid=<%= tbGo.getEventID() %>&round=<%= tbGo.getNumRounds() %>"><%=tbGo.getName()%></a></td>
                    <td><a href="statusRound.jsp?eid=<%= tbGo9x9.getEventID() %>&round=<%= tbGo9x9.getNumRounds() %>"><%=tbGo9x9.getName()%></a></td>
                    <td><a href="statusRound.jsp?eid=<%= tbGo13x13.getEventID() %>&round=<%= tbGo13x13.getNumRounds() %>"><%=tbGo13x13.getName()%></a></td>
                </tr>
                <tr>
                    <td><a href="../profile?viewName=<%=tbGo.getWinner()%>"><%=tbGo.getWinner()%></a> <img src="/gameServer/images/bcrown.gif"></td>
                    <td><a href="../profile?viewName=<%=tbGo9x9.getWinner()%>"><%=tbGo9x9.getWinner()%></a> <img src="/gameServer/images/bcrown.gif"></td>
                    <td><a href="../profile?viewName=<%=tbGo13x13.getWinner()%>"><%=tbGo13x13.getWinner()%></a> <img src="/gameServer/images/bcrown.gif"></td>
                </tr>
                <tr>
                    <td><img src="/gameServer/avatar?name=<%=tbGo.getWinner()%>" style="width:125px;height:125px;"></td>
                    <td><img src="/gameServer/avatar?name=<%=tbGo9x9.getWinner()%>" style="width:125px;height:125px;"></td>
                    <td><img src="/gameServer/avatar?name=<%=tbGo13x13.getWinner()%>" style="width:125px;height:125px;"></td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td>
            &nbsp
        </td>
    </tr>
  <tr>
  <td>
     <table border="1" cellpadding="2" cellspacing="0" bordercolor="black">
       <tr bgcolor="<%= bgColor1 %>">
        <td><font color="white">Pente</font></td>
        <td><font color="white">D-Pente</font></td>
        <td><font color="white">Pente</font></td>
        <td><font color="white">Speed-Pente</font></td>
        <td><font color="white">Connect6</font></td>
       </tr>
      </tr>
      <tr>
        <td>Pente - 16th Anniversary World Champion Tournament 2015</td>
        <td>D-Pente - Fall 2005</td>
        <td>Pente - Winter 2007 Below 1700</td>
        <td>Speed 3:Hat trick?</td>
        <td>Summer 2007 C6 Open</td>
      </tr>
      <tr>
        <td><a href="../profile?viewName=spavacz">spavacz</a> <img src="/gameServer/images/crown.gif"></td>
        <td><a href="../profile?viewName=richardiii">richardiii</a> <img src="/gameServer/images/crown.gif"></td>
        <td><a href="../profile?viewName=spifster">spifster</a> <img src="/gameServer/images/scrown.gif"></td>
        <td><a href="../profile?viewName=karlw">karlw</a> <img src="/gameServer/images/crown.gif"></td>
        <td><a href="../profile?viewName=zhangying">zhangying</a> <img src="/gameServer/images/crown.gif"></td>
      </tr>
      <tr>
        <td><img src="/gameServer/avatar?name=spavacz" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=richardiii" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=spifster" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=karlw" style="width:125px;height:125px;"></td>
        <td>&nbsp;</td>
      </tr>
     </table>
     </td>
  </tr>
  <tr><td>&nbsp;</td></tr>
  <tr> 
    <td colspan="2">
      <h3>Past Tournaments</h3>
    </td>
  </tr>
  <tr>
    <td colspan="2"><font face="Verdana, Arial, Helvetica, sans-serif" size="2">
      <% try 
         { 
         int year = 0;
          for (Iterator it = completedDetails.iterator(); it.hasNext();) {
             Tourney t = (Tourney) it.next(); 
//             Tourney t = tourneyStorer.getTourney(d.getEventID());
             Calendar cal = Calendar.getInstance();
            cal.setTime(t.getStartDate());  
            int tourneyYear = cal.get(Calendar.YEAR);
            if (year != tourneyYear) {
            year = tourneyYear;
            %>
            <h2><%=year%> </h2>
            <%
            }
            DSGPlayerData d = dsgPlayerStorer.loadPlayer(t.getWinnerPid());
            %>
             <b><a href="statusRound.jsp?eid=<%= t.getEventID() %>&round=<%= t.getNumRounds() %>">
                 <%= t.getName() %></a></b> (winner: 
                 <%@ include file="../playerLink.jspf" %>)&nbsp;
<br>
      <%  }
         } 
         catch (Throwable t) 
         { 
           t.printStackTrace(); 
         } %>

        <h2>Before 2005</h2>
      <%-- older manual tournaments --%>
      <b><a href="old/tournament5Results.jsp">Pente - Tournament 5</a></b><br>
      <b><a href="old/tournament4Results.jsp">Pente - Tournament 4</a></b><br>
      <b><a href="old/tournament3Results.jsp">Pente - Tournament 3</a></b><br>
      <b><a href="old/tournament2Results.jsp">Pente - Tournament 2</a></b><br>
      <b><a href="old/tournament1Results.jsp">Pente - Tournament 1</a></b>
    </td>
  </tr>
  
</table>
<br>

<%@ include file="../end.jsp" %>
