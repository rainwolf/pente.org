<% 
String name = (String) request.getAttribute("name");
%>

<html>
<head><title>Pente.org Admin</title></head>

<body>

<h3>Pente.org Admin</h3>

<% if (name.equals("rainwolf") || name.equals("zachburau")) { %>

<h2>Player Management</h2>
<a href="setKOTHcrown.jsp">Assign/change KotH crown</a><br>
<a href="addDonor.jsp">Add Donor</a><br>
<a href="refreshPlayer.jsp">Refresh Player Cache</a><br>
<a href="deactivate.jsp">Deactivate Player</a><br>
<b>Check for Cheaters</b><br>
&nbsp;&nbsp;<a href="checkLogs.jsp">Check logs</a><br>
&nbsp;&nbsp;<a href="checkIPs.jsp">Check IPs</a><br>
<br>

<h2>Game Management</h2>
<a href="modifyServers.jsp">Modify Game Servers</a><br>
<a href="tb">Manage Turn-based Game Cache</a><br>
<br>

<h2>Tournament Management</h2>
<a href="manageTourneys.jsp">Manage Tournament</a><br>
<a href="newTourney.jsp">Create Tournament</a><br>
<a href="createTourneyForum.jsp">Create Tournament Forum</a><br>
<a href="flushTourneyCache.jsp">Flush Tourney Cache</a><br>
<br>

<h2>Info</h2>
<a href="threads.jsp">View Tomcat Threads</a><br>
<a href="http://pente.org/manager/status">Tomcat manager</a><br>
<a href="http://stats.pente.org?config=pente.org">Web server stats</a><br>
<a href="http://www.pente.org/gameServer/forums/forum.jspa?forumID=10&start=0">Admin Forum</a><br>
<a href="http://my.sagonet.com">SAGO Server Managment</a><br>
<a href="http://www.winning-moves.com/affiliates/login.htm">Winning Moves</a><br>
<a href="https://www.google.com/adsense/?hl=en_US">Google Adsense</a><br>
<a href="http://www.cafepress.com/cp/members/">Cafepress</a><br>
<% } else { %>

<h2>Player Management</h2>
<a href="setKOTHcrown.jsp">Assign/change KotH crown</a><br>
<a href="addDonor.jsp">Add Donor</a><br>
<a href="refreshPlayer.jsp">Refresh Player Cache</a><br>
<a href="deactivate.jsp">Deactivate Player</a><br>

<h2>Game Management</h2>
<a href="modifyServers.jsp">Modify Game Servers</a><br>
<br>

<h2>Tournament Management</h2>
<a href="manageTourneys.jsp">Manage Tournament</a><br>
<a href="newTourney.jsp">Create Tournament</a><br>
<a href="createTourneyForum.jsp">Create Tournament Forum</a><br>
<a href="flushTourneyCache.jsp">Flush Tourney Cache</a><br>
<br>

<% } %>

</body>
</html>
