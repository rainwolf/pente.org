<%@ page import="org.pente.game.*, org.pente.turnBased.*,
                 org.pente.turnBased.web.*,
                 org.pente.gameDatabase.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*,
                 java.text.*,
                 java.util.*" %>

<%@ page contentType="text/html; charset=UTF-8" %>
<%  

TBSet set = game.getTbSet();

DSGPlayerData p1 = dsgPlayerStorer.loadPlayer(game.getPlayer1Pid());
DSGPlayerGameData p1GameData = p1.getPlayerGameData(game.getGame());
DSGPlayerData p2 = dsgPlayerStorer.loadPlayer(game.getPlayer2Pid());
DSGPlayerGameData p2GameData = p2.getPlayerGameData(game.getGame());
// String myTurn = (String) request.getAttribute("myTurn");
// if (myTurn == null) myTurn="false";
String setStatus = "active";
if (set.isDraw()) {
    setStatus = "draw";
}
else if (set.isCancelled()) {
    setStatus = "cancelled";
}
else if (set.isCompleted()) {
    long wPid = set.getWinnerPid();
    if (wPid == p1.getPlayerID()) {
        setStatus = p1.getName() + " wins";
    }
    else if (wPid == p2.getPlayerID()) {
        setStatus = p2.getName() + " wins";
    }
}
String otherGame = "";
if (set.isTwoGameSet()) {
    otherGame = Long.toString(set.getOtherGame(game.getGid()).getGid());
}

String moves = "";
String seqNums = "";
String dates = "";
String players = ""; //indicates which seat made message
for (int i = 0; i < game.getNumMoves(); i++) {
    moves += game.getMove(i) + ",";
}


int height = 550;
int width = 700;
if (request.getParameter("h") != null) {
    try { height = Integer.parseInt(request.getParameter("h")); } catch (NumberFormatException n) {}
}
if (request.getParameter("w") != null) {
    try { width = Integer.parseInt(request.getParameter("w")); } catch (NumberFormatException n) {}
}

%>

<% pageContext.setAttribute("title", "Game"); %>
<% pageContext.setAttribute("leftNav", "false"); %>

<%
String version = globalResources.getAppletVersion();

String cancelRequested="false";
%>



<table border="0" colspacing="1" colpadding="1">

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


<table>
<tr>
<td align="center">
        <canvas id="<%=game.getGid()+"board"%>" width="230" height="230" style="position: relative;"></canvas>
        <br>
             <table align="center" width="100%" border=1>
            <tr>
               <td align="center" width="50%">
             <b>   <%=p1.getName()%> </b>
             <br>
               <%
                     DSGPlayerData d = p1;
                     DSGPlayerGameData dsgPlayerGameData = p1GameData;
                 %>
                 <% if (dsgPlayerGameData != null) { %><%@ include file="../ratings.jspf" %><% } %>
               </td>
               <td align="center" bgcolor="#000000">
             <font color="white"> <b>  <%=p2.getName()%>
            </b>
            <br>
               <%
                     d = p2;
                     dsgPlayerGameData = p2GameData;
                 %>
                 <% if (dsgPlayerGameData != null) { %><%@ include file="../ratings.jspf" %><% } %>
            </font>
               </td>
            </tr>

            </table>
</td>

</tr>  
</table>
<!-- </center> -->




<script type="text/javascript">
window.google_analytics_uacct = "UA-20529582-2";
</script>


    <script src="/gameServer/tb/gameScript.js"></script>

    <script type="text/javascript">
        var moves = [<%=moves.substring(0, moves.length() - 1)%>];
        var active = <%=!"false".equals(myTurn)%>;
        var game = <%= game.getGame() %>;
        var myName = "<%= me %>";
        var p1Name = "<%=p1.getName()%>";
        var p2Name = "<%=p2.getName()%>";
        var opponentName = "<%= (me.equals(p1.getName())?p2.getName():p1.getName()) %>";
        var iAmP1 = <%=me.equals(p1.getName())%>;

        var boardSize = 200;
        var boardCanvas = document.getElementById("<%=game.getGid()+"board"%>");
        var boardContext = boardCanvas.getContext("2d");
        var indentWidth = (boardCanvas.width - boardSize) / 2;
        var indentHeight = (boardCanvas.height - boardSize) / 2;
        var stepX = boardSize / 18;
        var stepY = boardSize / 18;
        var boardColor;
        var radius = stepX * 95 / 200;


        var drawUntilMove;
        var playedMove;
        var whiteCaptures = 0;
        var blackCaptures = 0;
        var lastMove;
        var rated = <%= game.isRated()%>;
        var c6Move1 = -1;
        var c6Move2 = -1;
        var dPenteMove1 = -1;
        var dPenteMove2 = -1;
        var dPenteMove3 = -1;
        var dPenteChoice = <%= game.getDPenteState() == 2 %>;
        var dPenteSwap = <%= game.didDPenteSwap()%>;

        var stoneColor = true;
        var trackingI = -1, trackingJ = -1;
        var iRadius = 6*radius/4;

        var currentMove = -1;

            function init() {
                switch (game) {
                    case 51: boardColor = penteColor; break;
                    case 53: boardColor = keryPenteColor; break;
                    case 55: boardColor = gomokuColor; break;
                    case 57: boardColor = dPenteColor; break;
                    case 59: boardColor = gPenteColor; break;
                    case 61: boardColor = poofPenteColor; break;
                    case 63: boardColor = connect6Color; break;
                    case 65: boardColor = boatPenteColor; break;
                    case 67: boardColor = dkeryoPenteColor; break;
                    default: boardColor = penteColor; break;
                }
                boardCanvas.addEventListener("click", boardClick, false);
                boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
                drawGrid(boardContext, boardColor);

                drawUntilMove = moves.length;
                playedMove = -1;
                lastMove = moves[drawUntilMove - 1];

            }

            function boardClick() {
                window.open('/gameServer/tb/game?gid=<%=game.getGid()%>&command=load&mobile',"_self");
            }


            function drawGrid(boardContext, boardColor) {
              boardContext.save();
                boardContext.beginPath();
                boardContext.rect(indentWidth / 2, indentHeight / 2, boardSize + indentWidth, boardSize + indentHeight);
                boardContext.lineWidth=0.5;
                boardContext.fillStyle=boardColor;
                boardContext.shadowColor = 'Black';
                boardContext.shadowBlur = 5;
                boardContext.shadowOffsetX = radius/4;
                boardContext.shadowOffsetY = radius/4;
                boardContext.fill();     
                // boardContext.closePath();
                boardContext.restore();

                // boardContext.beginPath();
                boardContext.font = "10px sans-serif";
                boardContext.fillStyle='black';
                boardContext.lineWidth=0.2;
                for (var i = 0; i < 19; i++) {
                    boardContext.moveTo(indentWidth + i*stepX, indentHeight);
                    boardContext.lineTo(indentWidth + i*stepX, indentHeight + boardSize);
                    // boardContext.fillText(coordinateLetters[i], indentWidth + i*stepX - 2, indentHeight - 5);
                    // boardContext.fillText(coordinateLetters[i], indentWidth + i*stepX - 2, boardSize + indentHeight + 12);
                }
                for (var i = 0; i < 19; i++) {
                    boardContext.moveTo(indentWidth, indentHeight + i*stepY);
                    boardContext.lineTo(indentWidth + boardSize, indentHeight + i*stepY);
                    // boardContext.fillText("" + (19 - i), indentWidth - 15, indentHeight + i*stepX + 3);
                    // boardContext.fillText("" + (19 - i), boardSize + indentWidth + 6, indentHeight + i*stepX + 3);
                }
                // boardContext.strokeStyle = "#FFFFFF";
                boardContext.stroke();
                boardContext.closePath();
                boardContext.beginPath();
                boardContext.arc(indentWidth + 9*stepX, indentHeight + 9*stepY, stepX / 10, 0, Math.PI*2, true); 
                boardContext.stroke();
                boardContext.closePath();
                boardContext.beginPath();
                boardContext.arc(indentWidth + 6*stepX, indentHeight + 6*stepY, stepX / 10, 0, Math.PI*2, true); 
                boardContext.stroke();
                boardContext.closePath();
                boardContext.beginPath();
                boardContext.arc(indentWidth + 6*stepX, indentHeight + 12*stepY, stepX / 10, 0, Math.PI*2, true); 
                boardContext.stroke();
                boardContext.closePath();
                boardContext.beginPath();
                boardContext.arc(indentWidth + 12*stepX, indentHeight + 6*stepY, stepX / 10, 0, Math.PI*2, true); 
                boardContext.stroke();
                boardContext.closePath();
                boardContext.beginPath();
                boardContext.arc(indentWidth + 12*stepX, indentHeight + 12*stepY, stepX / 10, 0, Math.PI*2, true); 
                boardContext.stroke();
                boardContext.closePath();
            }
            function drawStone(i, j, color) {
              boardContext.save();
                var centerX = indentWidth + stepX*(i);
                var centerY = indentHeight + stepY*(j);
                boardContext.beginPath();
                boardContext.arc(centerX, centerY, radius , 0, Math.PI*2, true); 
                if (color == true) {
                    boardContext.fillStyle = 'black';
                } else {
                    boardContext.fillStyle = 'white';
                }
                centerX -= radius/8;
                centerY -= radius/8;
                boardContext.shadowColor = 'DimGray';
                boardContext.shadowBlur = 1;
                boardContext.shadowOffsetX = radius/8;
                boardContext.shadowOffsetY = radius/8;
                if (color) {
                    var gradient = boardContext.createRadialGradient(centerX, centerY, radius / 8, centerX, centerY, radius);
                    gradient.addColorStop(0, 'Grey');
                    gradient.addColorStop(1, 'Black');
                    boardContext.fillStyle = gradient; 
                } else {
                    var gradient = boardContext.createRadialGradient(centerX, centerY, 2*radius / 4, centerX, centerY, radius);
                    gradient.addColorStop(0, 'White');
                    gradient.addColorStop(1, 'Gainsboro');
                    boardContext.fillStyle = gradient; 
                }
                boardContext.fill();
                // boardContext.lineWidth = 5;
                // boardContext.strokeStyle = '#003300';
                // boardContext.stroke();
                boardContext.closePath();
              boardContext.restore();
            }
            function drawRedDot(i, j) {
                var centerX = indentWidth + stepX*(i);
                var centerY = indentHeight + stepY*(j);
                boardContext.beginPath();
                boardContext.arc(centerX, centerY, stepX / 7 , 0, Math.PI*2, true); 
                boardContext.fillStyle = 'red';
                boardContext.fill();
                boardContext.closePath();
            }
            function replayGame(abstractBoard, movesList, until) {
                whiteCaptures = 0;
                blackCaptures = 0;
                switch(game) {
                    case 51: replayPenteGame(abstractBoard, movesList, until); break;
                    case 53: replayKeryoPenteGame(abstractBoard, movesList, until); break;
                    case 55: replayGomokuGame(abstractBoard, movesList, until); break;
                    case 57: replayPenteGame(abstractBoard, movesList, until); break;
                    case 59: replayGPenteGame(abstractBoard, movesList, until); break;
                    case 61: replayPoofPenteGame(abstractBoard, movesList, until); break;
                    case 63: replayConnect6Game(abstractBoard, movesList, until); break;
                    case 65: replayPenteGame(abstractBoard, movesList, until); break;
                    case 67: replayKeryoPenteGame(abstractBoard, movesList, until); break;
                }
            }

            function drawCaptures () {
                if (whiteCaptures > 0) {
                    for (var i = 0; i < whiteCaptures; i++) {
                        boardContext.beginPath();
                        boardContext.arc( indentWidth + i*stepX*2/3, boardSize + indentHeight + stepY, stepX / 3 , 0, Math.PI*2, true); 
                        boardContext.fillStyle = 'white';
                        boardContext.fill();
                        boardContext.stroke();
                        boardContext.closePath();
                    }
                }
                if (blackCaptures > 0) {
                    for (var i = 0; i < blackCaptures; i++) {
                        boardContext.beginPath();
                        boardContext.arc( boardSize + indentWidth - i*stepX*2/3, indentHeight - stepY, stepX / 3 , 0, Math.PI*2, true); 
                        boardContext.fillStyle = 'black';
                        boardContext.fill();
                        boardContext.stroke();
                        boardContext.closePath();
                    }
                }
            }

        init();
        replayGame(abstractBoard, moves, moves.length);
        drawGame();
        lastMove = moves[drawUntilMove - 1];
        drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
        if (game == 63 && moves.length > 1) {
            lastMove = moves[drawUntilMove - 2];
            drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
        }
    </script>


    
 </td>
</tr>

</table>
