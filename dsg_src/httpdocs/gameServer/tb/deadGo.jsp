<%@ page import="org.pente.game.*,
                 org.pente.turnBased.*,
                 org.pente.turnBased.web.*,
                 org.pente.gameDatabase.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*,
                 java.text.*,
                 java.util.*" %>

<%@ page contentType="text/html; charset=UTF-8" %>
<%
   String loggedInStr = (String) request.getAttribute("name");
   if (loggedInStr == null) {
      response.sendRedirect("../index.jsp");
   } %>
<%
   com.jivesoftware.base.FilterChain filters =
      new com.jivesoftware.base.FilterChain(
         null, 1, new com.jivesoftware.base.Filter[]{
         new HTMLFilter(), new URLConverter(), new TBEmoticon(), new Newline()},
         new long[]{1, 1, 1, 1});

   TBGame game = (TBGame) request.getAttribute("game");
   TBSet set = game.getTbSet();

   DSGPlayerData p1 = (DSGPlayerData) request.getAttribute("p1");
   DSGPlayerGameData p1GameData = p1.getPlayerGameData(game.getGame());
   DSGPlayerData p2 = (DSGPlayerData) request.getAttribute("p2");
   DSGPlayerGameData p2GameData = p2.getPlayerGameData(game.getGame());
   String myTurn = (String) request.getAttribute("myTurn");
   if (myTurn == null) myTurn = "false";
   String setStatus = "active";
   if (set.isDraw()) {
      setStatus = "draw";
   } else if (set.isCancelled()) {
      setStatus = "cancelled";
   } else if (set.isCompleted()) {
      long wPid = set.getWinnerPid();
      if (wPid == p1.getPlayerID()) {
         setStatus = p1.getName() + " wins";
      } else if (wPid == p2.getPlayerID()) {
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
   if (moves.length() == 0) {
      moves = ",";
   }
// if (loggedInStr.equals("rainwolf") || loggedInStr.equals(p1.getName()) || loggedInStr.equals(p2.getName())) {
   if (loggedInStr.equals(p1.getName()) || loggedInStr.equals(p2.getName())) {
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
         } else {
            players += "2,";
         }
      }
   }
   String tmpMsgs = "";
   if (!"".equals(messages)) {
//    tmpMsgs = messages.substring(0, messages.length() - 1);
      tmpMsgs = messages.substring(0, messages.length() - 1).replace("\\1", ",").replace("\\2", "'");
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
      try {
         height = Integer.parseInt(request.getParameter("h"));
      } catch (NumberFormatException n) {
      }
   }
   if (request.getParameter("w") != null) {
      try {
         width = Integer.parseInt(request.getParameter("w"));
      } catch (NumberFormatException n) {
      }
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
<%@ include file="../begin.jsp" %>
<%
   String version = globalResources.getAppletVersion();
   DSGPlayerData meData = dsgPlayerStorer.loadPlayer(me);

   String cancelRequested = "false";
%>
<% if (meData.showAds()) { %>
<center>
   <div id="senseReplace" style="width:728px;height:90px;" top="50%"></div>
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


            function changeCycle() {
               if (document.getElementById('cycleCheck').checked) {
                  cycleStr = "&cycle";
               } else {
                  cycleStr = "";
               }
            }

            function changeHidden() {
               if (document.getElementById('hideCheck').checked) {
                  hideStr = "&hide=yes";
               } else {
                  hideStr = "&hide=no";
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
                  <% if (!"false".equals(myTurn)) { %>
                  <div>
                     <b><font color="#8b0000">Your opponent has made a pass as well, mark the stones you think are dead
                        and/or press submit.</font> </b>
                  </div>
                  <% } %>
                  <br>
                  <br>
                  <div id="scoreBox" style="width:550px; height:auto; "></div>
                  <br>
                  <br>
                  <div id="messageBox" style="width:550px; height:auto; background: #cf9;"></div>
                  <br>
                  <% if (!"false".equals(myTurn)) { %>
                  Message: <input type="text" id="message" size="256" style="width:500px;">
                  <br>
                  <br>
                  <label><input id="cycleCheck" name="cycleCheck" type="checkbox" onclick="changeCycle()"/> check
                     this to cycle to the next game after submitting</label>
                  <% } %>
                  <br>


                  <div class="buttonwrapper" style="margin-top:5px; width:500px;">
                     <% if (!"false".equals(myTurn) && (game.getDPenteState() != 2)) { %>
                     <a class="boldbuttons" href="javascript:submit();"
                        style="margin-right:5px;"><span>Submit</span></a>
                     <%
                     } else if ((game.getPlayer1Pid() == meData.getPlayerID() || game.getPlayer2Pid() == meData.getPlayerID()) && game.isUndoRequested()) {
                     %>
                     <b>Undo requested</b>
                     <%
                     } else if ((game.getPlayer1Pid() == meData.getPlayerID() || game.getPlayer2Pid() == meData.getPlayerID()) && meData.hasPlayerDonated() && game.getState() == TBGame.STATE_ACTIVE && (game.getDPenteState() != 2)) {
                     %>
                     <a class="boldbuttons" href="javascript:requestUndo();"
                        style="margin-right:5px;"><span>Request undo</span></a>
                     <%
                        }
                     %>
                     <% if (game.getDPenteState() == 2 && !"false".equals(myTurn)) { %>
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
                        style="margin-left:100px;"><span>Resign</span></a>
                     <a class="boldbuttons" href="javascript:requestCancel();"
                        style="margin-left:5px;"><span>Cancel Set</span></a>
                     <%
                        }
                     %>
                  </div>
                  <% if (!"false".equals(myTurn) && meData.hasPlayerDonated()) {
                     if (game.canHide(meData.getPlayerID()) || game.canUnHide(meData.getPlayerID())) { %>
                  <br>
                  <label><input id="hideCheck" name="hideCheck" type="checkbox"
                                onclick="changeHidden()" <%=(game.isHidden() ? "checked" : "")%>/> hide this game
                     from public view</label>
                  <%
                        }
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
                                 <td width="45%" align="center" bgcolor="#<%=(!isGo?"FFFFFF":"000000")%>">
                                    <b><font color="<%=(!isGo?"black":"white")%>"><%=p1.getName()%>
                                    </font>
                                    </b>
                                 </td>
                                 <td align="center" bgcolor="#<%=(isGo?"FFFFFF":"000000")%>">
                                    <b><font color="<%=(isGo?"black":"white")%>"><%=p2.getName()%>
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
                                                    for (int i = 0; i < game.getNumMoves(); i++) {
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
                                       <td onclick='selectMove(<%=i%>)' id='<%=i%>' width="45%"
                                           align="center">
                                          <%=" " + (game.getMove(i) > -1 && game.getMove(i) < gridSize * gridSize ? coordinateLetters[(game.getMove(i) % gridSize)] + (gridSize - (game.getMove(i) / gridSize)) : "PASS")%>
                                          <%
                                             //      if ((game.getGame() == 63) && (i != 0) && (i + 1 < game.getNumMoves())) {
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

   <tr>
      <td>
         <table align="right" border=1 width="250px">
            <tr>
               <td width="30%">Player 1
               </td>
               <td>
                  <% if (true) {
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
                  <% }
                  }%>
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
                  <a href="javascript:goWH('/gameServer/tb/game?gid=<%= otherGame %>&command=load&mobile');">other
                     game in the set</a>

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


<%--            <script type="text/javascript">--%>
<%--                window.google_analytics_uacct = "UA-20529582-2";--%>
<%--            </script>--%>


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
   var lastMove;
   var rated = <%= game.isRated()%>;
   var dPenteChoice = <%= game.getDPenteState() == 2 %>;
   var dPenteSwap = <%= game.didDPenteSwap()%>;

   var stoneColor = true;
   var trackingI = -1, trackingJ = -1;
   var iRadius = 6 * radius / 4;
   var cycleCheck = <%=((request.getParameter("cycle") != null)?"true":"false")%>;
   var cycleStr = <%=((request.getParameter("cycle") != null)?"\"&cycle\"":"\"\"")%>;
   var hideStr = "";

   document.onkeydown = leftRight;


   function init() {
      boardColor = goColor;
      boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
      interactionContext.clearRect(0, 0, interactionCanvas.width, interactionCanvas.height);
      drawGrid(boardContext, boardColor, gridSize, true);
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
      if (game === 63) {
         stoneColor = (((moves.length - 1) % 4) === 0);
      } else {
         stoneColor = ((moves.length % 2) === 1);
      }

      var rect = boardCanvas.getBoundingClientRect();
      var offsetX = rect.left;
      var offsetY = rect.top;
      // evt.preventDefault();
      var touch = evt.changedTouches[0];

      var i = Math.floor((touch.clientX - indentWidth + stepX / 2 - offsetX) / stepX);
      var j = Math.floor((touch.clientY - indentHeight + stepY / 2 - offsetY) / stepY);

      var x = touch.clientX - offsetX;
      var y = touch.clientY - offsetY;

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
      var i = Math.floor((touch.clientX - indentWidth + stepX / 2 - offsetX) / stepX);
      var j = Math.floor((touch.clientY - indentHeight + stepY / 2 - offsetY) / stepY);

      var x = touch.clientX - offsetX;
      var y = touch.clientY - offsetY;

      if (i >= 0 && i < 19 && j >= 0 && j < 19) {
         interactionContext.drawImage(boardCanvas, -x / 2, -y / 2);
         if (abstractBoard[i][j] === 0 && active === true) {
            if ((trackingI !== i) || (trackingJ !== j)) {
               drawInteractionStone(i, j, stoneColor);
            }
            interactionContext.drawImage(stoneCanvas, -x / 2, -y / 2);
         }
      } else {
         interactionContext.clearRect(0, 0, interactionCanvas.width, interactionCanvas.height);
      }
   }

   function touchCancel(evt) {
      interactionContext.clearRect(0, 0, interactionCanvas.width, interactionCanvas.height);
   }

   function touchEnd(evt) {
      interactionContext.clearRect(0, 0, interactionCanvas.width, interactionCanvas.height);
      var rect = boardCanvas.getBoundingClientRect();
      var offsetX = rect.left;
      var offsetY = rect.top;
      // evt.preventDefault();
      var touch = evt.changedTouches[0];
      var i = Math.floor((touch.clientX - indentWidth + stepX / 2 - offsetX) / stepX);
      var j = Math.floor((touch.clientY - indentHeight + stepY / 2 - offsetY) / stepY);

      var x = touch.clientX - offsetX;
      var y = touch.clientY - offsetY;

      if (i >= 0 && i < 19 && j >= 0 && j < 19) {
         if (abstractBoard[i][j] === 0 && active === true) {
            var newMoves = moves.slice(0);
            playedMove = j * 19 + i;
            newMoves.push(playedMove);
            resetAbstractBoard(abstractBoard);
            drawUntilMove = newMoves.length;
            replayGame(abstractBoard, newMoves, drawUntilMove);
            boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
            boardContext.fill();
            drawGrid(boardContext, boardColor, gridSize, true);
            drawGame();
            lastMove = moves[moves.length - 1];
         } else {
            playedMove = -1;

            resetAbstractBoard(abstractBoard);
            drawUntilMove = moves.length;
            replayGame(abstractBoard, moves, drawUntilMove);
            boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
            boardContext.fill();
            drawGrid(boardContext, boardColor, gridSize, true);
            drawGame();
            lastMove = moves[moves.length - 1];
         }

         // document.getElementById("messageBox").innerHTML = "Recorded X = " + i + " Y = " + j;
      } else {
         playedMove = -1;
         c6Move2 = -1;
         dPenteMove4 = -1;
         if (game === 63 && moves.length > 1) {
            selectMove(drawUntilMove - 2);
         } else {
            selectMove(drawUntilMove - 1);
         }
      }


   }

   function boardClick(e) {
      var rect = boardCanvas.getBoundingClientRect();
      var offsetX = rect.left;
      var offsetY = rect.top;
      var i = Math.floor((e.clientX - indentWidth - 2 * stepX / 2 - offsetX) / stepX);
      var j = Math.floor((e.clientY - indentHeight - 2 * stepY / 2 - offsetY) / stepY);
      if (i >= 0 && i < gridSize && j >= 0 && j < gridSize) {
         playedMove = j * gridSize + i;
         var p1DeadStones = goDeadStonesByPlayer[1], p2DeadStones = goDeadStonesByPlayer[2];
         if (abstractBoard[i][j] === 0 && active === true) {
            var idx = p1DeadStones.indexOf(playedMove);
            if (idx > -1) {
               p1DeadStones.splice(idx, 1);
               abstractBoard[i][j] = 2;
            }
            idx = p2DeadStones.indexOf(playedMove);
            if (idx > -1) {
               p2DeadStones.splice(idx, 1);
               abstractBoard[i][j] = 1;
            }
         } else if (abstractBoard[i][j] === 2 && active === true) {
            p1DeadStones.push(playedMove);
            abstractBoard[i][j] = 0;
         } else if (abstractBoard[i][j] === 1 && active === true) {
            p2DeadStones.push(playedMove);
            abstractBoard[i][j] = 0;
         }
         drawGrid(boardContext, boardColor, gridSize, true);
         drawGame();
         reDrawTerritories();
      }
   }

   function drawInteractionStone(i, j, color) {
      trackingI = i;
      trackingJ = j;
      stoneContext.clearRect(0, 0, stoneCanvas.width, stoneCanvas.height);
      var centerX = indentWidth + stepX * (i);
      var centerY = indentHeight + stepY * (j);
      stoneContext.save();
      stoneContext.beginPath();
      stoneContext.fillStyle = 'white';
      stoneContext.strokeStyle = "#FFF";
      stoneContext.lineWidth = 2;
      stoneContext.moveTo(0, centerY);
      stoneContext.lineTo(stoneCanvas.width, centerY);
      stoneContext.moveTo(centerX, 0);
      stoneContext.lineTo(centerX, stoneCanvas.height);
      stoneContext.stroke();
      // stoneContext.fill();
      stoneContext.closePath();
      stoneContext.beginPath();
      stoneContext.arc(centerX, centerY, iRadius, 0, Math.PI * 2, true);
      stoneContext.fillStyle = 'red';
      centerX -= iRadius / 8;
      centerY -= iRadius / 8;
      stoneContext.shadowColor = 'DimGray';
      stoneContext.shadowBlur = 1;
      stoneContext.shadowOffsetX = iRadius / 8;
      stoneContext.shadowOffsetY = iRadius / 8;
      if (color) {
         var gradient = stoneContext.createRadialGradient(centerX, centerY, iRadius / 8, centerX, centerY, iRadius);
         gradient.addColorStop(0, 'Grey');
         gradient.addColorStop(1, 'Black');
         stoneContext.fillStyle = gradient;
      } else {
         gradient = stoneContext.createRadialGradient(centerX, centerY, 2 * iRadius / 4, centerX, centerY, iRadius);
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
      if (i >= gridSize || j >= gridSize) {
         return;
      }
      var centerX = indentWidth + stepX * (i);
      var centerY = indentHeight + stepY * (j);
      boardContext.beginPath();
      boardContext.arc(centerX, centerY, stepX / 7, 0, Math.PI * 2, true);
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
      replayGoGame(abstractBoard, movesList, until);

      // document.getElementById("messageBox").innerHTML = "message";
      if (until <= moves.length) {
         if (messageMoveNums.indexOf(until) !== -1) {
            <% if (true) {
               DSGPlayerData d = null;
            %>

            var encMessage = messages[messageMoveNums.indexOf(until)];
            // var message = encMessage.replace("\\",",");
            var msgr = myName;
            if (((until + 1) % 2) === 0) {
               msgr = p1Name;
               <%  d = p1; %>
            } else {
               msgr = p2Name;
               <%  d = p2; %>
            }
            if (game === 63) {
               if ((Math.floor((until - 1) / 2) % 2) === 0) {
                  msgr = p1Name;
               } else {
                  msgr = p2Name;
               }
            }
            document.getElementById("messageBox").innerHTML = "<b>" + msgr + "</b>" + ": " + messages[messageMoveNums.indexOf(until)].replace("[host]", window.location.host);
            <% } %>
         } else {
            document.getElementById("messageBox").innerHTML = "";
         }
      }
   }

   function getDeadStones() {
      var deadStr = '';
      passMove = gridSize * gridSize;
      var p1DeadStones = goDeadStonesByPlayer[1];
      for (var i = 0; i < p1DeadStones.length; i++) {
         if (deadStr === '') {
            deadStr = '' + p1DeadStones[i];
         } else {
            deadStr += ',' + p1DeadStones[i];
         }
      }
      var p2DeadStones = goDeadStonesByPlayer[2];
      for (i = 0; i < p2DeadStones.length; i++) {
         if (deadStr === '') {
            deadStr = '' + p2DeadStones[i];
         } else {
            deadStr += ',' + p2DeadStones[i];
         }
      }
      if (deadStr === '') {
         deadStr = '' + passMove;
      } else {
         deadStr += ',' + passMove;
      }
      return deadStr;
   }


   function submit() {
      window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+
      cycleStr + hideStr + "&moves=" + getDeadStones() + "&message=" + encodeURIComponent(document.getElementById('message').value), "_self"
   )
      ;
   }

   function submitPass() {
      window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+
      cycleStr + hideStr + "&moves=" + (gridSize * gridSize) + "&message=" + encodeURIComponent(document.getElementById('message').value), "_self"
   )
      ;
   }

   function resign() {
      window.open("/gameServer/tb/resign?command=confirm&gid=" +<%=game.getGid()%>, "_self");
   }

   function requestCancel() {
      window.open("/gameServer/tb/cancel?command=confirm&sid="+<%= set.getSetId() %>+
      "&gid="+<%=game.getGid()%>+
      "&message=" + encodeURIComponent(document.getElementById('message').value), "_self"
   )
      ;
   }

   function requestUndo() {
      window.open("/gameServer/tb/game?command=requestUndo&gid=" +<%=game.getGid()%>, "_self");
   }

   init();
   replayGame(abstractBoard, moves, moves.length);
   drawGame();
   reDrawTerritories();
   document.getElementById("movesTable").scrollTop = document.getElementById("movesTable").scrollHeight;
</script>


<%
   int gameId = game.getGame();
   if ("rainwolf".equals(meData.getName()) && gameId != GridStateFactory.CONNECT6 && gameId != GridStateFactory.SPEED_CONNECT6 && gameId != GridStateFactory.TB_CONNECT6) { %>
<tr>
   <td>

      <script type="text/javascript">
         gameStr = "<%=(((gameId % 2) == 0)?"Speed ":"")+GridStateFactory.getGameName(gameId) %>";
         coordinateAlphas = ["A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T"];
      </script>
      <script language="javascript" src="<%= request.getContextPath() %>/gameServer/js/database.js"></script>
      <a class="button" href="javascript:search2();"><span style="color:white">Search the DB</span></a>
      <form name="data_form">
         <input type="hidden" name="response_format"
                value="org.pente.gameDatabase.SimpleHtmlGameStorerSearchResponseFormat">
         <input type="hidden" name="moves" value="K10,L10,">
         <input type="hidden" name="game" value="Pente">
         <input type="hidden" name="results_order" value="2">
         <input type="hidden" name="zippedPartNumParam" value="0">
      </form>
      <form name="filter_options_data">
         <% if ("rainwolf".equals(p1.getName())) { %>
         <input type="hidden" name="<%= SimpleGameStorerSearchRequestFilterFormat.PLAYER_1_NAME_PARAM%>"
                value="">
         <input type="hidden" name="<%= SimpleGameStorerSearchRequestFilterFormat.PLAYER_2_NAME_PARAM%>"
                value="<%=p2.getName()%>">
         <% } else { %>
         <input type="hidden" name="<%= SimpleGameStorerSearchRequestFilterFormat.PLAYER_1_NAME_PARAM%>"
                value="<%=p1.getName()%>">
         <input type="hidden" name="<%= SimpleGameStorerSearchRequestFilterFormat.PLAYER_2_NAME_PARAM%>"
                value="">
         <% } %>
         <input type="hidden" name="<%= SimpleGameStorerSearchRequestFilterFormat.AFTER_DATE_PARAM %>" value="">
         <input type="hidden" name="<%= SimpleGameStorerSearchRequestFilterFormat.BEFORE_DATE_PARAM %>" value="">
         <input type="hidden" name="selectWinner" value="0">
      </form>
      <form name="filter_data">
         <input type="hidden" name="startGameNum" value="0">
      </form>
      &nbsp;
      <br>
      <br>

   </td>
</tr>
<tr>
   <td>

      <%}%>


      <a href="/gameServer/tbpgn.jsp?g=<%= game.getGid() %>">Text (PGN)</a> version.

   </td>
</tr>

</table>
<br><br>

<%@ include file="../end.jsp" %>
