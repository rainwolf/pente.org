<%@ page import="org.pente.game.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.core.*,
                 org.pente.turnBased.web.*,
                 org.pente.turnBased.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*,
                 java.text.*,
                 java.util.*" %>

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

int gameId = GridStateFactory.getGameId(gameName);
GridState state = GridStateFactory.createGridState(gameId);
GridCoordinates coords = new AlphaNumericGridCoordinates(19, 19);
String moveStr[] = movesList.split(",");
String moves="";
ArrayList<Integer> gameMoves = new ArrayList<Integer>();
for (int i = 0; i < moveStr.length; i++) {
    java.awt.Point p = coords.getPoint(moveStr[i]);
    int move = state.convertMove(p.x, 18-p.y);
    gameMoves.add(move);
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
    color = "#FFFFFF";
}

%>
<html>

<body>
  

 <table>
<tr>
<td valign="top" width="60%">
<canvas id="board" width="500" height="500"></canvas>
</td>

<td valign="top">

<table align="right" border=0 width="250px">
<tr>
   <td>

 <table align="right" border=1  width="250px">
<tr>
<td width="10%"></td>
   <td align="center" colspan="2">
   <font size="3">
         <b>
  <%= (((gameId % 2) == 0)?"Speed-":"")+GridStateFactory.getGameName(gameId) %>
  </b>
   </font>
   </td>
</tr>
<tr>
<td width="10%"></td>
   <td width="45%" align="center">
 <b>   <%=whiteName%> </b>
   </td>
   <td align="center" bgcolor="#000000">
 <b><font color="white">    <%=blackName%> </font>
</b>
   </td>
</tr>
</table>
</td>
</tr>
<tr>
<td>
<div style="height:200px;position: relative; overflow:hidden;">
<div id="movesTable" style="height:200px; width: 270px; right: -20px; position: absolute; align: right; overflow:auto;">
<table align="left" border=1  width="250px">
<% 
String coordinateLetters[] = {"A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T"};
int row = 0;
for( int i = 0; i < gameMoves.size(); i++ ) {
    if ((gameId != 63) && (i % 2 == 0)) {
    %> <tr> <td width="10%" align="center"> <%= ++row %> </td> <%
    }
    if ((gameId == 63) && ((i % 4 == 3) || (i == 0))) {
    %> <tr> <td width="10%" align="center"> <%= ++row %> </td> <%
    }
    %> 
    <td onclick='selectMove(<%=i%>)' id='<%=i%>' width="40%" align="center">
    <%=" " + coordinateLetters[(gameMoves.get(i) % 19)] + (19 - (gameMoves.get(i) / 19))%>
    <% if ((gameId == 63) && (i != 0) && (i + 1 < gameMoves.size())) {
        ++i;
        %>
        - <%="" + coordinateLetters[(gameMoves.get(i)  % 19)] + (19 - (gameMoves.get(i) / 19))%>
        <%
    } %>
    </td>
    <%
    if ((gameId != 63) && (i % 2 == 1)) {
    %> </tr> <%
    }
    if ((gameId == 63) && (i % 4 == 2)) {
    %> </tr> <%
    }
}
%>
</table>
</div>
</div>

 <table align="right" border=1  width="250px">
<tr>
   <td width="50%" onclick="goBack()" align="center">
   <b>back</b>
   </td>
   <td onclick="goForward()" align="center">
   <b>forward</b>
   </td>
</tr>
</table>












   </td>
</tr>
</table>




</td>
</tr>  
</table>




    <script src="/gameServer/tb/gameScript.js"></script>



    <script type="text/javascript">
        var moves = [<%=moves.substring(0, moves.length() - 1)%>];
        var game = <%= 50 + (((gameId % 2) == 0)?gameId-1:gameId) %>;
        var p1Name = "<%=whiteName%>";
        var p2Name = "<%=blackName%>";
        var rated = false;

        var gridSize = 19; 
        
        var boardSize = 420;
        var boardCanvas = document.getElementById("board");
        var boardContext = boardCanvas.getContext("2d");
        var indentWidth = (boardCanvas.width - boardSize) / 2;
        var indentHeight = (boardCanvas.height - boardSize) / 2;
        var stepX = boardSize / 18;
        var stepY = boardSize / 18;
        var boardColor;
        var radius = stepX * 95 / 200;

        var drawUntilMove;
        var whiteCaptures = 0;
        var blackCaptures = 0;
        var lastMove;
        var currentMove = -1;

            function selectMove(newMove) {
                                    // alert("cell " + newMove);
               var cell=document.getElementById(''+newMove);
               cell.style.background='Yellow';
                  resetAbstractBoard(abstractBoard);
                  drawUntilMove = newMove + 1;
                  if (game == 63 && drawUntilMove != 1) {
                      drawUntilMove += 1;
                  }
                  replayGame(abstractBoard, moves, drawUntilMove);
                  boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
                  boardContext.fill();
                  drawGrid(boardContext, boardColor, gridSize, true);
                  drawGame();
                  lastMove = moves[drawUntilMove - 1];
                  drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
                  if (game == 63 && moves.length > 1) {
                      lastMove = moves[drawUntilMove - 2];
                      drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
                  }
               if(currentMove!=-1) {
                   var cell=document.getElementById(''+currentMove);
                   cell.style.background='<%=color%>';
               }
               currentMove=newMove;
            }
            function boardClick(e) {
               if(currentMove != -1) {
                   var cell=document.getElementById(''+currentMove);
                   cell.style.background='<%=color%>';
               }
               currentMove = -1;
                var rect = boardCanvas.getBoundingClientRect();
                var offsetX = rect.left;
                var offsetY = rect.top;
                if ((drawUntilMove != moves.length)) {
                    resetAbstractBoard(abstractBoard);
                    drawUntilMove = moves.length;
                    replayGame(abstractBoard, moves, drawUntilMove);
                    boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
                    boardContext.fill();
                    drawGrid(boardContext, boardColor, gridSize, true);
                    drawGame();
                    lastMove = moves[moves.length - 1];
                    drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
                    if (game == 63 && moves.length > 1) {
                        lastMove = moves[moves.length - 2];
                        drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
                    }
                    document.getElementById("movesTable").scrollTop = document.getElementById("movesTable").scrollHeight;
                }
                if (game == 63 && moves.length > 1) {
                    selectMove(drawUntilMove - 2);
                } else {
                  selectMove(drawUntilMove - 1);
                }
            }



            // function drawGrid(boardContext, boardColor) {
            //   boardContext.save();
            //     boardContext.beginPath();
            //     boardContext.rect(indentWidth / 2, indentHeight / 2, boardSize + indentWidth, boardSize + indentHeight);
            //     boardContext.lineWidth=0.5;
            //     boardContext.fillStyle=boardColor;
            //     boardContext.shadowColor = 'Black';
            //     boardContext.shadowBlur = 5;
            //     boardContext.shadowOffsetX = radius/4;
            //     boardContext.shadowOffsetY = radius/4;
            //     boardContext.fill();     
            //     // boardContext.closePath();
            //     boardContext.restore();
            //
            //     boardContext.font = "10px sans-serif";
            //     boardContext.fillStyle='black';
            //     boardContext.lineWidth=0.5;
            //     for (var i = 0; i < 19; i++) {
            //         boardContext.moveTo(indentWidth + i*stepX, indentHeight);
            //         boardContext.lineTo(indentWidth + i*stepX, indentHeight + boardSize);
            //         boardContext.fillText(coordinateLetters[i], indentWidth + i*stepX - 2, indentHeight - 5);
            //         boardContext.fillText(coordinateLetters[i], indentWidth + i*stepX - 2, boardSize + indentHeight + 12);
            //     }
            //     for (var i = 0; i < 19; i++) {
            //         boardContext.moveTo(indentWidth, indentHeight + i*stepY);
            //         boardContext.lineTo(indentWidth + boardSize, indentHeight + i*stepY);
            //         boardContext.fillText("" + (19 - i), indentWidth - 15, indentHeight + i*stepX + 3);
            //         boardContext.fillText("" + (19 - i), boardSize + indentWidth + 6, indentHeight + i*stepX + 3);
            //     }
            //     // boardContext.strokeStyle = "#FFFFFF";
            //     boardContext.stroke();
            //     boardContext.closePath();
            //     boardContext.beginPath();
            //     boardContext.arc(indentWidth + 9*stepX, indentHeight + 9*stepY, stepX / 5, 0, Math.PI*2, true); 
            //     boardContext.stroke();
            //     boardContext.closePath();
            //     boardContext.beginPath();
            //     boardContext.arc(indentWidth + 6*stepX, indentHeight + 6*stepY, stepX / 5, 0, Math.PI*2, true); 
            //     boardContext.stroke();
            //     boardContext.closePath();
            //     boardContext.beginPath();
            //     boardContext.arc(indentWidth + 6*stepX, indentHeight + 12*stepY, stepX / 5, 0, Math.PI*2, true); 
            //     boardContext.stroke();
            //     boardContext.closePath();
            //     boardContext.beginPath();
            //     boardContext.arc(indentWidth + 12*stepX, indentHeight + 6*stepY, stepX / 5, 0, Math.PI*2, true); 
            //     boardContext.stroke();
            //     boardContext.closePath();
            //     boardContext.beginPath();
            //     boardContext.arc(indentWidth + 12*stepX, indentHeight + 12*stepY, stepX / 5, 0, Math.PI*2, true); 
            //     boardContext.stroke();
            //     boardContext.closePath();
            // }
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
                    case 69: replayGoGame(abstractBoard, movesList, until); break;
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
                    var digit = 0;
                    if (whiteCaptures > 9) {
                        digit = Math.floor(whiteCaptures / 10);
                    } else {
                        digit = whiteCaptures % 10;
                    }
                    boardContext.beginPath();
                    boardContext.font = "14px bold sans-serif";
                    boardContext.fillStyle='black';
                    boardContext.fillText("" + digit, indentWidth - 4, boardSize + indentHeight + stepY + 4);
                    boardContext.stroke();
                    boardContext.closePath();
                    if (whiteCaptures > 9) {
                        digit = whiteCaptures % 10;
                        boardContext.beginPath();
                        boardContext.font = "14px bold sans-serif";
                        boardContext.fillStyle='black';
                        boardContext.fillText("" + digit, indentWidth + stepX*2/3 - 4, boardSize + indentHeight + stepY + 4);
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
                    var digit = blackCaptures % 10;
                    boardContext.beginPath();
                    boardContext.font = "14px bold sans-serif";
                    boardContext.fillStyle='white';
                    boardContext.fillText("" + digit, boardSize + indentWidth - 4, indentHeight - stepY + 4);
                    boardContext.stroke();
                    boardContext.closePath();
                    if (blackCaptures > 9) {
                        digit = Math.floor(blackCaptures / 10);
                        boardContext.beginPath();
                        boardContext.font = "14px bold sans-serif";
                        boardContext.fillStyle='white';
                        boardContext.fillText("" + digit, boardSize + indentWidth - stepX*2/3 - 4, indentHeight - stepY + 4);
                        boardContext.stroke();
                        boardContext.closePath();
                    }
                }
            }
            // function drawStone(i, j, color) {
            //   boardContext.save();
            //     var centerX = indentWidth + stepX*(i);
            //     var centerY = indentHeight + stepY*(j);
            //     boardContext.beginPath();
            //     boardContext.arc(centerX, centerY, radius , 0, Math.PI*2, true); 
            //     if (color == true) {
            //         boardContext.fillStyle = 'black';
            //     } else {
            //         boardContext.fillStyle = 'white';
            //     }
            //     centerX -= radius/8;
            //     centerY -= radius/8;
            //     boardContext.shadowColor = 'DimGray';
            //     boardContext.shadowBlur = 1;
            //     boardContext.shadowOffsetX = radius/8;
            //     boardContext.shadowOffsetY = radius/8;
            //     if (color) {
            //         var gradient = boardContext.createRadialGradient(centerX, centerY, radius / 8, centerX, centerY, radius);
            //         gradient.addColorStop(0, 'Grey');
            //         gradient.addColorStop(1, 'Black');
            //         boardContext.fillStyle = gradient; 
            //     } else {
            //         var gradient = boardContext.createRadialGradient(centerX, centerY, 2*radius / 4, centerX, centerY, radius);
            //         gradient.addColorStop(0, 'White');
            //         gradient.addColorStop(1, 'Gainsboro');
            //         boardContext.fillStyle = gradient; 
            //     }
            //     boardContext.fill();
            //     // boardContext.lineWidth = 5;
            //     // boardContext.strokeStyle = '#003300';
            //     // boardContext.stroke();
            //     boardContext.closePath();
            //   boardContext.restore();
            // }
            function drawRedDot(i, j) {
                var centerX = indentWidth + stepX*(i);
                var centerY = indentHeight + stepY*(j);
                boardContext.beginPath();
                boardContext.arc(centerX, centerY, stepX / 7 , 0, Math.PI*2, true); 
                boardContext.fillStyle = 'red';
                boardContext.fill();
                // boardContext.lineWidth = 5;
                // boardContext.strokeStyle = '#003300';
                // boardContext.stroke();
                boardContext.closePath();
            }
            function goBack() {
                if (drawUntilMove > 1) {
                    if (game == 63 && drawUntilMove > 1) {
                        if ((drawUntilMove % 2) == 1) {
                            drawUntilMove = drawUntilMove - 1;
                        }
                        c6Move1 = -1;
                        c6Move2 = -1;
                    }
                    if (game == 57 && moves.length == 1) {
                        drawUntilMove = 2;
                        dPenteMove3 = -1;
                        dPenteMove2 = -1;
                        dPenteMove1 = -1;
                    }
                    drawUntilMove = drawUntilMove - 1;
                    boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
                    drawGrid(boardContext, boardColor, gridSize, true);
                    replayGame(abstractBoard, moves, drawUntilMove);
                    drawGame();
                    lastMove = moves[drawUntilMove - 1];
                    drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
                    if (game == 63 && drawUntilMove > 1) {
                        lastMove = moves[drawUntilMove - 2];
                        drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
                        selectMove(drawUntilMove - 2);
                    } else {
                      selectMove(drawUntilMove - 1);
                    }
                }
            }
            function goForward() {
                if (drawUntilMove < moves.length) {
                    drawUntilMove = drawUntilMove + 1;
                    if (game == 63 && drawUntilMove > 1) {
                        drawUntilMove = drawUntilMove + 1;
                    }
                    boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
                    drawGrid(boardContext, boardColor, gridSize, true);
                    replayGame(abstractBoard, moves, drawUntilMove);
                    drawGame();
                    lastMove = moves[drawUntilMove - 1];
                    drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
                    if (game == 63 && drawUntilMove > 1) {
                        lastMove = moves[drawUntilMove - 2];
                        drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
                        selectMove(drawUntilMove - 2);
                    } else {
                      selectMove(drawUntilMove - 1);
                    }
                }
            }

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
                    case 69: boardColor = goColor; break;
                    default: boardColor = penteColor; break;
                }
                boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
                drawGrid(boardContext, boardColor, gridSize, true);
                boardCanvas.addEventListener("click", boardClick, false);
                drawUntilMove = moves.length;
                playedMove = -1;
                lastMove = moves[drawUntilMove - 1];
            }


        init();
        replayGame(abstractBoard, moves, moves.length);
        drawGame();
        lastMove = moves[drawUntilMove - 1];
        drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
        if (game === 63 && moves.length > 1) {
            lastMove = moves[drawUntilMove - 2];
            drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
            selectMove(drawUntilMove - 2);
        } else {
          selectMove(drawUntilMove - 1);
        }
        document.getElementById("movesTable").scrollTop = document.getElementById("movesTable").scrollHeight;
    </script>

</body>
</html>