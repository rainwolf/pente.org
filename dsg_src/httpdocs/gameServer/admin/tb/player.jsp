<%@ page import="org.pente.game.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.turnBased.*,
                 java.util.*" %>

<%
   Resources resources = (Resources) application.getAttribute(
      Resources.class.getName());

   CacheTBStorer tbGameStorer = (CacheTBStorer) resources.getTbGameStorer();
   DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
   String pidStr = request.getParameter("pid");
   long pid = Long.parseLong(pidStr);
   DSGPlayerData player = dsgPlayerStorer.loadPlayer(pid);
   String pageName = "player.jsp?pid=" + pid + "&";
%>

<html>
<head>
   <title>TB Cache Statistics - Player</title>
</head>

<body>

<b>Sets for player <%= player.getName() %>:</b> <%= tbGameStorer.getSetsByPid(pid).size() %>
[<a href="clear.jsp?what=player&pid=<%= pid %>">clear</a>]<br>
<br>

<% List<TBSet> sets = tbGameStorer.getSetsByPid(pid);
   List<TBGame> games = new ArrayList<TBGame>(sets.size() * 2);
   for (TBSet s : sets) {
      games.add(s.getGame1());
      if (s.isTwoGameSet()) games.add(s.getGame2());
   }
%>
<%@include file="games.jsp" %>

<br>
<a href=".">Back to TB Main</a>
</body>
</html>