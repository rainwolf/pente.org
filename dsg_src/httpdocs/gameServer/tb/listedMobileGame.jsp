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
if ("".equals(moves)) {
    moves = ",";
}


int height = 550;
int width = 700;
if (request.getParameter("h") != null) {
    try { height = Integer.parseInt(request.getParameter("h")); } catch (NumberFormatException n) {}
}
if (request.getParameter("w") != null) {
    try { width = Integer.parseInt(request.getParameter("w")); } catch (NumberFormatException n) {}
}
    boolean isGo = game.getGame() == GridStateFactory.TB_GO || game.getGame() == GridStateFactory.TB_GO9 || game.getGame() == GridStateFactory.TB_GO13;
    int gridSize = 19;
    if (game.getGame() == GridStateFactory.TB_GO9) {
        gridSize = 9;
    } else if (game.getGame() == GridStateFactory.TB_GO13) {
        gridSize = 13;
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
               <td align="center" width="50%" bgcolor="#<%=(!isGo?"FFFFFF":"000000")%>">
                    <b><font color="<%=(!isGo?"black":"white")%>"><%=p1.getName()%>
                    </b>
             <br>
               <%
                     DSGPlayerData d = p1;
                     DSGPlayerGameData dsgPlayerGameData = p1GameData;
                 %>
                 <% if (dsgPlayerGameData != null) { %><%@ include file="../ratings.jspf" %><% } %>
                   </font>
               </td>
                <td align="center" bgcolor="#<%=(isGo?"FFFFFF":"000000")%>">
                    <b><font color="<%=(isGo?"black":"white")%>"><%=p2.getName()%>
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
        var game = <%= game.getGame() %>;

        var boardCanvas = document.getElementById("<%=game.getGid()+"board"%>");
        var boardContext = boardCanvas.getContext("2d");

        var gridSize = <%=gridSize%>;
        var indentWidth = (boardCanvas.width/(gridSize+3)) / 2;
        var indentHeight = (boardCanvas.height/(gridSize+3)) / 2;
        // var stepX = boardSize / (gridSize - 1);
        // var stepY = boardSize / (gridSize - 1);
        var stepX = 2*indentWidth;
        var stepY = 2*indentHeight;
        var boardColor;
        var radius = stepX * 95 / 200;
        var boardSize = boardCanvas.width - indentWidth*2;

        var rated = <%= game.isRated()%>;
        


        var drawUntilMove;
        var playedMove;
        var whiteCaptures = 0;
        var blackCaptures = 0;
        var lastMove;

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
                    case 69:
                    case 71:
                    case 73:
                        boardColor = goColor; break;
                    case 75: boardColor = oPenteColor; break;
                    default: boardColor = penteColor; break;
                }
                boardCanvas.addEventListener("click", boardClick, false);
                boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
                drawGrid(boardContext, boardColor, gridSize, false);

                drawUntilMove = moves.length;
                playedMove = -1;
                lastMove = moves[drawUntilMove - 1];

            }

            function boardClick() {
                window.open('/gameServer/tb/game?gid=<%=game.getGid()%>&command=load&mobile',"_self");
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
                    case 69:
                    case 71:
                    case 73:
                        replayGoGame(abstractBoard, movesList, until); break;
                    case 75: replayOPenteGame(abstractBoard, movesList, until); break;
                }
            }

        init();
        replayGame(abstractBoard, moves, moves.length);
        drawGame();
        lastMove = moves[drawUntilMove - 1];
        drawRedDot(lastMove % gridSize, Math.floor(lastMove / gridSize));
        if (game === 63 && moves.length > 1) {
            lastMove = moves[drawUntilMove - 2];
            drawRedDot(lastMove % gridSize, Math.floor(lastMove / gridSize));
        }
    </script>


    
 </td>
</tr>

</table>
