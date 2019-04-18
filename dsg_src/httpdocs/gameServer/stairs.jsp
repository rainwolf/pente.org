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
DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");

if (gameStr != null) {
	game = Integer.parseInt(gameStr);
}

	Resources resources = (Resources) application.getAttribute(Resources.class.getName());
	CacheKOTHStorer kothStorer = resources.getKOTHStorer();
	Hill hill = kothStorer.getHill(game);
	long myPid = dsgPlayerData.getPlayerID();
	boolean iAmMember = false;
	if (hill != null) {
		iAmMember = hill.hasPlayer(myPid);
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

<table>
	<tr>
		<td valign="top">
			
<h2> King of the Hill (beta)</h2>

Pente.org now has a fully automated King of the Hill. The format is similar to ladders or stairs that may be familiar from other sites. 
Each game has a hill and each hill has steps. Everyone starts out at the bottom step, you can advance one step higher by winning a rated set, 
if you lose the set and are not already on the bottom step, then you drop a step. A draw results in no change. Emtpy steps are removed as 
they appear, if there are only 2 players on 2 steps, and the higher placed player keeps beating the lower placed player, nothing changes.
<br>
<br>
A few more rules
<ul>
	<li>The player on the top step is king of the hill and gets a <img src="/gameServer/images/kothcrown.gif"> if they occupy the top step alone, the more hills you conquer, the darker your crown becomes: 
	<% for ( int i = 1; i < 40 ; i++ ) { %>
<img src="/gameServer/images/kothcrown<%=i%>.png">		
	<%}%> </li>
	<li>non-subscribers who don't play any King of the Hill games for 62 days are removed from the hill, subscribers have 100 days leeway, </li>
	<li>non-subscribers can only participate in one hill, <a href="https://pente.org/gameServer/subscriptions">subscribers</a> can participate in all hills,</li>
	<li>live games must be played in the King of the Hill room and must be rated in order to count, games between players who are more than 2 steps apart are not considered.</li>
</ul>
<br>
This is new code so there may still be bugs here and there, that's why this is a beta feature.

<br>
<br>
Turn-based King of the Hill have a few more rules to consider:
<ul>
	<li>non-subscribers can only play 2 sets at any given time, this includes active sets, sent invitations, and invitations you haven't responded to yet, </li>
	<li>non-subscribers who don't play any King of the Hill games for 31 days are removed from the hill, for subscribers, this limit is set at 100 days, </li>
	<li><a href="https://pente.org/gameServer/subscriptions">subscribers</a> can play unlimited KotH games, when <a href="https://pente.org/gameServer/subscriptions">subscribers</a> have 5 or more ongoing KotH sets, including waiting invitations, they cannot be challenged anymore, but can still send out challenges,</li>
	<li>if your opponent leaves the hill before the set is over, the result still counts towards your hill position,</li>
	<li>joining or leaving a hill, and challenging other players has to be done from this page or your mobile device,</li>
	<li>declining an invitation affects your position if the timeout is 5 days or more, and, you are king of the hill or your rating is higher than that of the challenger.</li>
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

<script type="text/javascript">
function submitMainPlayForm()
{
  document.mainPlayForm.submit();
}
function submitJoinLeaveForm()
{
  document.joinLeaveForm.submit();
}
function submitNewGameForm()
{
  document.new_game_form.submit();
}
function submitNewGameFormIndividual(individual) {
  document.getElementById('new_game_form_'+individual).submit();
	// alert('new_game_form_'+individual+'\"');
}
</script>

<%
if (game > 0) {
%>
<table width="400">
	<tr align="top">
<!-- 	<td>
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

        <a class="boldbuttons" href="javascript: submitMainPlayForm()" style="margin-right:6px; margin-left: 6px"><span>Load Hill</span></a>
</form>
</td>
 -->
 <%
	if (game > 50) {
%>
<td>
<%
		if (hill != null && iAmMember) {
			%>
			<form name="joinLeaveForm" method="post" action="/gameServer/koth" style="margin:0;padding:0;">
			<input type="hidden" name="leave">
			<input type="hidden" name="game" value="<%=game%>">

			<!-- <input type="submit" value="leave hill"> -->
        <a class="boldbuttons" href="javascript: submitJoinLeaveForm()" style="margin-right:6px; margin-left: 6px"><span>Leave this hill</span></a>
			</form>
			<%
		} else {
			%>
			<form name="joinLeaveForm" method="post" action="/gameServer/koth" style="margin:0;padding:0;">
			<input type="hidden" name="join">
			<input type="hidden" name="game" value="<%=game%>">
			<!-- <input type="submit" value="join hill"> -->
        <a class="boldbuttons" href="javascript: submitJoinLeaveForm()" style="margin-right:6px; margin-left: 6px"><span>Join this hill</span></a>
			</form>
			<%
			if (!dsgPlayerData.hasPlayerDonated()) { %>
				<u><b>note:</b></u> you will be automatically removed from any other hills you are in when you join.
			<% }
	}
%>

</td>
<%
	}	
%>

<!-- </tr> -->
<%
if (hill != null && iAmMember && game > 50 && (dsgPlayerData.hasPlayerDonated() || kothStorer.canPlayerBeChallenged(game, myPid))) {
%>
	<!-- <tr align="top"> -->
	<td>
	<form name="new_game_form" method="post" action="<%= request.getContextPath() %>/gameServer/tb/newKotH.jsp">
	<input type="hidden" name="invitee" value="">
	<input type="hidden" name="game" value="<%=game%>">
	<!-- <input type="submit" name="send open hill invitation" value="send open hill invitation"> -->
        <a class="boldbuttons" href="javascript: submitNewGameForm()" style="margin-right:6px; margin-left: 6px"><span>Send Open Hill Invitation</span></a>
	</form>
	</td>	

<%	
}
%>

	</tr>
</table>

<br>
<br>

<table border="1" width="450">
	<tr>
	<% if (iAmMember) { %>
		<td colspan="<%=(game > 50)?4:3%>" align="center" bgcolor="#ff8105">
			<font color="white"><h1><%=(game > 50?"Turn-based ":"") + GridStateFactory.getGameName(game) + " (" + (hill != null?hill.getNumPlayers():0)  + ")"%></h1></font>
		
	<% } else { %>
		<td colspan="3" align="center">
			<h1><%=(game > 50?"Turn-based ":"") + GridStateFactory.getGameName(game) + " (" + (hill != null?hill.getNumPlayers():0)  + ")"%></h1>
	<% } %>
		</td>
	</tr>

<%
	if (hill != null && hill.getSteps().size() > 0) {
		boolean canIchallenge = true;
		if (game > 50) {
			canIchallenge = iAmMember;
            if (!dsgPlayerData.hasPlayerDonated()) {
                canIchallenge = canIchallenge && kothStorer.canPlayerBeChallenged(game, myPid);
            }
		} 
		int myStep = -1;
		if (canIchallenge) {
			myStep = hill.myStep(myPid);
		}
		List<Step> steps = hill.getSteps();
		for (int i = steps.size() - 1; i >= 0; i-- ) {
//			steps.get(i).getPlayers().sort((o1, o2) -> o1.getLastGame().compareTo(o2.getLastGame()));
			Collections.sort(steps.get(i).getPlayers(), new Comparator<Player>() {
			    @Override
			    public int compare(Player o1, Player o2) {
			        return o2.getLastGame().compareTo(o1.getLastGame());
			    }
			});
		%>
	<tr>
		<td bgcolor="<%=steps.get(i).hasPlayer(myPid)?"#ff8105":"#2C862F"%>" colspan="<%=(iAmMember && game > 50)?4:3%>"  align="center"><font color="white"><b><%=(i==steps.size()-1)?"top of the hill":"Step " + (i+1)%></b></font></td>
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
            <%=(myPid == pid)?"</h2>":""%>
			</td>
            <%
            if (game > 50 && iAmMember) {
            %>
            	<td align="center">
            	<%
	            if (game > 50 && canIchallenge && myPid != pid && (myStep - i)*(myStep - i)<5 && canChallengeThem) {
	            	%>
	            	   <form id="new_game_form_<%=d.getName()%>" name="new_game_form_<%=d.getName()%>" method="post" action="<%= request.getContextPath() %>/gameServer/tb/newKotH.jsp">
	            	   <input type="hidden" name="invitee" value="<%=d.getName()%>">
	            	   <input type="hidden" name="game" value="<%=game%>">
	            	   <!-- <input type="submit" name="challenge" value="challenge"> -->
	         			</form>
      <a class="boldbuttons" href="javascript:submitNewGameFormIndividual('<%=d.getName()%>');"><span>challenge</span></a>
	            	<%
	            }
            	%>
     			</td>
 			<%	
            }
            %>
            <td align="center">
            <%=(myPid == pid)?"<h2>":""%>
            <%=dateFormat.format(player.getLastGame())%>
            <%=(myPid == pid)?"</h2>":""%>
			</td>
	</tr>

				<%
			}
		}
	%>

<% 
	} else { 
	}
%>

</table>

<%

} else {
%>
    <h1>Hall of Fame</h1>
</center>
    <ul>
        <li>On Januari 29th, 2018, 
        <% if (true) { DSGPlayerData d = dsgPlayerStorer.loadPlayer("dmitriking"); %> <%@ include file="playerLink.jspf" %> <%}%>
         won all 27 KotH crowns. </li>
    </ul>
    
    <br>
    <br>
    
    <table border="1" cellpadding="2" cellspacing="0" bordercolor="black">
        <tr>
            <td><a href="profile?viewName=dmitriking">dmitriking</a> <img src="/gameServer/images/kothcrown27.png"></td>
        </tr>
        <tr>
            <td><img src="/gameServer/avatar?name=dmitriking" style="width:125px;height:125px;"></td>
        </tr>
    </table>
    <%

    }
%>

</center>      

		</td>
		<td width="29%" valign="top">
		<br>
		<br>
		<table align="right">
		<tr>
		<td align="center">
		<h1>Hills</h1>
		</td>
		</tr>
      <% 
      int color = 0;
      for (int gameInt: CacheKOTHStorer.tbGames) {
			hill = kothStorer.getHill(gameInt);
			if (hill != null) { 
			color += 1;
			%>
			<tr <%=(color%2 == 0)?"bgcolor=\"#deecde\"":""%>>
            <td align="right">
            <a href="/gameServer/stairs.jsp?game=<%=gameInt%>"><b><h2><%="TB-" + GridStateFactory.getGameName(gameInt)%> (<%=hill.getNumPlayers()%>)</h2></b></a>
            <%
            long kingPid = hill.getKing();
            if (kingPid != 0) {
				DSGPlayerData d = dsgPlayerStorer.loadPlayer(kingPid);
//				DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(CacheKOTHStorer.tbGames[i]);
				%>ruled by 
			<%@ include file="playerLink.jspf" %>&nbsp;
			<br>
			<br>
				<%
            }
            %>
            </td>
            </tr>
      <% }} %>
      <% for (int gameInt: CacheKOTHStorer.liveGames) {
      		if (gameInt%2 == 0) { continue; }
			hill = kothStorer.getHill(gameInt);
          if (hill != null && hill.getMembers().size()>0) {
              color += 1;
			%>
			<tr <%=(color%2 == 0)?"bgcolor=\"#deecde\"":""%>>
            <td align="right">
            <a href="/gameServer/stairs.jsp?game=<%=gameInt%>"><b><h2><%=GridStateFactory.getGameName(gameInt)%> (<%=hill.getNumPlayers()%>)</h2></b></a>
            <%
            long kingPid = hill.getKing();
            if (kingPid != 0) {
				DSGPlayerData d = dsgPlayerStorer.loadPlayer(kingPid);
//				DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(CacheKOTHStorer.liveGames[i]);
				%>ruled by 
			<%@ include file="playerLink.jspf" %>&nbsp;
			<br>
			<br>
				<%
            }
            %>
            </td>
            </tr>
      <% }} %>
      <% for (int gameInt: CacheKOTHStorer.liveGames) {
      		if (gameInt%2 == 1) { continue; }
			hill = kothStorer.getHill(gameInt);
			if (hill != null && hill.getMembers().size()>0) { 
			color += 1;
			%>
			<tr <%=(color%2 == 0)?"bgcolor=\"#deecde\"":""%>>
            <td align="right">
            <a href="/gameServer/stairs.jsp?game=<%=gameInt%>"><b><h2><%=GridStateFactory.getGameName(gameInt)%> (<%=hill.getNumPlayers()%>)</h2></b></a>
            <%
            long kingPid = hill.getKing();
            if (kingPid != 0) {
				DSGPlayerData d = dsgPlayerStorer.loadPlayer(kingPid);
//				DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(CacheKOTHStorer.liveGames[i]);
				%>ruled by 
			<%@ include file="playerLink.jspf" %>&nbsp;
			<br>
			<br>
				<%
            }
            %>
            </td>
            </tr>
      <% }} %>
		</table>



		</td>
	</tr>
</table>



<br>
<br>
	










<%@ include file="end.jsp" %>
