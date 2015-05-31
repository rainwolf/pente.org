<%@ page import="org.pente.game.*, 
				 org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.turnBased.*, 
                 java.util.*,
                 java.text.*" %>



<%
Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());


CacheTBStorer tbGameStorer = (CacheTBStorer) resources.getTbGameStorer();
CacheStats cacheStats = tbGameStorer.getCacheStats();
List<String> threadStates = tbGameStorer.getThreadStates();
%>

<html>
<head>
 <title>TB Cache Statistics</title>
</head>

<body>

<b>Sets cached:</b> <%= tbGameStorer.getSets().size() %>
<br>
<b>Games in cache:</b> <%= tbGameStorer.getGames().size() %>
[<a href="all.jsp">view</a>]
[<a href="clear.jsp?what=all">clear</a>]<br>
<br>
<b>Waiting sets in cache:</b> <%= tbGameStorer.getWaitingSets().size() %><br>
<br>
<b>Thread states:</b><br>
<% for (String s : threadStates) { %>
   <%= s %><br>
<% } %>
<br>
<table border="1">
<tr>
 <td colspan="2">Cache Stats:</td>
</tr>
<tr>
 <td>Sets Created</td>
 <td><%= cacheStats.getSetsCreated() %></td>
</tr>
<tr> 
 <td>Sets Completed</td>
 <td><%= cacheStats.getSetsCompleted() %></td>
</tr>
<tr>
 <td>Games Created</td>
 <td><%= cacheStats.getGamesCreated() %></td>
</tr>
<tr> 
 <td>Games Completed</td>
 <td><%= cacheStats.getGamesCompleted() %></td>
</tr>
<tr>
 <td>Moves Made</td>
 <td><%= cacheStats.getMovesMade() %></td>
</tr>
<tr>
 <td>Sets Cached</td>
 <td><%= cacheStats.getSetsCached() %></td>
</tr>
<tr>
 <td>Sets Uncached</td>
 <td><%= cacheStats.getSetsUncached() %></td>
</tr>
<tr>
 <td>Games Cached</td>
 <td><%= cacheStats.getGamesCached() %></td>
</tr>
<tr>
 <td>Games Uncached</td>
 <td><%= cacheStats.getGamesUncached() %></td>
</tr>
<tr>
 <td>Set Load Details</td>
 <td><%= cacheStats.getSetLoadsCached() %> / <%= cacheStats.getSetLoads() %> <%= cacheStats.getSetLoadHitRate() %>
</tr>
<tr>
 <td>Game Load Details</td>
 <td><%= cacheStats.getGameLoadsCached() %> / <%= cacheStats.getGameLoads() %> <%= cacheStats.getGameLoadHitRate() %>
</tr>

</table>

<br>
<b>Players with cached sets:</b><br>
<% for (Long pid : tbGameStorer.getCachedPids()) { 
       int cnt = tbGameStorer.getSetsByPid(pid).size(); %>
       <a href="player.jsp?pid=<%= pid %>"><%= pid %></a>: <%= cnt %><br>
<% } %>

</body>
</html>