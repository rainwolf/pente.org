<%@ page import="org.pente.game.*, org.pente.turnBased.*,
                 org.pente.turnBased.web.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*,
                 java.text.*,
                 java.util.*" %>

<%  
com.jivesoftware.base.FilterChain filters = 
    new com.jivesoftware.base.FilterChain(
        null, 1, new com.jivesoftware.base.Filter[] { 
            new HTMLFilter(), new URLConverter(), new TBEmoticon(), new Newline() }, 
            new long[] { 1, 1, 1, 1 });

TBGame game = (TBGame) request.getAttribute("game");
TBSet set = game.getTbSet();

DSGPlayerData p1 = (DSGPlayerData) request.getAttribute("p1");
DSGPlayerGameData p1GameData = p1.getPlayerGameData(game.getGame());
DSGPlayerData p2 = (DSGPlayerData) request.getAttribute("p2");
DSGPlayerGameData p2GameData = p2.getPlayerGameData(game.getGame());
String myTurn = (String) request.getAttribute("myTurn");
if (myTurn == null) myTurn="false";
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
String messages = "";
String moveNums = "";
String seqNums = "";
String dates = "";
String players = ""; //indicates which seat made message
for (int i = 0; i < game.getNumMoves(); i++) {
    moves += game.getMove(i) + ",";
}
for (TBMessage m : game.getMessages()) {
    // bug in URLConverter
    if (m.getMessage().length() == 1) {
        messages += "\"" + m.getMessage() + "\",";
    } else {
        messages += "\"" + MessageEncoder.encodeMessage(
            filters.applyFilters(0, m.getMessage())) + "\",";
    }
    seqNums += m.getSeqNbr() + ",";
    moveNums += m.getMoveNum() + ",";
    dates += m.getDate().getTime() + ",";
    if (p1.getPlayerID() == m.getPid()) {
        players += "1,";
    }
    else {
        players += "2,";
    }
}
String tmpMsgs = "";
if (!"".equals(messages)) {
//    tmpMsgs = messages.substring(0, messages.length() - 1);
    tmpMsgs = messages.substring(0, messages.length() - 1).replace("\\1",",").replace("\\2","'");
    messages = tmpMsgs;
}
if (!"".equals(moveNums)) {
    tmpMsgs = moveNums.substring(0, moveNums.length() - 1);
    moveNums = tmpMsgs;
}


Boolean showMessages = (Boolean) request.getAttribute("showMessages");
if (showMessages == null) {
    showMessages = new Boolean(true);
}

String attach = (String) request.getAttribute("attach");
if (attach == null) {
    attach = "true";
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
<%@ include file="../begin.jsp" %>
<%
String version = globalResources.getAppletVersion();
DSGPlayerData meData = dsgPlayerStorer.loadPlayer(me);

String cancelRequested="false";
%>
<% if (meData.showAds()) { %>
    <center>
        <div id = "senseReplace" style="width:728px;height:90px;" top="50%"> </div>
        <%@include file="728x90ad.jsp" %>
        <script type="text/javascript">
            sensePage();
        </script>
    </center>
<% } %>



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
 

<!--

<br>
<br>
        <ul>
          <li>Tap anywhere on the board and the game will reset to its final state. If you tapped/clicked on an empty spot a stone was placed, otherwise not.
          </li>
          <li>The cells in the table of moves are clickable.
          </li>
          <li>Report any bugs <a href="http://www.pente.org/gameServer/forums/thread.jspa?forumID=5&threadID=230510&tstart=0">here</a>.
          </li>
          <li>For an optimal experience, start moving your finger as soon as you touch the board.
          </li>
</ul>
 <center> -->

<script type='text/javascript'>
var currentMove = -1;
function selectMove(newMove)
{
                        // alert("cell " + newMove);
   var cell=document.getElementById(''+newMove);
   cell.style.background='#AAF';
      resetAbstractBoard(abstractBoard);
      drawUntilMove = newMove + 1;
      if (game == 63 && drawUntilMove != 1) {
          drawUntilMove += 1;
      }
      replayGame(abstractBoard, moves, drawUntilMove);
      boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
      boardContext.fill();     
      drawGrid(boardContext, boardColor);
      drawGame();
      lastMove = moves[drawUntilMove - 1];
      drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
      if (game == 63 && moves.length > 1) {
          lastMove = moves[drawUntilMove - 2];
          drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
      }
   if(currentMove!=-1) {
       var cell=document.getElementById(''+currentMove);
       cell.style.background='#FFF';
   }
   currentMove=newMove;
}

function changeCycle() {
  if (document.getElementById('cycleCheck').checked) {
    cycleStr = "&cycle";
  } else {
    cycleStr = "";
  }
}
// function IsSelected()
// {
//    return currentRow==-1?false:true;
// }

// function GetSelectedRow()
// {
//    return currentRow;
// }
</script>


<br>
<br>
<table>
<tr>
<td valign="top" width="70%">


      <div style="position: relative; top: 0; height: 600px; ">
        <canvas id="stone" width="600" height="600" style="position: absolute; left: 0; top: 0; z-index: -1;  "></canvas>
        <canvas id="board" width="600" height="600" style="position: absolute; left: 0; top: 0; z-index: 0;  "></canvas>
        <canvas id="interactionLayer" width="600" height="600" style="position: absolute; left: 0; top: 0; z-index: 1;  "></canvas>
      </div>  


    <br>
    <div id="messageBox" style="width:550px; height:auto; background: #cf9;"></div>
    <br>
<% if (!"false".equals(myTurn)) { %>
    Message:  <input type="text" id="message" size="256" style="width:500px;">
    <br>
    <br>
<label><input id="cycleCheck" name="cycleCheck" type="checkbox" onclick="changeCycle()" /> check this to cycle to the next game after submitting</label>
<br>
<%
}
%>


    <div class="buttonwrapper" style="margin-top:5px; width:580px;">
<% if (!"false".equals(myTurn) && (game.getDPenteState() != 2)) { %>
      <a class="boldbuttons" href="javascript:submit();" 
         style="margin-right:5px;"><span>Submit</span></a>
<%
}
%>
<% if (game.getDPenteState() == 2) { %>
      <a class="boldbuttons" href="javascript:dPentePlayAsP1();" 
         style="margin-right:5px;"><span>Play as P1 (white)</span></a>
      <a class="boldbuttons" href="javascript:dPentePlayAsP2();" 
         style="margin-right:5px;"><span>Play as P2 (black)</span></a>
      <a class="boldbuttons" href="javascript:resign();" 
         style="margin-left:50px;"><span>Resign</span></a>
      <a class="boldbuttons" href="javascript:requestCancel();" 
         style="margin-left:5px;"><span>Request Set Cancellation</span></a>
<%
} else if (!"false".equals(myTurn)) { %>
      <a class="boldbuttons" href="javascript:resign();" 
         style="margin-left:235px;"><span>Resign</span></a>
      <a class="boldbuttons" href="javascript:requestCancel();" 
         style="margin-left:5px;"><span>Request Set Cancellation</span></a>
<%
}
%>
    </div>

</td>

<td valign="top">

<table align="right" border=0 width="300px">
<tr>
<td>
<br>
</td>
</tr>
<tr>
   <td>

 <table align="right" border=1  width="250px">
<tr>
<td></td>
   <td align="center" colspan="2">
   <font size="3">
         <b>
  <%= GridStateFactory.getGameName(game.getGame()) %>
  </b>
   </font>
   </td>
</tr>
<tr>
<td width="10%"></td>
   <td width="45%" align="center">
 <b>   <%=p1.getName()%> </b>
   </td>
   <td align="center" bgcolor="#000000">
 <b><font color="white">   <%=p2.getName()%></font>
</b>
   </td>
</tr>

</table>
</td>
</tr>
<tr>
<td>
<div style="height:300px;position: relative; overflow:hidden;">
<div id="movesTable" style="height:300px; width: 270px; right: -20px; position: absolute; align: right; overflow:auto;">
<table align="left" border=1  width="250px">

<% 
String coordinateLetters[] = {"A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T"};
int row = 0;
for( int i = 0; i < game.getNumMoves(); i++ ) {
    if ((game.getGame() != 63) && (i % 2 == 0)) {
    %> <tr> <td width="10%" align="center"> <%= ++row %> </td> <%
    }
    if ((game.getGame() == 63) && ((i % 4 == 3) || (i == 0))) {
    %> <tr> <td width="10%" align="center"> <%= ++row %> </td> <%
    }
    %> 
    <td onclick='selectMove(<%=i%>)' id='<%=i%>' width="45%" align="center">
    <%=" " + coordinateLetters[(game.getMove(i) % 19)] + (19 - (game.getMove(i) / 19))%>
    <% 
//      if ((game.getGame() == 63) && (i != 0) && (i + 1 < game.getNumMoves())) {
      if ((game.getGame() == 63) && (i != 0)) {
        ++i;
        %>
        - <%="" + coordinateLetters[(game.getMove(i) % 19)] + (19 - (game.getMove(i) / 19))%>
        <%
    } %>
    </td>
    <%
    if (game.getNumMoves() == 1) {
    %> <td></td></tr> <%
    }
    if ((game.getGame() != 63) && (i % 2 == 1)) {
    %> </tr> <%
    }
    if ((game.getGame() == 63) && (i % 4 == 2)) {
    %> </tr> <%
    }
}
%>
</table>
</div>
</div>
</td>
</tr>
<% if (game.getDPenteState() != 2) { %>
<tr>
<td>
<table align="right" border=1  width="250px">
<tr>
   <td width="50%" onclick="goBack()" align="center">
   <br>
   back
   <br>
   <br>
   </td>
   <td onclick="goForward()" align="center">
   <br>
   forward
   <br>
   <br>
   </td>
</tr>
</table>
</td>
</tr>

<%
}
%>
<tr>
<td>
<table align="right" border=1 width="250px">
<tr>
   <td width="30%">Player 1
   </td>
   <td>
   <%
         DSGPlayerData d = p1;
         DSGPlayerGameData dsgPlayerGameData = p1GameData;
     %>
     <%@ include file="../playerLink.jspf" %></a>&nbsp;<% if (dsgPlayerGameData != null) { %><%@ include file="../ratings.jspf" %><% } %>
   </td>
</tr>
<tr>
   <td>Player 2
   </td>
   <td>
   <%
         d = p2;
         dsgPlayerGameData = p2GameData;
     %>
     <%@ include file="../playerLink.jspf" %></a>&nbsp;<% if (dsgPlayerGameData != null) { %><%@ include file="../ratings.jspf" %><% } %>
   </td>
</tr>
<tr>
   <td>
   Timer
   </td>
   <td>
<%= game.getDaysPerMove() %> days/move
   </td>
</tr>
<tr>
   <td>
   Timeout
   </td>
   <td>
<%
    DateFormat profileDateFormat = null;
    TimeZone playerTimeZone = null;
    TimeZone tz = TimeZone.getTimeZone(meData.getTimezone());
    profileDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm z");
    profileDateFormat.setTimeZone(tz);
%>
    <%= profileDateFormat.format(game.getTimeoutDate().getTime()) %>
   </td>
</tr>
<% if (!"".equals(otherGame)) {
  %>

<tr>
   <td>
   </td>
   <td>
        <script type="text/javascript" src="/gameServer/js/go.js"></script>
         <a href="javascript:goWH('/gameServer/tb/game?gid=<%= otherGame %>&command=load&mobile');">other game in the set</a> 

   </td>
</tr>

<%}%>

</table>


   </td>
</tr>
</table>



</td>
</tr>  
</table>
<!-- </center> -->


    <br>
    <br>


<script type="text/javascript">
window.google_analytics_uacct = "UA-20529582-2";
</script>


    <script src="/gameServer/tb/gameScript.js"></script>

    <script type="text/javascript">
        var moves = [<%=moves.substring(0, moves.length() - 1)%>];
        var messages = [<%=messages%>];
        var messageMoveNums = [<%=moveNums%>];
        var active = <%=!"false".equals(myTurn)%>;
        var game = <%= game.getGame() %>;
        var myName = "<%= me %>";
        var p1Name = "<%=p1.getName()%>";
        var p2Name = "<%=p2.getName()%>";
        var opponentName = "<%= (me.equals(p1.getName())?p2.getName():p1.getName()) %>";
        var iAmP1 = <%=me.equals(p1.getName())%>;

        var boardSize = 500;
        var boardCanvas = document.getElementById("board");
        var boardContext = boardCanvas.getContext("2d");
        var stoneCanvas = document.getElementById("stone");
        var stoneContext = stone.getContext("2d");
        var interactionCanvas = document.getElementById("interactionLayer");
        var interactionContext = interactionCanvas.getContext("2d");
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
        var cycleCheck = <%=((request.getParameter("cycle") != null)?"true":"false")%>;
        var cycleStr = <%=((request.getParameter("cycle") != null)?"\"&cycle\"":"\"\"")%>;



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
                    default: boardColor = penteColor; break;
                }
                boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
                interactionContext.clearRect(0, 0, interactionCanvas.width, interactionCanvas.height);
                drawGrid(boardContext, boardColor);
                interactionCanvas.addEventListener("click", boardClick, false);

                interactionCanvas.addEventListener("touchstart", touchStart, false);
                interactionCanvas.addEventListener("touchend", touchEnd, false);
                interactionCanvas.addEventListener("touchcancel", touchCancel, false);
                interactionCanvas.addEventListener("touchleave", touchEnd, false);
                interactionCanvas.addEventListener("touchmove", touchMove, false);
                interactionContext.scale(2, 2);

                drawUntilMove = moves.length;
                playedMove = -1;
                lastMove = moves[drawUntilMove - 1];

                if (cycleCheck) {
                  document.getElementById("cycleCheck").checked = cycleCheck;
                }
            }

function touchStart(evt) {
  if (game == 63) {
    stoneColor = (((moves.length - 1) % 4) == 0);
  } else {
    stoneColor = ((moves.length % 2) == 1);
  }
  if ((drawUntilMove != moves.length)) {
      var newMoves = moves.slice(0);
      if (game == 63) {
          if (c6Move1 > -1) {
              newMoves.push(c6Move1);
          }
      }
      if (game == 57 && moves.length == 1) {
          if (dPenteMove1 == -1) {
          } else if (dPenteMove2 == -1) {
              newMoves.push(dPenteMove1);
          } else {
              newMoves.push(dPenteMove1);
              newMoves.push(dPenteMove2);
          }
      } 
      if (game == 63) {
        stoneColor = (((moves.length - 1) % 4) == 0);
      } else {
        stoneColor = ((newMoves.length % 2) == 1);
      }
      resetAbstractBoard(abstractBoard);
      drawUntilMove = newMoves.length;
      replayGame(abstractBoard, newMoves, drawUntilMove);
      boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
      boardContext.fill();     
      drawGrid(boardContext, boardColor);
      drawGame();
      lastMove = moves[moves.length - 1];
      drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
      if (game == 63 && moves.length > 1) {
          lastMove = moves[moves.length - 2];
          drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
      }
  }

  var rect = boardCanvas.getBoundingClientRect();
  var offsetX = rect.left;
  var offsetY = rect.top;
  // evt.preventDefault();
  var touch = evt.changedTouches[0];

  var i = Math.floor((touch.clientX - indentWidth + stepX/2 - offsetX) / stepX);
  var j = Math.floor((touch.clientY - indentHeight + stepY/2 - offsetY) / stepY);

  var x = touch.clientX - offsetX;
  var y = touch.clientY - offsetY;

  // if (i >= 0 && i < 19 && j >= 0 && j < 19) {
  //   evt.preventDefault();
  //   interactionContext.drawImage(boardCanvas, -x/2 , -y/2);
  // } else {
  //   interactionContext.clearRect(0, 0, interactionCanvas.width, interactionCanvas.height);
  // }





  // document.getElementById("messageBox").innerHTML = "Started X = " + (touch.clientX - offsetX) + " Y = " + (touch.clientY - offsetY);

}
function touchMove(evt) {
  var rect = boardCanvas.getBoundingClientRect();
  var offsetX = rect.left;
  var offsetY = rect.top;
  if (evt.touches.length > 1) {
    return;
  }
    evt.preventDefault();
  var touch = evt.changedTouches[0];
  var i = Math.floor((touch.clientX - indentWidth + stepX/2 - offsetX) / stepX);
  var j = Math.floor((touch.clientY - indentHeight + stepY/2 - offsetY) / stepY);

  var x = touch.clientX - offsetX;
  var y = touch.clientY - offsetY;

  if (i >= 0 && i < 19 && j >= 0 && j < 19) {
    interactionContext.drawImage(boardCanvas, -x/2 , -y/2);
    if (abstractBoard[i][j] == 0 && active == true) {
      if ((trackingI != i) || (trackingJ != j)) {
        drawInteractionStone(i, j, stoneColor);
      }
      interactionContext.drawImage(stoneCanvas, -x/2 , -y/2);
    }
  } else {
    interactionContext.clearRect(0, 0, interactionCanvas.width, interactionCanvas.height);
  }




  // document.getElementById("messageBox").innerHTML = "Moving X = " + i + " Y = " + j;

}
function touchCancel(evt) {
  interactionContext.clearRect(0, 0, interactionCanvas.width, interactionCanvas.height);
  playedMove = -1;
  c6Move2 = -1;
  dPenteMove3 = -1;
  if (game == 63 && moves.length > 1) {
      selectMove(drawUntilMove - 2);
  } else {
      selectMove(drawUntilMove - 1);
  }
}
function touchEnd(evt) {
  interactionContext.clearRect(0, 0, interactionCanvas.width, interactionCanvas.height);
  var rect = boardCanvas.getBoundingClientRect();
  var offsetX = rect.left;
  var offsetY = rect.top;
  // evt.preventDefault();
  var touch = evt.changedTouches[0];
  var i = Math.floor((touch.clientX - indentWidth + stepX/2 - offsetX) / stepX);
  var j = Math.floor((touch.clientY - indentHeight + stepY/2 - offsetY) / stepY);

  var x = touch.clientX - offsetX;
  var y = touch.clientY - offsetY;

  if (i >= 0 && i < 19 && j >= 0 && j < 19) {
    if (abstractBoard[i][j] == 0 && active == true) {
        var newMoves = moves.slice(0);
        playedMove = j*19+i;
        if (game == 63) {
            if (c6Move1 > -1) {
                newMoves.push(c6Move1);
                c6Move2 = playedMove;
            } else {
                c6Move1 = playedMove;
            }
        }
        if (game == 57 && moves.length == 1) {
            if (dPenteMove1 == -1) {
                dPenteMove1 = playedMove;
            } else if (dPenteMove2 == -1) {
                newMoves.push(dPenteMove1);
                dPenteMove2 = playedMove;
            } else {
                newMoves.push(dPenteMove1);
                newMoves.push(dPenteMove2);
                dPenteMove3 = playedMove;
            }
        } 
        newMoves.push(playedMove);
        resetAbstractBoard(abstractBoard);
        drawUntilMove = newMoves.length;
        replayGame(abstractBoard, newMoves, drawUntilMove);
        boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
        boardContext.fill();     
        drawGrid(boardContext, boardColor);
        drawGame();
        lastMove = moves[moves.length - 1];
        drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
        if (game == 63 && moves.length > 1) {
            lastMove = moves[moves.length - 2];
            drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
        }
    } else {
        playedMove = -1;
        c6Move1 = -1;
        c6Move2 = -1;
        dPenteMove1 = -1;
        dPenteMove2 = -1;
        dPenteMove3 = -1;

        resetAbstractBoard(abstractBoard);
        drawUntilMove = moves.length;
        replayGame(abstractBoard, moves, drawUntilMove);
        boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
        boardContext.fill();     
        drawGrid(boardContext, boardColor);
        drawGame();
        lastMove = moves[moves.length - 1];
        drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
        if (game == 63 && moves.length > 1) {
            lastMove = moves[moves.length - 2];
            drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
        }
        if (game == 63 && moves.length > 1) {
            selectMove(drawUntilMove - 2);
        } else {
            selectMove(drawUntilMove - 1);
        }
    }

    // document.getElementById("messageBox").innerHTML = "Recorded X = " + i + " Y = " + j;
  } else {
    playedMove = -1;
    c6Move2 = -1;
    dPenteMove3 = -1;
    if (game == 63 && moves.length > 1) {
        selectMove(drawUntilMove - 2);
    } else {
        selectMove(drawUntilMove - 1);
    }
  }


}

            function boardClick(e) {
               if(currentMove != -1) {
                   var cell=document.getElementById(''+currentMove);
                   cell.style.background='#FFF';
               }
               currentMove = -1;
                var rect = boardCanvas.getBoundingClientRect();
                var offsetX = rect.left;
                var offsetY = rect.top;
                var i = Math.floor((e.clientX - indentWidth + stepX/2 - offsetX) / stepX);
                var j = Math.floor((e.clientY - indentHeight + stepY/2 - offsetY) / stepY);
                if (i >= 0 && i < 19 && j >= 0 && j < 19) {
                    if ((drawUntilMove != moves.length)) {
                        resetAbstractBoard(abstractBoard);
                        drawUntilMove = moves.length;
                        replayGame(abstractBoard, moves, drawUntilMove);
                        boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
                        boardContext.fill();     
                        drawGrid(boardContext, boardColor);
                        drawGame();
                        lastMove = moves[moves.length - 1];
                        drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
                        if (game == 63 && moves.length > 1) {
                            lastMove = moves[moves.length - 2];
                            drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
                        }
                        document.getElementById("movesTable").scrollTop = document.getElementById("movesTable").scrollHeight;
                    }
                    if (abstractBoard[i][j] == 0 && active == true) {
                        var newMoves = moves.slice(0);
                        playedMove = j*19+i;
                        if (game == 63) {
                            if (c6Move1 > -1) {
                                newMoves.push(c6Move1);
                                c6Move2 = playedMove;
                            } else {
                                c6Move1 = playedMove;
                            }
                        }
                        if (game == 57 && moves.length == 1) {
                            if (dPenteMove1 == -1) {
                                dPenteMove1 = playedMove;
                            } else if (dPenteMove2 == -1) {
                                newMoves.push(dPenteMove1);
                                dPenteMove2 = playedMove;
                            } else {
                                newMoves.push(dPenteMove1);
                                newMoves.push(dPenteMove2);
                                dPenteMove3 = playedMove;
                            }
                        } 
                        newMoves.push(playedMove);
                        resetAbstractBoard(abstractBoard);
                        drawUntilMove = newMoves.length;
                        replayGame(abstractBoard, newMoves, drawUntilMove);
                        boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
                        boardContext.fill();     
                        drawGrid(boardContext, boardColor);
                        drawGame();
                        lastMove = moves[moves.length - 1];
                        drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
                        if (game == 63 && moves.length > 1) {
                            lastMove = moves[moves.length - 2];
                            drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
                        }
                    } else {
                        playedMove = -1;
                        c6Move1 = -1;
                        c6Move2 = -1;
                        dPenteMove1 = -1;
                        dPenteMove2 = -1;
                        dPenteMove3 = -1;
                        if (game == 63 && moves.length > 1) {
                            selectMove(drawUntilMove - 2);
                        } else {
                            selectMove(drawUntilMove - 1);
                        }
                    }
                }
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
                boardContext.lineWidth=0.5;
                for (var i = 0; i < 19; i++) {
                    boardContext.moveTo(indentWidth + i*stepX, indentHeight);
                    boardContext.lineTo(indentWidth + i*stepX, indentHeight + boardSize);
                    boardContext.fillText(coordinateLetters[i], indentWidth + i*stepX - 2, indentHeight - 5);
                    boardContext.fillText(coordinateLetters[i], indentWidth + i*stepX - 2, boardSize + indentHeight + 12);
                }
                for (var i = 0; i < 19; i++) {
                    boardContext.moveTo(indentWidth, indentHeight + i*stepY);
                    boardContext.lineTo(indentWidth + boardSize, indentHeight + i*stepY);
                    boardContext.fillText("" + (19 - i), indentWidth - 15, indentHeight + i*stepX + 3);
                    boardContext.fillText("" + (19 - i), boardSize + indentWidth + 6, indentHeight + i*stepX + 3);
                }
                // boardContext.strokeStyle = "#FFFFFF";
                boardContext.stroke();
                boardContext.closePath();
                boardContext.beginPath();
                boardContext.arc(indentWidth + 9*stepX, indentHeight + 9*stepY, stepX / 5, 0, Math.PI*2, true); 
                boardContext.stroke();
                boardContext.closePath();
                boardContext.beginPath();
                boardContext.arc(indentWidth + 6*stepX, indentHeight + 6*stepY, stepX / 5, 0, Math.PI*2, true); 
                boardContext.stroke();
                boardContext.closePath();
                boardContext.beginPath();
                boardContext.arc(indentWidth + 6*stepX, indentHeight + 12*stepY, stepX / 5, 0, Math.PI*2, true); 
                boardContext.stroke();
                boardContext.closePath();
                boardContext.beginPath();
                boardContext.arc(indentWidth + 12*stepX, indentHeight + 6*stepY, stepX / 5, 0, Math.PI*2, true); 
                boardContext.stroke();
                boardContext.closePath();
                boardContext.beginPath();
                boardContext.arc(indentWidth + 12*stepX, indentHeight + 12*stepY, stepX / 5, 0, Math.PI*2, true); 
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
            function drawInteractionStone(i, j, color) {
              trackingI = i;
              trackingJ = j;
                stoneContext.clearRect(0, 0, stoneCanvas.width, stoneCanvas.height);
                var centerX = indentWidth + stepX*(i);
                var centerY = indentHeight + stepY*(j);
                stoneContext.save();
                stoneContext.beginPath();
                stoneContext.fillStyle='white';
                stoneContext.strokeStyle = "#FFF";
                stoneContext.lineWidth=2;
                stoneContext.moveTo(0, centerY);
                stoneContext.lineTo(stoneCanvas.width, centerY);
                stoneContext.moveTo(centerX, 0);
                stoneContext.lineTo(centerX, stoneCanvas.height);
                stoneContext.stroke();
                // stoneContext.fill();
                stoneContext.closePath();
                stoneContext.beginPath();
                stoneContext.arc(centerX, centerY, iRadius , 0, Math.PI*2, true); 
                if (color == true) {
                    stoneContext.fillStyle = 'black';
                } else {
                    stoneContext.fillStyle = 'white';
                }
                centerX -= iRadius/8;
                centerY -= iRadius/8;
                stoneContext.shadowColor = 'DimGray';
                stoneContext.shadowBlur = 1;
                stoneContext.shadowOffsetX = iRadius/8;
                stoneContext.shadowOffsetY = iRadius/8;
                if (color) {
                    var gradient = stoneContext.createRadialGradient(centerX, centerY, iRadius / 8, centerX, centerY, iRadius);
                    gradient.addColorStop(0, 'Grey');
                    gradient.addColorStop(1, 'Black');
                    stoneContext.fillStyle = gradient; 
                } else {
                    var gradient = stoneContext.createRadialGradient(centerX, centerY, 2*iRadius / 4, centerX, centerY, iRadius);
                    gradient.addColorStop(0, 'White');
                    gradient.addColorStop(1, 'Gainsboro');
                    stoneContext.fillStyle = gradient; 
                }
                stoneContext.fill();
                // boardContext.lineWidth = 5;
                // boardContext.strokeStyle = '#003300';
                // boardContext.stroke();
                stoneContext.closePath();
              stoneContext.restore();
            }
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
                }
                    // document.getElementById("messageBox").innerHTML = "message";
                if (until <= moves.length) {
                    if (messageMoveNums.indexOf(until) != -1) {
                        var encMessage = messages[messageMoveNums.indexOf(until)];
                        // var message = encMessage.replace("\\",",");
                        var msgr = myName;
                        if (((until + 1) % 2) == 0) {
                            msgr = p1Name;
                        } else {
                            msgr = p2Name;
                        }
                        if (game == 63) {
                            if ((Math.floor((until - 1)/2) % 2) == 0) {
                                msgr = p1Name;
                            } else {
                                msgr = p2Name;
                            }
                        }
                        document.getElementById("messageBox").innerHTML = msgr + ": " + messages[messageMoveNums.indexOf(until)].replace("[host]",window.location.host);
                    } else {
                        document.getElementById("messageBox").innerHTML = "";
                    }
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
                    drawGrid(boardContext, boardColor);
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
                    drawGrid(boardContext, boardColor);
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

            function submit () {

                if (playedMove == -1) {
                    alert("No move played yet");
                } else if (game == 63 && c6Move2 < 0) {
                    alert("You have to place 2 stones for Connect6");
                } else if (game == 63 && c6Move2 > -1) {
                    // window.open("http://development.pente.org/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+"&moves="+c6Move1 + "," + c6Move2 +"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
                    if ((c6Move1 > -1) && (c6Move1 < 361) && (c6Move2 > -1) && (c6Move2 < 361) && (moves.indexOf(c6Move1) == -1) && (moves.indexOf(c6Move2) == -1) && (c6Move1 != c6Move2)) {
                      window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+cycleStr+"&moves="+c6Move1 + "," + c6Move2 +"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
                    } else {
                        alert("Invalid Connect6 moves detected, please (reload and) try again");
                    }
                } else if (game == 57 && moves.length == 1 && (dPenteMove1 == -1 || dPenteMove2 == -1 || dPenteMove3 == -1)) {
                    alert("You have to place 3 stones for D-Pente");
                } else if (game == 57 && moves.length == 1) {
                  if ((dPenteMove1 != dPenteMove2) && (dPenteMove2 != dPenteMove3) && (dPenteMove3 != dPenteMove1)) {
                    window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+cycleStr+"&moves="+dPenteMove1 + "," + dPenteMove2 + "," + dPenteMove3 +"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
                    // window.open("http://development.pente.org/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+"&moves="+dPenteMove1 + "," + dPenteMove2 + "," + dPenteMove3 +"&message="+encodeURIComponent(document.getElementById('message').value),"_self");

                  } else {
                        alert("Invalid D-Pente moves detected, please (reload and) try again");
                  }
                } else {
                    // window.open("http://development.pente.org/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+cycleStr+"&moves="+playedMove+"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
                    window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+cycleStr+"&moves="+playedMove+"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
                }
            }
            function dPentePlayAsP1() {
                if (playedMove == -1) {
                    alert("You have to place a stone if you choose to play as P1.");
                } else {
                    window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+cycleStr+"&moves=1,"+playedMove+"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
                    // window.open("http://development.pente.org/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+"&moves=1,"+playedMove+"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
                }
            }
            function dPentePlayAsP2() {
                if (playedMove > -1) {
                    alert("You placed a stone. Remove it first if you choose to play as P2.");
                } else {
                    window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+cycleStr+"&moves=0&message="+encodeURIComponent(document.getElementById('message').value),"_self");
                    // window.open("http://development.pente.org/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+"&moves=0&message="+encodeURIComponent(document.getElementById('message').value),"_self");
                }
            }
            function resign () {
                // window.open("http://development.pente.org/gameServer/tb/resign?command=resign&gid="+<%=game.getGid()%>,"_self");
                window.open("/gameServer/tb/resign?command=resign&gid="+<%=game.getGid()%>,"_self");
            }
            function requestCancel () {
                // window.open("http://development.pente.org/gameServer/tb/cancel?command=request&sid="+<%= set.getSetId() %>+"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
                window.open("/gameServer/tb/cancel?command=confirm&sid="+<%= set.getSetId() %>+"&gid="+<%=game.getGid()%>+"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
                // window.open("/gameServer/tb/resign?command=resign&gid="+<%=game.getGid()%>+"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
            }
        init();
        replayGame(abstractBoard, moves, moves.length);
        drawGame();
        lastMove = moves[drawUntilMove - 1];
        drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
        if (game == 63 && moves.length > 1) {
            lastMove = moves[drawUntilMove - 2];
            drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
            selectMove(drawUntilMove - 2);
        } else {
          selectMove(drawUntilMove - 1);
        }
        document.getElementById("movesTable").scrollTop = document.getElementById("movesTable").scrollHeight;
    </script>









<a href="/gameServer/tbpgn.jsp?g=<%= game.getGid() %>">Text (PGN)</a> version.
    
 </td>
</tr>

</table>
<br><br>

<%@ include file="../end.jsp" %>
