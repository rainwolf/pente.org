<%@ page import="org.pente.game.*, org.pente.gameServer.tourney.*" %>

<% pageContext.setAttribute("title", "Tournaments"); %>

<%
Resources resources = (Resources) application.getAttribute(
       Resources.class.getName());

List signup = resources.getTourneyStorer().getUpcomingTournies();
List currentT = resources.getTourneyStorer().getCurrentTournies();
List completed = resources.getTourneyStorer().getCompletedTournies();

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
               Tourney t = resources.getTourneyStorer().getTourney(d.getEventID());
               boolean live = !t.isTurnBased();
               if (t.getNumRounds() > 0) { %>
                   <b><a href="statusRound.jsp?eid=<%= t.getEventID() %>&round=<%= t.getNumRounds() %>">
                    <%= t.getName() %></a> (<%=(live?"Live":"Turn-Based")%> tournament)</b><br>
            <% } else { %>
                   <b><a href="status.jsp?eid=<%= t.getEventID() %>">
                    <%= t.getName() %></a> (<%=(live?"Live":"Turn-Based")%> tournament)</b><br>
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
               Tourney t = resources.getTourneyStorer().getTourney(d.getEventID());
                boolean live = !t.isTurnBased();%>
               <b><a href="tournamentConfirm.jsp?eid=<%= t.getEventID() %>">
                    <%= t.getName() %></a> (<%=(live?"Live":"Turn-Based")%> tournament)</b><br>
          <% }
         } %>
    </td>
  </tr>
  <tr><td>&nbsp;</td></tr>
  <tr>
     <td><b>Current Tournament Champs!</b></td>
  </tr>
  <tr><td>&nbsp;</td></tr>
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
          for (Iterator it = completed.iterator(); it.hasNext();) {
             Tourney d = (Tourney) it.next(); 
             Tourney t = resources.getTourneyStorer().getTourney(d.getEventID());%>
             <b><a href="statusRound.jsp?eid=<%= t.getEventID() %>&round=<%= t.getNumRounds() %>">
                 <%= t.getName() %></a></b><br>
      <%  }
         } 
         catch (Throwable t) 
         { 
           t.printStackTrace(); 
         } %>

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
