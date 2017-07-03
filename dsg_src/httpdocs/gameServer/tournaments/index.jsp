<%@ page import="org.pente.game.*, org.pente.gameServer.tourney.*" %>

<% pageContext.setAttribute("title", "Tournaments"); %>

<%
Resources resources = (Resources) application.getAttribute(
       Resources.class.getName());

List signup = resources.getTourneyStorer().getUpcomingTournies();
List currentT = resources.getTourneyStorer().getCurrentTournies();
List completed = resources.getTourneyStorer().getCompletedTournies();
TourneyStorer tourneyStorer = resources.getTourneyStorer();

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
        <td><font color="white">TB Boat-Pente</font></td>
        <td><font color="white">TB Gomoku</font></td>
        <td><font color="white">TB Connect6</font></td>
        <td><font color="white">TB D-Pente</font></td>
        <td><font color="white">TB Poof-Pente</font></td>
        <td><font color="white">TB Keryo-Pente</font></td>
       </tr>
      </tr>
      <tr>
        <td>Remember, remember, the 5th of November (2016)</td>
        <td>Fool's Boat (2017)</td>
        <td>Fool's Gomoku (2017)</td>
        <td>Fool's Connect6 (2017)</td>
        <td>Fool's D (2017)</td>
        <td>Fool's Poof (2017)</td>
        <td>Fool's Keryo (2017)</td>
      </tr>
      <tr>
        <td><a href="../profile?viewName=brf">brf</a> <img src="/gameServer/images/bcrown.gif"></td>
        <td><a href="../profile?viewName=pete77">pete777</a> <img src="/gameServer/images/bcrown.gif"></td>
        <td><a href="../profile?viewName=myuym">myuym</a> <img src="/gameServer/images/bcrown.gif"></td>
        <td><a href="../profile?viewName=ivans73">ivans73</a> <img src="/gameServer/images/bcrown.gif"></td>
        <td><a href="../profile?viewName=ivans73">ivans73</a> <img src="/gameServer/images/bcrown.gif"></td>
        <td><a href="../profile?viewName=watsu">watsu</a> <img src="/gameServer/images/bcrown.gif"></td>
        <td><a href="../profile?viewName=ivans73">ivans73</a> <img src="/gameServer/images/bcrown.gif"></td>
      </tr>
      <tr>
        <td><img src="/gameServer/avatar?name=brf" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=pete777" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=myuym" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=ivans73" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=ivans73" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=watsu" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=ivans73" style="width:125px;height:125px;"></td>
      </tr>
     </table>
     </td>
  </tr>
  <tr>
  <td>
     <table border="1" cellpadding="2" cellspacing="0" bordercolor="black">
       <tr bgcolor="<%= bgColor1 %>">
        <td><font color="white">Pente</font></td>
        <td><font color="white">D-Pente</font></td>
        <td><font color="white">Pente</font></td>
        <td><font color="white">Pente</font></td>
        <td><font color="white">Speed-Pente</font></td>
        <td><font color="white">Keryo-Pente</font></td>
        <td><font color="white">Connect6</font></td>
       </tr>
      </tr>
      <tr>
        <td>Pente -  carson75's Pente Tournament</td>
        <td>D-Pente - Fall 2005</td>
        <td>Summer 2007 Pente Open</td>
        <td>Pente - Winter 2007 Below 1700</td>
        <td>Speed 3:Hat trick?</td>
        <td>Summer 2007 Keryo Open</td>
        <td>Summer 2007 C6 Open</td>
      </tr>
      <tr>
        <td><a href="../profile?viewName=zoeyk">zoeyk</a> <img src="/gameServer/images/crown.gif"></td>
        <td><a href="../profile?viewName=richardiii">richardiii</a> <img src="/gameServer/images/crown.gif"></td>
        <td><a href="../profile?viewName=richardiii">richardiii</a> <img src="/gameServer/images/crown.gif"></td>
        <td><a href="../profile?viewName=spifster">spifster</a> <img src="/gameServer/images/scrown.gif"></td>
        <td><a href="../profile?viewName=karlw">karlw</a> <img src="/gameServer/images/crown.gif"></td>
        <td><a href="../profile?viewName=karlw">karlw</a> <img src="/gameServer/images/crown.gif"></td>
        <td><a href="../profile?viewName=zhangying">zhangying</a> <img src="/gameServer/images/crown.gif"></td>
      </tr>
      <tr>
        <td><img src="/gameServer/avatar?name=zoeyk" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=richardiii" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=richardiii" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=spifster" style="width:125px;height:125px;"></td>
        <td><img src="/gameServer/avatar?name=karlw" style="width:125px;height:125px;"></td>
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
         List<Tourney> completedDetails = new ArrayList();
         for (Object d: completed) {
            completedDetails.add(tourneyStorer.getTourney(((Tourney) d).getEventID()));
         }
        Collections.sort(completedDetails, new Comparator<Tourney>() {
          public int compare(Tourney o1, Tourney o2) {
              return o2.getStartDate().compareTo(o1.getStartDate());
          }
        });         
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
