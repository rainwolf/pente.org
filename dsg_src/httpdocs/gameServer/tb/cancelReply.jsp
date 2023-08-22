<%@ page import="org.pente.game.*,
                 org.pente.turnBased.*,
                 org.pente.turnBased.web.TBEmoticon,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*" %>

<%
   TBEmoticon emoticon = new TBEmoticon();
   emoticon.setImageURL("/gameServer/forums/images/emoticons");

   com.jivesoftware.base.FilterChain filters =
      new com.jivesoftware.base.FilterChain(
         null, 1, new com.jivesoftware.base.Filter[]{
         new HTMLFilter(), new URLConverter(), emoticon, new Newline()},
         new long[]{1, 1, 1, 1});
%>

<% pageContext.setAttribute("title", "Cancel Set"); %>
<%@ include file="../begin.jsp" %>


<%
   TBSet set = (TBSet) request.getAttribute("set");
   TBGame game = (TBGame) request.getAttribute("game");

   DSGPlayerData meData = dsgPlayerStorer.loadPlayer(me);
   DSGPlayerData opponent = dsgPlayerStorer.loadPlayer(
      game.getOpponent(meData.getPlayerID()));

   boolean isGo = game.getGame() == GridStateFactory.TB_GO || game.getGame() == GridStateFactory.TB_GO9 || game.getGame() == GridStateFactory.TB_GO13;
   int gridSize = 19;
   if (game.getGame() == GridStateFactory.TB_GO9) {
      gridSize = 9;
   } else if (game.getGame() == GridStateFactory.TB_GO13) {
      gridSize = 13;
   }

%>

<table align="left" width="490" border="0" colspacing="1" colpadding="1">

   <tr>
      <td>
         <h3>Cancel Set</h3>
      </td>
   </tr>


   <tr>
      <td>

         <form name="reply_cancel_form" method="post"
               action="<%= request.getContextPath() %>/gameServer/tb/cancel">
            <input type="hidden" name="sid" value="<%= set.getSetId() %>">
            <input type="hidden" name="gid" value="<%= game.getGid() %>">

            <%= opponent.getName() %> is requesting that this set be cancelled.<br>
            <br>
            Message:
            <% String message = "";
               if (set.getCancelMsg() != null) {
                  message = filters.applyFilters(0, set.getCancelMsg());
               } %>
            <%= message %><br>
            <br>
            Do you want this set to be cancelled?<br>
            <br>
            <button type="submit" name="command" value="Yes"
                    style="background-color:#4CAF50;color: white;font-size: 16px;padding: 5px 15px;"> accept
            </button>
            <button type="submit" name="command" value="No"
                    style="background-color:#f44336;color: white;font-size: 16px;padding: 5px 15px;"> decline
            </button>
            <input type="hidden" name="mobileBrowser" value="">

         </form>
      </td>
   </tr>
   <tr>
      <td>
         <%
            DSGPlayerData p1 = (DSGPlayerData) request.getAttribute("p1");
            DSGPlayerGameData p1GameData = p1.getPlayerGameData(game.getGame());
            DSGPlayerData p2 = (DSGPlayerData) request.getAttribute("p2");
            DSGPlayerGameData p2GameData = p2.getPlayerGameData(game.getGame());
            String myTurn = "false";
            String setStatus = "active";
            String otherGame = "";
            if (set.isTwoGameSet()) {
               otherGame = Long.toString(set.getOtherGame(game.getGid()).getGid());
            }
            String moves = "";
            for (int i = 0; i < game.getNumMoves(); i++) {
               moves += game.getMove(i) + ",";
            }
            if ("".equals(moves)) {
               moves = ",";
            }
            String messages = "";
            String moveNums = "";
            String seqNums = "";
            String dates = "";
            String players = ""; //indicates which seat made message
            Boolean showMessages = new Boolean(false);
            String attach = "true";
            int height = 400;
            int width = 400;
            String version = globalResources.getAppletVersion();
            String cancelRequested = "true";

         %>

      </td>
   </tr>

</table>


<script type='text/javascript'>
   var currentMove = -1;

   function selectMove(newMove) {
      // alert("cell " + newMove);
      var cell = document.getElementById('' + newMove);
      cell.style.background = '#AAF';
      resetAbstractBoard(abstractBoard);
      drawUntilMove = newMove + 1;
      if (game === 63 && drawUntilMove !== 1) {
         drawUntilMove += 1;
      }
      replayGame(abstractBoard, moves, drawUntilMove);
      boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
      boardContext.fill();
      drawGrid(boardContext, boardColor, gridSize, true);
      drawGame();
      lastMove = moves[drawUntilMove - 1];
      drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
      if (game === 63 && moves.length > 1) {
         lastMove = moves[drawUntilMove - 2];
         drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
      }
      if (currentMove !== -1) {
         var cell = document.getElementById('' + currentMove);
         cell.style.background = '#FFF';
      }
      currentMove = newMove;
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
            <canvas id="stone" width="600" height="600"
                    style="position: absolute; left: 0; top: 0; z-index: -1;  "></canvas>
            <canvas id="board" width="600" height="600"
                    style="position: absolute; left: 0; top: 0; z-index: 0;  "></canvas>
            <canvas id="interactionLayer" width="600" height="600"
                    style="position: absolute; left: 0; top: 0; z-index: 1;  "></canvas>
         </div>


         <br>
         <div id="messageBox" style="width:550px; height:auto; background: #cf9;"></div>
         <br>
         <% if (!"false".equals(myTurn)) { %>
         Message: <input type="text" id="message" size="256" style="width:500px;">
         <br>
         <br>
         <label><input id="cycleCheck" name="cycleCheck" type="checkbox" onclick="changeCycle()"/> check this to cycle
            to the next game after submitting</label>
         <br>
         <%
            }
         %>


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

                  <table align="right" border=1 width="250px">
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
                           <b><%=p1.getName()%>
                           </b>
                        </td>
                        <td align="center" bgcolor="#000000">
                           <b><font color="white"><%=p2.getName()%>
                           </font>
                           </b>
                        </td>
                     </tr>

                  </table>
               </td>
            </tr>
            <tr>
               <td>
                  <div style="height:300px;position: relative; overflow:hidden;">
                     <div id="movesTable"
                          style="height:300px; width: 270px; right: -20px; position: absolute; align: right; overflow:auto;">
                        <table align="left" border=1 width="250px">

                              <%
String coordinateLetters[] = {"A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T"};
int row = 0;
for( int i = 0; i < game.getNumMoves(); i++ ) {
    if ((game.getGame() != 63) && (i % 2 == 0)) {
    %>
                           <tr>
                              <td width="10%" align="center"><%= ++row %>
                              </td>
                                 <%
    }
    if ((game.getGame() == 63) && ((i % 4 == 3) || (i == 0))) {
    %>
                           <tr>
                              <td width="10%" align="center"><%= ++row %>
                              </td>
                              <%
                                 }
                              %>
                              <td onclick='selectMove(<%=i%>)' id='<%=i%>' width="45%" align="center">
                                 <%=" " + (game.getMove(i) > -1 && game.getMove(i) < gridSize * gridSize ? coordinateLetters[(game.getMove(i) % gridSize)] + (gridSize - (game.getMove(i) / gridSize)) : "PASS")%>
                                 <%
                                    //      if ((game.getGame() === 63) && (i != 0) && (i + 1 < game.getNumMoves())) {
                                    if ((game.getGame() == 63) && (i != 0)) {
                                       ++i;
                                 %>
                                 - <%="" + coordinateLetters[(game.getMove(i) % gridSize)] + (gridSize - (game.getMove(i) / gridSize))%>
                                 <%
                                    } %>
                              </td>
                              <%
                                 if (game.getNumMoves() == 1) {
                              %>
                              <td></td>
                           </tr>
                              <%
    }
    if ((game.getGame() != 63) && (i % 2 == 1)) {
    %>
            </tr>
               <%
    }
    if ((game.getGame() == 63) && (i % 4 == 2)) {
    %>
   </tr>
   <%
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
      <table align="right" border=1 width="250px">
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
               <%@ include file="../playerLink.jspf" %></a>&nbsp;<% if (dsgPlayerGameData != null) { %>
               <%@ include file="../ratings.jspf" %>
               <% } %>
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
               <%@ include file="../playerLink.jspf" %></a>&nbsp;<% if (dsgPlayerGameData != null) { %>
               <%@ include file="../ratings.jspf" %>
               <% } %>
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
               <a href="javascript:goWH('/gameServer/tb/game?gid=<%= otherGame %>&command=load&mobile');">other game in
                  the set</a>

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


<%--<script type="text/javascript">--%>
<%--window.google_analytics_uacct = "UA-20529582-2";--%>
<%--</script>--%>


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

   var gridSize = <%=gridSize%>;
   var boardCanvas = document.getElementById("board");
   var boardContext = boardCanvas.getContext("2d");
   var indentWidth = (boardCanvas.width / (gridSize + 3)) / 2;
   var indentHeight = (boardCanvas.height / (gridSize + 3)) / 2;
   // var stepX = boardSize / (gridSize - 1);
   // var stepY = boardSize / (gridSize - 1);
   var stepX = 2 * indentWidth;
   var stepY = 2 * indentHeight;
   var boardColor;
   var radius = stepX * 95 / 200;
   var boardSize = boardCanvas.width - indentWidth * 2;


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
   var iRadius = 6 * radius / 4;
   var cycleCheck = <%=((request.getParameter("cycle") != null)?"true":"false")%>;
   var cycleStr = <%=((request.getParameter("cycle") != null)?"\"&cycle\"":"\"\"")%>;


   function init() {
      switch (game) {
         case 51:
            boardColor = penteColor;
            break;
         case 53:
            boardColor = keryoPenteColor;
            break;
         case 55:
            boardColor = gomokuColor;
            break;
         case 57:
            boardColor = dPenteColor;
            break;
         case 59:
            boardColor = gPenteColor;
            break;
         case 61:
            boardColor = poofPenteColor;
            break;
         case 63:
            boardColor = connect6Color;
            break;
         case 65:
            boardColor = boatPenteColor;
            break;
         case 67:
            boardColor = dkeryoPenteColor;
            break;
         case 69:
         case 71:
         case 73:
            boardColor = goColor;
            break;

         default:
            boardColor = penteColor;
            break;
      }
      boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
      interactionContext.clearRect(0, 0, interactionCanvas.width, interactionCanvas.height);
      drawGrid(boardContext, boardColor, gridSize, true);
      // interactionCanvas.addEventListener("click", boardClick, false);
      //
      // interactionCanvas.addEventListener("touchstart", touchStart, false);
      // interactionCanvas.addEventListener("touchend", touchEnd, false);
      // interactionCanvas.addEventListener("touchcancel", touchCancel, false);
      // interactionCanvas.addEventListener("touchleave", touchEnd, false);
      // interactionCanvas.addEventListener("touchmove", touchMove, false);
      interactionContext.scale(2, 2);

      drawUntilMove = moves.length;
      playedMove = -1;
      lastMove = moves[drawUntilMove - 1];

      if (cycleCheck) {
         document.getElementById("cycleCheck").checked = cycleCheck;
      }
   }


   function replayGame(abstractBoard, movesList, until) {
      whiteCaptures = 0;
      blackCaptures = 0;
      switch (game) {
         case 51:
            replayPenteGame(abstractBoard, movesList, until);
            break;
         case 53:
            replayKeryoPenteGame(abstractBoard, movesList, until);
            break;
         case 55:
            replayGomokuGame(abstractBoard, movesList, until);
            break;
         case 57:
            replayPenteGame(abstractBoard, movesList, until);
            break;
         case 59:
            replayGPenteGame(abstractBoard, movesList, until);
            break;
         case 61:
            replayPoofPenteGame(abstractBoard, movesList, until);
            break;
         case 63:
            replayConnect6Game(abstractBoard, movesList, until);
            break;
         case 65:
            replayPenteGame(abstractBoard, movesList, until);
            break;
         case 67:
            replayKeryoPenteGame(abstractBoard, movesList, until);
            break;
         case 69:
         case 71:
         case 73:
            replayGoGame(abstractBoard, movesList, until);
            break;
      }
      // document.getElementById("messageBox").innerHTML = "message";
      if (until <= moves.length) {
         if (messageMoveNums.indexOf(until) !== -1) {
            var encMessage = messages[messageMoveNums.indexOf(until)];
            // var message = encMessage.replace("\\",",");
            var msgr = myName;
            if (((until + 1) % 2) === 0) {
               msgr = p1Name;
            } else {
               msgr = p2Name;
            }
            if (game === 63) {
               if ((Math.floor((until - 1) / 2) % 2) === 0) {
                  msgr = p1Name;
               } else {
                  msgr = p2Name;
               }
            }
            document.getElementById("messageBox").innerHTML = msgr + ": " + messages[messageMoveNums.indexOf(until)].replace("[host]", window.location.host);
         } else {
            document.getElementById("messageBox").innerHTML = "";
         }
      }
   }


   function goBack() {
      if (drawUntilMove > 1) {
         if (game === 63 && drawUntilMove > 1) {
            if ((drawUntilMove % 2) === 1) {
               drawUntilMove = drawUntilMove - 1;
            }
            c6Move1 = -1;
            c6Move2 = -1;
         }
         if (game === 57 && moves.length === 1) {
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
         if (game === 63 && drawUntilMove > 1) {
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
         if (game === 63 && drawUntilMove > 1) {
            drawUntilMove = drawUntilMove + 1;
         }
         boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
         drawGrid(boardContext, boardColor, gridSize, true);
         replayGame(abstractBoard, moves, drawUntilMove);
         drawGame();
         lastMove = moves[drawUntilMove - 1];
         drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
         if (game === 63 && drawUntilMove > 1) {
            lastMove = moves[drawUntilMove - 2];
            drawRedDot(lastMove % 19, Math.floor(lastMove / 19));
            selectMove(drawUntilMove - 2);
         } else {
            selectMove(drawUntilMove - 1);
         }
      }
   }

   function submit() {

      if (playedMove === -1) {
         alert("No move played yet");
      } else if (game === 63 && c6Move2 < 0) {
         alert("You have to place 2 stones for Connect6");
      } else if (game === 63 && c6Move2 > -1) {
         // window.open("http://development.pente.org/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+"&moves="+c6Move1 + "," + c6Move2 +"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
         if ((c6Move1 > -1) && (c6Move1 < 361) && (c6Move2 > -1) && (c6Move2 < 361) && (moves.indexOf(c6Move1) === -1) && (moves.indexOf(c6Move2) === -1) && (c6Move1 !== c6Move2)) {
            window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+
            cycleStr + "&moves=" + c6Move1 + "," + c6Move2 + "&message=" + encodeURIComponent(document.getElementById('message').value), "_self"
         )
            ;
         } else {
            alert("Invalid Connect6 moves detected, please (reload and) try again");
         }
      } else if ((game === 57 || game === 67) && moves.length === 1 && (dPenteMove1 === -1 || dPenteMove2 === -1 || dPenteMove3 === -1)) {
         alert("You have to place 3 stones for D-Pente");
      } else if ((game === 57 || game === 67) && moves.length === 1) {
         if ((dPenteMove1 !== dPenteMove2) && (dPenteMove2 !== dPenteMove3) && (dPenteMove3 !== dPenteMove1)) {
            window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+
            cycleStr + "&moves=" + dPenteMove1 + "," + dPenteMove2 + "," + dPenteMove3 + "&message=" + encodeURIComponent(document.getElementById('message').value), "_self"
         )
            ;
            // window.open("http://development.pente.org/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+"&moves="+dPenteMove1 + "," + dPenteMove2 + "," + dPenteMove3 +"&message="+encodeURIComponent(document.getElementById('message').value),"_self");

         } else {
            alert("Invalid D-Pente moves detected, please (reload and) try again");
         }
      } else {
         // window.open("http://development.pente.org/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+cycleStr+"&moves="+playedMove+"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
         window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+
         cycleStr + "&moves=" + playedMove + "&message=" + encodeURIComponent(document.getElementById('message').value), "_self"
      )
         ;
      }
   }

   function dPentePlayAsP1() {
      if (playedMove === -1) {
         alert("You have to place a stone if you choose to play as P1.");
      } else {
         window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+
         cycleStr + "&moves=1," + playedMove + "&message=" + encodeURIComponent(document.getElementById('message').value), "_self"
      )
         ;
         // window.open("http://development.pente.org/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+"&moves=1,"+playedMove+"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
      }
   }

   function dPentePlayAsP2() {
      if (playedMove > -1) {
         alert("You placed a stone. Remove it first if you choose to play as P2.");
      } else {
         window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+
         cycleStr + "&moves=0&message=" + encodeURIComponent(document.getElementById('message').value), "_self"
      )
         ;
         // window.open("http://development.pente.org/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+"&moves=0&message="+encodeURIComponent(document.getElementById('message').value),"_self");
      }
   }

   function resign() {
      // window.open("http://development.pente.org/gameServer/tb/resign?command=resign&gid="+<%=game.getGid()%>,"_self");
      window.open("/gameServer/tb/resign?command=resign&gid=" +<%=game.getGid()%>, "_self");
   }

   function requestCancel() {
      // window.open("http://development.pente.org/gameServer/tb/cancel?command=request&sid="+<%= set.getSetId() %>+"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
      window.open("/gameServer/tb/cancel?command=confirm&sid="+<%= set.getSetId() %>+
      "&gid="+<%=game.getGid()%>+
      "&message=" + encodeURIComponent(document.getElementById('message').value), "_self"
   )
      ;
      // window.open("/gameServer/tb/resign?command=resign&gid="+<%=game.getGid()%>+"&message="+encodeURIComponent(document.getElementById('message').value),"_self");
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


</td>
</tr>

</table>
<br><br>


<%@ include file="../end.jsp" %>