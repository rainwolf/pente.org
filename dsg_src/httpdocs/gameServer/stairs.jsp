<%@ page import="org.pente.kingOfTheHill.*,
                org.pente.gane.*" %>

<% pageContext.setAttribute("title", "King of the Hill"); %>
<%@ include file="begin.jsp" %>


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
	<li>subscribers can participate in all hills,</li>
	<li>each speed game has their own hill,</li>
	<li>games must be played in the King of the Hill room,</li>
	<li>games between players who are more than 2 steps apart are not considered.</li>
</ul>
<br>
This is new code so there may still be bugs here and there, that's why this is a beta feature.

<br>
<br>

<b>Note:</b> Turn-based games will be added soon but this requires (even) more work.

<br>
<br>

<center>

<% 
String nm = (String) request.getAttribute("name");
String gameStr = (String) request.getParameter("game");
int game = 0;

if (gameStr != null) {
	game = Integer.parseInt(gameStr);
}
%>

 <form name="mainPlayForm" method="post" action="" style="margin:0;padding:0;">
<div class="buttonwrapper">

    
      <select name="game">
      <% for (int i = 0; i < CacheKOTHStorer.liveGames.length; i++ ) {
         %>
             <option <%=(i+1==game?"selected":"")%> value="<%= CacheKOTHStorer.liveGames[i] %>"><%= GridStateFactory.getGameName(CacheKOTHStorer.liveGames[i]) %></option>
      <% } %>
    </select>

  <input type="submit" value="load hill">


</div>

</form>
<br>
<br>

<%
if (game > 0) {
	
%>

<table border="1" width="400">
	<tr>
		<td colspan="3">
			<h1><%=GridStateFactory.getGameName(game)%></h1>
		</td>
	</tr>

<%
	DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(nm);
	Resources resources = (Resources) application.getAttribute(Resources.class.getName());
	CacheKOTHStorer kothStorer = resources.getKOTHStorer();
	Hill hill = kothStorer.getHill(game);
	if (hill != null && hill.getSteps().size() > 0) {
		List<Step> steps = hill.getSteps();
		for (int i = steps.size() - 1; i >= 0; i-- ) {
		%>
	<tr>
		<td colspan="3"><%="Step " + (i+1)%></td>
	</tr>
		<%

			for( long pid : steps.get(i).getPlayers()) {
			DSGPlayerData d = dsgPlayerStorer.loadPlayer(pid);
			DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(game);
				%>

	<tr>
            <td><%@ include file="playerLink.jspf" %>&nbsp;</td>
            <td><%@ include file="ratings.jspf" %>&nbsp;</td>
            <td><%= d.getTotalGames() %></td>
	</tr>

				<%
			}
		}
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
