<%@ page import="java.text.*,
                 java.util.*,
                 java.sql.*,
                 java.util.Date,
                 
                 javax.servlet.*,
                 javax.servlet.http.*,
                 
                 com.jivesoftware.base.*,
                 com.jivesoftware.forum.*,
                 
                 org.pente.jive.*,
                 org.pente.database.*,
                 org.pente.game.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.client.web.*,
                 org.pente.gameServer.tourney.*,
                 org.pente.turnBased.*" %><%
String gidStr = request.getParameter("g");
if (gidStr == null) {
    return;
}
long gid = Long.parseLong(gidStr);
Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());
   
DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();

String player = (String) request.getAttribute("name");
DSGPlayerData playerData = null;
try {
	playerData = resources.getDsgPlayerStorer().
		loadPlayer(player);
	// if player prefers to make moves attached or not

} catch (DSGPlayerStoreException dpse) {
	//log4j.error("Error getting attach", dpse);
}

GameData game = new DefaultGameData();
resources.getGameStorer().loadGame(gid, game);

if (game.isPrivateGame() && 
	playerData.getPlayerID() != game.getPlayer1Data().getUserID() &&
	playerData.getPlayerID() != game.getPlayer2Data().getUserID()) {

	response.sendError(404);
	return;
}

PGNGameFormat gf = new PGNGameFormat("\r\n", "MM/dd/yyyy");
StringBuffer outBuf = new StringBuffer();
gf.format(game, outBuf);

response.setContentType("text/plain");
%><%= outBuf %>