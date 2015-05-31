<%@ page import="org.pente.game.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.core.*,
                 org.pente.turnBased.web.*,
                 org.pente.turnBased.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*" %>

<%!
private static String version;
public void jspInit() {
    ServletContext ctx = getServletContext();

    Resources globalResources = (Resources) ctx.getAttribute(Resources.class.getName());
    version = globalResources.getAppletVersion();
}
%>

<%  

//required http params are:
//g=game name
//m=moves

String gameName = request.getParameter("g");
String movesList = request.getParameter("m");
String whiteName = request.getParameter("wn");
String blackName = request.getParameter("bn");
if (movesList == null || movesList.equals("") ||
    gameName == null || gameName.equals("")) {
	response.sendError(500, "Invalid request");
	return;
}
if (whiteName == null) whiteName = "";
if (blackName == null) blackName = "";

int g = GridStateFactory.getGameId(gameName);
GridState state = GridStateFactory.createGridState(g);
GridCoordinates coords = new AlphaNumericGridCoordinates(19, 19);
String moveStr[] = movesList.split(",");
String moves="";
for (int i = 0; i < moveStr.length; i++) {
    java.awt.Point p = coords.getPoint(moveStr[i]);
    int move = state.convertMove(p.x, 18-p.y);
    moves += move + ",";
}

int height = 590;
int width = 770;
if (request.getParameter("h") != null) {
    try { height = Integer.parseInt(request.getParameter("h")); } catch (NumberFormatException n) {}
}
if (request.getParameter("w") != null) {
    try { width = Integer.parseInt(request.getParameter("w")); } catch (NumberFormatException n) {}
}

String color = request.getParameter("color");
if (color == null) {
    color = "#ffffff";
}

%>
<html><body style="background: <%= color %>">
	<object 
	  classid="clsid:8AD9C840-044E-11D1-B3E9-00805F499D93"
	  width="<%= width %>" height="<%= height %>"
	  codebase="http://java.sun.com/products/plugin/1.3/jinstall-13-win32.cab#Version=1,3,0,0">
	  
	  <param name="code" value="org.pente.turnBased.swing.TBApplet.class">
	  <param name="codebase" value="/gameServer/lib/">
	  <param name="archive" value="tb__V<%= version %>.jar">
	  <param name="type" value="application/x-java-applet;version=1.3">
      <param name="scriptable" value="false">
        <param name="me" value="me">
        <param name="gid" value="0">
        <param name="event" value="">
     	<param name="game" value="<%= g %>">
     	<param name="player1" value="<%= whiteName %>">
     	<param name="player1Rating" value="0">
     	<param name="player1RatingGif" value="<%= SimpleDSGPlayerGameData.getRatingsGifRatingOnly(0) %>">
     	<param name="player2" value="<%= blackName %>">
     	<param name="player2Rating" value="0">
     	<param name="player2RatingGif" value="<%= SimpleDSGPlayerGameData.getRatingsGifRatingOnly(0) %>">
     	<param name="myTurn" value="false">
     	<param name="moves" value="<%= moves %>">
     	<param name="showMessages" value="false">
        <param name="messages" value="">
        <param name="moveNums" value="">
        <param name="seqNums" value="">
        <param name="dates" value="">
        <param name="players" value="">
     	<param name="timer" value="">
        <param name="rated" value="false">
        <param name="private" value="false">
        <param name="timeout" value="0">
        <param name="gameState" value="C">
        <param name="winner" value="0">
        <param name="completedDate" value="<%= System.currentTimeMillis() %>">
        <param name="timezone" value="America/New York">
        <param name="setStatus" value="">
        <param name="otherGame" value="">
        <param name="attach" value="true">
        <param name="color" value="<%= color %>">
      <comment>
       <embed type="application/x-java-applet;version=1.3"  
              code="org.pente.turnBased.swing.TBApplet.class" 
              codebase="/gameServer/lib/" 
              archive="tb__V<%= version %>.jar" 
              width="<%= width %>" 
              height="<%= height %>"
              me="me"
              gid="0"
              event=""
     	      game="<%= g %>"
     	      player1="<%= whiteName %>"
     	      player1Rating="0"
     	      player1RatingGif="<%= SimpleDSGPlayerGameData.getRatingsGifRatingOnly(0) %>"
     	      player2="<%= blackName %>"
     	      player2Rating="0"
     	      player2RatingGif="<%= SimpleDSGPlayerGameData.getRatingsGifRatingOnly(0) %>"
     	      myTurn="false"
     	      moves="<%= moves %>"
     	      showMessages="false"
              messages=""
              moveNums=""
              seqNums=""
              dates=""
              players=""
     	      timer=""
              rated="false"
              private="false"
              timeout="0"
              gameState="C"
	          winner="0"
	          completedDate="<%= System.currentTimeMillis() %>"
	          timezone="America/New York"
	          setStatus=""
              otherGame=""
              attach="true"
              color="<%= color %>"
              scriptable="false"
              pluginspage="http://java.sun.com/products/plugin/1.3/plugin-install.html"><noembed></comment></noembed>
      </embed>

    </object>
</body>
</html>