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
                 org.pente.turnBased.*" %>
<%
   String gidStr = request.getParameter("g");
   if (gidStr == null) {
      return;
   }
   long gid = Long.parseLong(gidStr);
   Resources resources = (Resources) application.getAttribute(
      Resources.class.getName());

   DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
   TBGameStorer tbGameStorer = resources.getTbGameStorer();
   TBGame game = tbGameStorer.loadGame(gid);

   String player = (String) request.getAttribute("name");
   DSGPlayerData playerData = null;
   try {
      playerData = resources.getDsgPlayerStorer().
         loadPlayer(player);

   } catch (DSGPlayerStoreException dpse) {
      //log4j.error("Error getting attach", dpse);
   }
   if (!player.equals("dweebo") && game.getTbSet().isPrivateGame() &&
      playerData.getPlayerID() != game.getPlayer1Pid() &&
      playerData.getPlayerID() != game.getPlayer2Pid()) {

      response.sendError(404);
      return;
   } else if (game.getState() == TBGame.STATE_NOT_STARTED ||
      game.getState() == TBGame.STATE_CANCEL) {
      response.sendError(404);
      return;
   } else if (!player.equals("dweebo") && game.getState() == TBGame.STATE_ACTIVE &&
      playerData.getPlayerID() != game.getPlayer1Pid() &&
      playerData.getPlayerID() != game.getPlayer2Pid()) {
      response.sendError(404);
      return;
   }

   GameData gd = game.convertToGameData(dsgPlayerStorer);
   PGNGameFormat gf = new PGNGameFormat("\r\n", "MM/dd/yyyy");
   StringBuffer outBuf = new StringBuffer();
   gf.format(gd, outBuf);

   response.setContentType("text/plain");
%><%= outBuf %>