<% response.sendRedirect("/gameServer/index.jsp"); %>
<%--
<%@ page import="org.pente.game.*, org.pente.turnBased.*" %>


<%
Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());

String nm = (String) request.getAttribute("name");
DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(nm);

int refresh = 5;
List prefs = dsgPlayerStorer.loadPlayerPreferences(
	dsgPlayerData.getPlayerID());
for (Iterator it = prefs.iterator(); it.hasNext();) {
	DSGPlayerPreference p = (DSGPlayerPreference) it.next();
	if (p.getName().equals("refresh")) {
		refresh = ((Integer) p.getValue());
	}
}
if (refresh != 0) {
    response.setHeader("Refresh", refresh * 60 + "; URL=index.jsp");
}	
TBGameStorer tbGameStorer = resources.getTbGameStorer();
List<TBSet> currentSets = tbGameStorer.loadSets(dsgPlayerData.getPlayerID());
List<TBSet> invitesTo = new ArrayList<TBSet>();
List<TBSet> invitesFrom = new ArrayList<TBSet>();
List<TBGame> myTurn = new ArrayList<TBGame>();
List<TBGame> oppTurn = new ArrayList<TBGame>();
Utilities.organizeGames(dsgPlayerData.getPlayerID(), currentSets,
    invitesTo, invitesFrom, myTurn, oppTurn);
String title = "Play Turn-Based";
int gc = tbGameStorer.getNumGamesMyTurn(dsgPlayerData.getPlayerID());
if (gc > 0) {
    title += " (" + gc + ")";
}
%>
 
<% pageContext.setAttribute("title", title); %>
<%@ include file="../begin.jsp" %>
 

<table border="0" cellpadding="0" cellspacing="0" width="450">
 <tr>
  <td valign="top">
   <font size="4">My Turn-Based Games</font>
  </td>
  <td align="right">
      <font size="-1">
      Refresh: <%= refresh == 0 ? "No refresh" : refresh + " minutes" %> - 
      <a href="/gameServer/myprofile/prefs">Change</a>
      </font>
  </td>
 </tr>
</table>

<table border="0" cellpadding="0" cellspacing="0" width="100%">
  <tr>
   <td align="right">
    
     <form name="buttons">
       <input type="button" value="Start a new game"
                    onclick="javascript:window.location='/gameServer/tb/new.jsp';">
       <input type="button" value="View waiting games"
                    onclick="javascript:window.location='/gameServer/tb/waiting.jsp';">
       <input type="button" value="View my completed games"
                    onclick="javascript:window.location='/gameServer/profile?viewName=<%= nm %>';">
      </form>
   </td>
   <td valign="middle" align="right">
     
     <div align="left" style="position:relative;height:73px;width:170px;font-weight:bold;border:2px <%= textColor2 %> solid; background:#ffd0a7">
       <div style="position:absolute;top:3px;left:3px;width:64px;height:64px;">
        <% pageContext.setAttribute("playText", "<img src=/gameServer/images/jws.jpg border=0>"); %>
        <%@ include file="../playLink.jspf" %>
       </div>
       <% pageContext.setAttribute("playText", "Play Live!"); %>
       <div style="position:absolute;top:5px;left:70px;width:100px;height:59px;">
         <%@ include file="../playLink.jspf" %><br><%= siteStatsData.getNumCurrentPlayers() %> players playing now!
       </div>
     </div>
   </td>
   <td width="5">&nbsp;</td>
 </tr>
 <tr>
   <td colspan="2"><br>
     <% if (!invitesTo.isEmpty()) { %>

	 <table border="0"  cellspacing="0" cellpadding="0" width="100%">
	   <tr bgcolor="<%= textColor2 %>">
	     <td colspan="5">
	       <font color="white">
	         <b>Invitations received (<%= invitesTo.size() %>)
	       </font>
	     </td>
	   </tr>
	   <tr>
	     <td><b>Game</b></td>
	     <td><b>Opponent</b></td>
	     <td><b>Play as</b></td>
	     <td><b>Time/Move</b></td>
	     <td><b>Rated</b></td>
	   </tr>
     <% for (TBSet s : invitesTo) {
         String color = null;
         if (s.isTwoGameSet()) {
        	 color = "white,black (2 game set)";
         }
         else if (dsgPlayerData.getPlayerID() == s.getPlayer1Pid()) {
        	 color = "white (p1)";
         }
         else {
             color = "black (p2)";
         }
	     DSGPlayerData opp = dsgPlayerStorer.loadPlayer(s.getInviterPid());
	     DSGPlayerGameData dsgPlayerGameData = opp.getPlayerGameData(s.getGame1().getGame());
	     String oppName = "<a href=/gameServer/profile?viewName=" + opp.getName() +
	        ">" + opp.getName() + "</a>"; %>
         <tr>
           <td><a href="replyInvitation?command=load&sid=<%= s.getSetId() %>">
             <%= GridStateFactory.getGameName(s.getGame1().getGame()) %></a></td>
           <td><%= oppName %><%@ include file="../ratings.jspf" %></td>
	       <td><%= color %></td>
           <td><%= s.getGame1().getDaysPerMove() %> days</td>
           <td><%= s.getGame1().isRated() ? "Rated" : "Not Rated" %></td>
         </tr>
     <% } %>
     </table>
     <br>
     <% } %>
     
     <% if (!invitesFrom.isEmpty()) { %>
	 <table border="0"  cellspacing="0" cellpadding="0" width="100%">
	   <tr bgcolor="<%= bgColor2 %>">
	     <td colspan="5">
	       <b>Invitations sent (<%= invitesFrom.size() %>)
	     </td>
	   </tr>
	   <tr>
	     <td><b>Game</b></td>
	     <td><b>Opponent</b></td>
	     <td><b>You are</b></td>
	     <td><b>Time/Move</b></td>
	     <td><b>Rated</b></td>
	   </tr>
     <% for (TBSet s : invitesFrom) {
         String color = null;
         if (s.isTwoGameSet()) {
        	 color = "white,black (2 game set)";
         }
         else if (dsgPlayerData.getPlayerID() == s.getPlayer1Pid()) {
        	 color = "white (p1)";
         }
         else {
             color = "black (p2)";
         }
         long pid = s.getInviteePid();
         String oppName = "Anyone";
         DSGPlayerGameData dsgPlayerGameData = null;
         if (pid != 0) {
	         DSGPlayerData opp = dsgPlayerStorer.loadPlayer(pid);
	     	 dsgPlayerGameData = opp.getPlayerGameData(s.getGame1().getGame());
	         oppName = "<a href=/gameServer/profile?viewName=" + opp.getName() +
	         	">" + opp.getName() + "</a>";
	     } %>
         <tr>
           <td><a href="/gameServer/tb/cancelInvitation?command=load&sid=<%= s.getSetId() %>">
               <%= GridStateFactory.getGameName(s.getGame1().getGame()) %></a></td>
           <td><%= oppName %><% if (dsgPlayerGameData != null) { %><%@ include file="../ratings.jspf" %><% } %></td>
           <td><%= color %></td>
           <td><%= s.getGame1().getDaysPerMove() %> days</td>
           <td><%= s.getGame1().isRated() ? "Rated" : "Not Rated" %></td>
         </tr>
     <% } %>
     </table>
     
     <br>
     <% } %>

     <% if (!myTurn.isEmpty()) { %>

	 <table border="0"  cellspacing="0" cellpadding="0" width="100%">
	   <tr bgcolor="<%= textColor2 %>">
	     <td colspan="6">
	       <font color="white">
	         <b>Active Games - My Turn (<%= myTurn.size() %>)
	       </font>
	     </td>
	   </tr>
	   <tr>
	     <td><b>Game</b></td>
	     <td><b>Opponent</b></td>
	     <td><b>You are</b></td>
	     <td><b>Move</b></td>
	     <td><b>Time Left</b></td>
	     <td><b>Rated</b></td>
	   </tr>
     <% for (TBGame g : myTurn) {
         String color =  dsgPlayerData.getPlayerID() == g.getPlayer1Pid() ?
             "white (p1)" : "black (p2)";
         long oppPid = dsgPlayerData.getPlayerID() == g.getPlayer1Pid() ?
             g.getPlayer2Pid() : g.getPlayer1Pid();
	     DSGPlayerData opp = dsgPlayerStorer.loadPlayer(oppPid);
	     DSGPlayerGameData dsgPlayerGameData = opp.getPlayerGameData(g.getGame());
	     String oppName = "<a href=/gameServer/profile?viewName=" + opp.getName() +
	       ">" + opp.getName() + "</a>"; %>
	       
         <tr>
           <td><a href="game?gid=<%= g.getGid() %>&command=load">
             <%= GridStateFactory.getGameName(g.getGame()) %></a></td>
           <td><%= oppName %><% if (dsgPlayerGameData != null) { %><%@ include file="../ratings.jspf" %><% } %></td>
           <td><%= color %></td>
           <td><%= g.getNumMoves() + 1 %></td>
           <td><%= Utilities.getTimeLeft(g.getTimeoutDate().getTime()) %></td>
           <td><%= g.isRated() ? "Rated" : "Not Rated" %></td>
         </tr>
     <% } %>
     </table>

     <br>
     <% } %>
     
     <% if (!oppTurn.isEmpty()) { %>
	 <table border="0"  cellspacing="0" cellpadding="0" width="100%">
	   <tr  bgcolor="<%= bgColor1 %>">
	     <td colspan="6">
	       <font color="white">
	         <b>Active Games - Opponents Turn (<%= oppTurn.size() %>)
	       </font>
	     </td>
	   </tr>
	   <tr>
	     <td><b>Game</b></td>
	     <td><b>Opponent</b></td>
	     <td><b>You are</b></td>
	     <td><b>Move</b></td>
	     <td><b>Time Left</b></td>
	     <td><b>Rated</b></td>
	   </tr>
     <% for (TBGame g : oppTurn) {
         String color =  dsgPlayerData.getPlayerID() == g.getPlayer1Pid() ?
             "white (p1)" : "black (p2)";
         long oppPid = dsgPlayerData.getPlayerID() == g.getPlayer1Pid() ?
             g.getPlayer2Pid() : g.getPlayer1Pid();
	     DSGPlayerData opp = dsgPlayerStorer.loadPlayer(oppPid);
	     DSGPlayerGameData dsgPlayerGameData = opp.getPlayerGameData(g.getGame());
	     String oppName = "<a href=/gameServer/profile?viewName=" + opp.getName() +
	       ">" + opp.getName() + "</a>"; %>
	       
         <tr>
           <td><a href="game?gid=<%= g.getGid() %>&command=load">
             <%= GridStateFactory.getGameName(g.getGame()) %></a></td>
           <td><%= oppName %><% if (dsgPlayerGameData != null) { %><%@ include file="../ratings.jspf" %><% } %></td>
           <td><%= color %></td>
           <td><%= g.getNumMoves() + 1 %></td>
           <td><%= Utilities.getTimeLeft(g.getTimeoutDate().getTime()) %></td>
           <td><%= g.isRated() ? "Rated" : "Not Rated" %></td>
         </tr>
     <% } %>
     </table>
     
     <% } %>

    </td>
    
   <td width="5">&nbsp;</td>
  </tr>
  
</table>
<br>

<%@ include file="../end.jsp" %>
--%>
