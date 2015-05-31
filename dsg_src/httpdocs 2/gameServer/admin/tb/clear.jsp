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

String what = request.getParameter("what");
if (what.equals("all")) {
	tbGameStorer.uncacheAll();
}
else if (what.equals("player")) {
	String pidStr = request.getParameter("pid");
	long pid = Long.parseLong(pidStr);
	tbGameStorer.uncacheGamesForPlayer(pid);
}
%>

<html>
<head><title>Tb - uncache</title></head>
<body>
Games uncached successfully.

<br>
<a href=".">Back to TB Main</a>
</body>
</html>