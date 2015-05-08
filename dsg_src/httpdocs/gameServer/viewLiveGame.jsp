<%@ page import="org.pente.game.*,
                 org.pente.gameServer.core.*,
                 org.pente.turnBased.web.*,
                 org.pente.turnBased.*,
                 java.net.URLEncoder,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*" %>

<%  
com.jivesoftware.base.FilterChain filters = 
	new com.jivesoftware.base.FilterChain(
        null, 1, new com.jivesoftware.base.Filter[] { 
            new HTMLFilter(), new URLConverter(), new TBEmoticon(), new Newline() }, 
            new long[] { 1, 1, 1, 1 });

GameData game = (GameData) request.getAttribute("game");
TBGame tbGame = (TBGame) request.getAttribute("tbGame");
int gameId = GridStateFactory.getGameId(game.getGame()) + 50;//add 50 for tb

String moves="";
for (int i = 0; i < game.getNumMoves(); i++) {
    moves += game.getMove(i) + ",";
}


boolean turnBased = false;
String timer = "";
if (game.getEvent() != null && game.getEvent().equals("Turn-based Game") &&
    tbGame != null) {
    timer = game.getInitialTime() + " days/move";
    turnBased = true;
}
else if (game.getTimed()) {
	timer = game.getInitialTime() + "/" + game.getIncrementalTime();
}
else {
	timer = "No";
}
int height = 550;
int width = 700;
if (request.getParameter("h") != null) {
    try { height = Integer.parseInt(request.getParameter("h")); 
          height -= 50; // for bottom buttons
    } catch (NumberFormatException n) {}
}
if (request.getParameter("w") != null) {
    try { width = Integer.parseInt(request.getParameter("w")); } catch (NumberFormatException n) {}
}
%>

<% pageContext.setAttribute("title", "Game"); %>
<% pageContext.setAttribute("leftNav", "false"); %>
<%@ include file="begin.jsp" %>

<%
String version = globalResources.getAppletVersion();


String setStatus = "";
String otherGame = "";
if (turnBased) {
	TBSet set = tbGame.getTbSet();
	if (set.isDraw()) {
		setStatus = "draw";
	}
	else if (set.isCancelled()) {
    	setStatus = "cancelled";
	}
	else if (set.isCompleted()) {
		long wPid = set.getWinnerPid();
		if (wPid == game.getPlayer1Data().getUserID()) {
			setStatus = game.getPlayer1Data().getUserIDName() + " wins";
		}
		else if (wPid == game.getPlayer2Data().getUserID()) {
			setStatus = game.getPlayer2Data().getUserIDName() + " wins";
		}
	}
	if (set.isTwoGameSet()) {
		otherGame = Long.toString(set.getOtherGame(tbGame.getGid()).getGid());
	}
}

String messages = "";
String moveNums = "";
String seqNums = "";
String dates = "";
String players = ""; //indicates which seat made message
boolean showMessages = false;
DSGPlayerData meData = dsgPlayerStorer.loadPlayer(me);
if (turnBased && 
	(game.getPlayer1Data().getUserID() == meData.getPlayerID() ||
	 game.getPlayer2Data().getUserID() == meData.getPlayerID())) {
	
	showMessages = true;

	for (TBMessage m : tbGame.getMessages()) {
		// bug in URLConverter
		if (m.getMessage().length() == 1) {
			messages += m.getMessage() + ",";
		}
		else {
		    messages += MessageEncoder.encodeMessage(
		        filters.applyFilters(0, m.getMessage())) + ",";
		}
        seqNums += m.getSeqNbr() + ",";
	    moveNums += m.getMoveNum() + ",";
	    dates += m.getDate().getTime() + ",";
	    if (tbGame.getPlayer1Pid() == m.getPid()) {
	    	players += "1,";
	    }
	    else {
	    	players += "2,";
	    }
	}
}

String attach = (String) request.getAttribute("attach");
if (attach == null) {
    attach = "true";
}
String color = request.getParameter("color");
if (color == null) {
    color = "#ffffff";
}
%>

<table align="left" width="100%" border="0" colspacing="1" colpadding="1">



<% String error = (String) request.getAttribute("error");
   if (error != null) { %>

<tr>
 <td>
  <b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
   Error: <%= error %>
  </font></b>
 </td>
</tr>

<%   
   }
%>

<tr>
 <td>
	<object 
	  classid="clsid:8AD9C840-044E-11D1-B3E9-00805F499D93"
	  width="<%= width %>" height="<%= height %>"
	  codebase="http://java.sun.com/products/plugin/1.3/jinstall-13-win32.cab#Version=1,3,0,0">
	  
	  <param name="code" value="org.pente.turnBased.swing.TBApplet.class">
	  <param name="codebase" value="/gameServer/lib/">
	  <param name="archive" value="tb__V<%= version %>.jar">
	  <param name="type" value="application/x-java-applet;version=1.3">
      <param name="scriptable" value="false">
        <param name="me" value="<%= me %>">
        <param name="gid" value="<%= game.getGameID() %>">
        <param name="event" value="<%= game.getEvent() %>">
     	<param name="game" value="<%= gameId %>">
     	<param name="player1" value="<%= game.getPlayer1Data().getUserIDName() %>">
     	<param name="player1Rating" value="<%= game.getPlayer1Data().getRating() %>">
     	<param name="player1RatingGif" value="<%= SimpleDSGPlayerGameData.getRatingsGifRatingOnly(game.getPlayer1Data().getRating()) %>">
     	<param name="player2" value="<%= game.getPlayer2Data().getUserIDName() %>">
     	<param name="player2Rating" value="<%= game.getPlayer2Data().getRating() %>">
     	<param name="player2RatingGif" value="<%= SimpleDSGPlayerGameData.getRatingsGifRatingOnly(game.getPlayer2Data().getRating()) %>">
     	<param name="myTurn" value="false">
     	<param name="moves" value="<%= moves %>">
     	<param name="showMessages" value="<%= showMessages %>">
        <param name="messages" value="<%= messages %>">
        <param name="moveNums" value="<%= moveNums %>">
        <param name="seqNums" value="<%= seqNums %>">
        <param name="dates" value="<%= dates %>">
        <param name="players" value="<%= players %>">
     	<param name="timer" value="<%= timer %>">
        <param name="rated" value="<%= game.getRated() ? "true" : "false" %>">
        <param name="private" value="<%= game.isPrivateGame() ? "true" : "false" %>">
        <param name="timeout" value="0">
        <param name="gameState" value="C">
        <param name="winner" value="<%= game.getWinner()%>">
        <param name="completedDate" value="<%= game.getDate().getTime() %>">
        <param name="timezone" value="<%= meData.getTimezone() %>">
        <param name="setStatus" value="<%= setStatus %>">
        <param name="otherGame" value="<%= otherGame %>">
        <param name="attach" value="<%= attach %>">
        <param name="color" value="<%= color %>">
        <% if (gameId == GridStateFactory.DPENTE || gameId == GridStateFactory.SPEED_DPENTE) { %>
        <param name="dPenteState" value="<%= TBGame.DPENTE_STATE_DECIDED %>">
        <param name="dPenteSwap" value="<%= game.didPlayersSwap() ? "true" : "false" %>">
        <% } %>
      <comment>
       <embed type="application/x-java-applet;version=1.3"  
              code="org.pente.turnBased.swing.TBApplet.class" 
              codebase="/gameServer/lib/" 
              archive="tb__V<%= version %>.jar" 
              width="<%= width %>" 
              height="<%= height %>"
              me="<%= me %>"
              gid="<%= game.getGameID() %>"
              event="<%= game.getEvent() %>"
     	      game="<%= gameId %>"
     	      player1="<%= game.getPlayer1Data().getUserIDName() %>"
     	      player1Rating="<%= game.getPlayer1Data().getRating() %>"
     	      player1RatingGif="<%= SimpleDSGPlayerGameData.getRatingsGifRatingOnly(game.getPlayer1Data().getRating()) %>"
     	      player2="<%= game.getPlayer2Data().getUserIDName() %>"
     	      player2Rating="<%= game.getPlayer2Data().getRating() %>"
     	      player2RatingGif="<%= SimpleDSGPlayerGameData.getRatingsGifRatingOnly(game.getPlayer2Data().getRating()) %>"
     	      myTurn="false"
     	      moves="<%= moves %>"
     	      showMessages="<%= showMessages %>"
              messages="<%= messages %>"
              moveNums="<%= moveNums %>"
              seqNums="<%= seqNums %>"
              dates="<%= dates %>"
              players="<%= players %>"
     	      timer="<%= timer %>"
              rated="<%= game.getRated() ? "true" : "false" %>"
              private="<%= game.isPrivateGame() ? "true" : "false" %>"
              timeout="0"
              gameState="C"
	          winner="<%= game.getWinner()%>"
	          completedDate="<%= game.getDate().getTime() %>"
	          timezone="<%= meData.getTimezone() %>"
	          setStatus="<%= setStatus %>"
              otherGame="<%= otherGame %>"
              attach="<%= attach %>"
              color="<%= color %>"
	          <% if (gameId == GridStateFactory.DPENTE || gameId == GridStateFactory.SPEED_DPENTE) { %>
	          dPenteState="<%= TBGame.DPENTE_STATE_DECIDED %>"
	          dPenteSwap="<%= game.didPlayersSwap() ? "true" : "false" %>"
	          <% } %>
              scriptable="false"
              pluginspage="http://java.sun.com/products/plugin/1.3/plugin-install.html"><noembed></comment></noembed>
      </embed>

    </object>
    
    <br>

    <a class="button" href="/gameServer/pgn.jsp?g=<%= game.getGameID() %>"><span style="color:white">Text version</span></a>
    <% if (!game.isPrivateGame()) { %><a class="button" style="clear:right;" href="/gameServer/forums/post!default.jspa?forumID=27&body=[game]<%= game.getGameID() %>[/game]<%= URLEncoder.encode("\n\nEnter your comments here") %>&subject=<%= URLEncoder.encode("Game: " + game.getPlayer1Data().getUserIDName() + " vs. " + game.getPlayer2Data().getUserIDName() + " " + dateFormat.format(game.getDate().getTime())) %>"><span style="color:white">Discuss this game</span></a><% } %>
    <br>
    <br>
 </td>
</tr>

</table>


<%@ include file="end.jsp" %>