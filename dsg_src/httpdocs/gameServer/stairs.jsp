<%@ page import="org.pente.kingOfTheHill.*,
                org.pente.game.*" %>

<% pageContext.setAttribute("title", "King of the Hill"); %>
<%@ include file="begin.jsp" %>
<% 
String nm = (String) request.getAttribute("name");
DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(nm);
String gameStr = (String) request.getParameter("game");
if (gameStr == null) {
	gameStr = (String) request.getAttribute("game");
}
int game = 0;

if (gameStr != null) {
	game = Integer.parseInt(gameStr);
}
%>
<center>
<% if (dsgPlayerData.showAds()) { %>
    <div id = "senseReplace" style="width:728px;height:90px;" top="50%"> </div>
    <%@ include file="728x90adKotH.jsp" %>
    <br style="clear:both">
<% } %>
<% if (dsgPlayerData.showAds()) { %>
    <script type="text/javascript">
        sensePage();
    </script>
<% } %>
</center>


<h2> King of the Hill (beta)</h2>

Pente.org now has a fully automated King of the Hill. The format is similar to ladders or stairs that may be familiar from other sites. 
Each game has a hill and each hill has steps. Everyone starts out at the bottom step, you can advance one step higher by winning a rated set, 
if you lose the set and are not already on the bottom step, then you drop a step. A draw results in no change. Emtpy steps are removed as 
they appear, if there are only 2 players on 2 steps, and the higher placed player keeps beating the lower placed player, nothing changes.
<br>
<br>
A few more rules
<ul>
	<li>The player on the top step is king of the hill and gets a white crown if they occupy the top step alone,</li>
	<li>players who don't play any King of the Hill games for 31 days are removed from the hill,</li>
	<li>non-subscribers can only participate in one hill, when they play a game from another hill, they are removed from the previous hill,</li>
	<li><a href="https://pente.org/gameServer/subscriptions">subscribers</a> can participate in all hills,</li>
	<li>each speed game has their own hill,</li>
	<li>games must be played in the King of the Hill room,</li>
	<li>games must be rated in order to count,</li>
	<li>games between players who are more than 2 steps apart are not considered.</li>
</ul>
<br>
This is new code so there may still be bugs here and there, that's why this is a beta feature.

<br>
<br>
Turn-based King of the Hill have a few more rules to consider:
<ul>
	<li>non-subscribers can only join one hill and can only play 2 sets at any given time, this includes active sets, sent invitations, and invitations you haven't responded to yet, </li>
	<li><a href="https://pente.org/gameServer/subscriptions">subscribers</a> can play unlimited KotH games,</li>
	<li>if your opponent leaves the hill before the set is over, the result still counts towards your hill position,</li>
	<li>joining or leaving a hill has to be done from this page,</li>
	<li>declining an invitation does not affect your position,</li>
	<li>other players from the hill have to be challenged from this page or from your device.</li>
</ul>
The challenge button appears when you can challenge a player, and they're also marked with a green background. This button takes you to a page where you can decide on the timeout and post an invitation from there.
<br>
<br>

<center>

<% String error = (String) request.getAttribute("error");
   if (error != null) { %>

  <b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="#8b0000">
    Error: <%= error %>
  </font></b>

<%   
   }
%>


<table width="400">
	<tr>
	<td>
 <form name="mainPlayForm" method="post" action="/gameServer/stairs.jsp" style="margin:0;padding:0;">

    
      <select name="game">
      <% for (int i = 0; i < CacheKOTHStorer.tbGames.length; i++ ) {
         %>
             <option <%=(CacheKOTHStorer.tbGames[i]==game?"selected":"")%> value="<%= CacheKOTHStorer.tbGames[i] %>"><%= "Turn-based " + GridStateFactory.getGameName(CacheKOTHStorer.tbGames[i]) %></option>
      <% } %>
      <% for (int i = 0; i < CacheKOTHStorer.liveGames.length; i++ ) {
         %>
             <option <%=(CacheKOTHStorer.liveGames[i]==game?"selected":"")%> value="<%= CacheKOTHStorer.liveGames[i] %>"><%= GridStateFactory.getGameName(CacheKOTHStorer.liveGames[i]) %></option>
      <% } %>
    </select>

  <input type="submit" value="load hill">
</form>
</td>

<%
if (game > 0) {
	Resources resources = (Resources) application.getAttribute(Resources.class.getName());
	CacheKOTHStorer kothStorer = resources.getKOTHStorer();
	Hill hill = kothStorer.getHill(game);
	long myPid = dsgPlayerData.getPlayerID();
	if (game > 50) {
%>
<td>
<%
		if (hill != null && hill.hasPlayer(myPid)) {
			%>
			<form name="joinLeaveForm" method="post" action="/gameServer/koth" style="margin:0;padding:0;">
			<div class="buttonwrapper">
			<input type="hidden" name="leave">
			<input type="hidden" name="game" value="<%=game%>">

			<input type="submit" value="leave hill">
			</div>
			</form>
			<%
		} else {
			%>
			<form name="joinLeaveForm" method="post" action="/gameServer/koth" style="margin:0;padding:0;">
			<div class="buttonwrapper">
			<input type="hidden" name="join">
			<input type="hidden" name="game" value="<%=game%>">
			<input type="submit" value="join hill">
			</div>
			</form>
			<%
	}
%>
</td>
<%
	}	
%>

</tr>
</table>

<br>
<br>

<table border="1" width="400">
	<tr>
		<td colspan="3" align="center">
			<h1><%=(game > 50?"Turn-based ":"") + GridStateFactory.getGameName(game) + " (" + (hill != null?hill.getNumPlayers():0)  + ")"%></h1>
			
		</td>
	</tr>

<%
	if (hill != null && hill.getSteps().size() > 0) {
		boolean canIchallenge = true;
		if (game > 50) {
			canIchallenge = hill.hasPlayer(myPid) && kothStorer.canPlayerBeChallenged(game, myPid);
		} 
		int myStep = -1;
		if (canIchallenge) {
			myStep = hill.myStep(myPid);
		}
		List<Step> steps = hill.getSteps();
		for (int i = steps.size() - 1; i >= 0; i-- ) {
		%>
	<tr>
		<td colspan="3"  align="center"><b><%=(i==steps.size()-1)?"top of the hill":"Step " + (i+1)%></b></td>
	</tr>
		<%

			for( Player player : steps.get(i).getPlayers()) {
				long pid = player.getPid();
				DSGPlayerData d = dsgPlayerStorer.loadPlayer(pid);
				DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(game);
				boolean canChallengeThem = false;
				if (canIchallenge && myPid != pid && (myStep - i)*(myStep - i)<5) {
					if (game > 50) {
						boolean iAmIgnored = false;
					    List<DSGIgnoreData> ignoreData = dsgPlayerStorer.getIgnoreData(pid);
					    for (Iterator<DSGIgnoreData> it = ignoreData.iterator(); it.hasNext();) {
					        DSGIgnoreData id = it.next();
					        if (id.getIgnorePid() == myPid) {
					            if (id.getIgnoreInvite()) {
					                iAmIgnored = true;
					                break;
					            }   
					        }   
					    }

						canChallengeThem = !iAmIgnored && kothStorer.canPlayerBeChallenged(game, pid);
					} else {
						canChallengeThem = true;
					}
				}

				%>

	<tr <%=(canIchallenge && myPid != pid && (myStep - i)*(myStep - i)<5 && canChallengeThem)?"bgcolor=\"#deecde\"":""%>>
            <td align="center">
            <%=(myPid == pid)?"<h2>":""%>
			<%@ include file="playerLink.jspf" %>&nbsp;
            <%=(myPid == pid)?"<h2>":""%>
			</td>
            <td align="center">
            <%=(myPid == pid)?"<h2>":""%>
			<%@ include file="ratings.jspf" %>&nbsp;
            <%=(myPid == pid)?"</h3>":""%>
			</td>
            <%
            if (game > 50 && canIchallenge && myPid != pid && (myStep - i)*(myStep - i)<5 && canChallengeThem) {
            	%>
            	<td align="center">
            	   <form name="new_game_form" method="post" action="<%= request.getContextPath() %>/gameServer/tb/newKotH.jsp">
            	   <input type="hidden" name="invitee" value="<%=d.getName()%>">
            	   <input type="hidden" name="game" value="<%=game%>">
            	   <input type="submit" name="challenge" value="challenge">
         			</form>
         			</td>
            	<%
            }
            %>
	</tr>

				<%
			}
		}
	%>

<% 
	} else { 
%>
<% 
	}
%>

</table>

<%

}
%>

</center>      


<br>
<br>
	










<%@ include file="end.jsp" %>
