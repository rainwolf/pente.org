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
String pageName="all.jsp?";
%>

<html>
<head>
 <title>TB Cache Statistics</title>
</head>

<body>

<b>Sets cached:</b> <%= tbGameStorer.getSets().size() %>
[<a href="clear.jsp?what=all">clear</a>]<br>
<br>
<b>Games cached:</b> <%= tbGameStorer.getGames().size() %>
<br>

<% List<TBSet> sets = tbGameStorer.getSets(); 
   List<TBGame> games = new ArrayList<TBGame>(sets.size()*2); 
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