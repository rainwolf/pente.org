<%@ page import="org.pente.game.*, org.pente.turnBased.*" %>

<% pageContext.setAttribute("title", "Play Turn-Based"); %>

<%@ include file="../begin.jsp" %>

<%
   Resources resources = (Resources) application.getAttribute(
      Resources.class.getName());

   String name = (String) request.getAttribute("name");
   DSGPlayerData meData = dsgPlayerStorer.loadPlayer(name);

   TBGameStorer tbGameStorer = resources.getTbGameStorer();
   List<TBSet> waitingSets = tbGameStorer.loadWaitingSets();
   List<TBSet> currentSets = tbGameStorer.loadSets(meData.getPlayerID());
   List<TBSet> invitesTo = new ArrayList<TBSet>();
   List<TBSet> invitesFrom = new ArrayList<TBSet>();
   List<TBGame> myTurn = new ArrayList<TBGame>();
   List<TBGame> oppTurn = new ArrayList<TBGame>();
   Utilities.organizeGames(meData.getPlayerID(), currentSets,
      invitesTo, invitesFrom, myTurn, oppTurn);

   int openTBgames = 0;
   long myPID = meData.getPlayerID();
   for (TBSet s : waitingSets) {
      if (s.getPlayer1Pid() != meData.getPlayerID() &&
         s.getPlayer2Pid() != meData.getPlayerID()) openTBgames++;

      String setGame = GridStateFactory.getGameName(s.getGame1().getGame());
      boolean alreadyPlaying = false, iAmIgnored = false;
      long theirPID = (myPID == s.getPlayer1Pid()) ? s.getPlayer2Pid() : s.getPlayer1Pid();
      for (TBGame g : myTurn) {
         long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
         String myTurnGame = GridStateFactory.getGameName(g.getGame());
         if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
            alreadyPlaying = true;
            break;
         }
         ;
      }
      ;
      for (TBGame g : oppTurn) {
         long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
         String myTurnGame = GridStateFactory.getGameName(g.getGame());
         if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
            alreadyPlaying = true;
            break;
         }
         ;
      }
      ;
      if (alreadyPlaying)
         openTBgames--;

      List<DSGIgnoreData> ignoreData = dsgPlayerStorer.getIgnoreData(theirPID);
      for (Iterator<DSGIgnoreData> it = ignoreData.iterator(); it.hasNext(); ) {
         DSGIgnoreData i = it.next();
         if (i.getIgnorePid() == myPID) {
            if (i.getIgnoreInvite()) {
               iAmIgnored = true;
               break;
            }
         }
      }
      if (iAmIgnored && !alreadyPlaying)
         openTBgames--;
   }


%>
<br>
Here you can find other players who want to play turn-based games.<br>
An open invitation will not show up for you if you are already playing that particular game against the person inviting.
<br>
Games here can be accepted by anyone. To post a game here, click the button
below and do not specify a player to invite.<br>
<br>
<input type="button" value="Create Game"
       onclick="javascript:window.location='/gameServer/tb/new.jsp';"><br>
<br>
<table border="0" cellpadding="0" cellspacing="0" width="100%">
   <tr>
      <td>
         <% if (openTBgames == 0) { %>
         <h3>Open Invitation Games</h3>
         No open invitation games found.<br>
         <% } else { %>

         <table border="0" cellspacing="0" cellpadding="0" width="600">
            <tr bgcolor="<%= textColor2 %>">
               <td colspan="5">
                  <font color="white">
                     <b>Open Invitation Games (<%= openTBgames %>)
                  </font>
               </td>
            </tr>
            <tr>
               <td><b>Game</b></td>
               <td><b>Player</b></td>
               <td><b>Play as</b></td>
               <td><b>Time/Move</b></td>
               <td><b>Rated</b></td>
            </tr>
            <% for (TBSet s : waitingSets) {

               if (s.getPlayer1Pid() == myPID ||
                  s.getPlayer2Pid() == myPID) continue;

               String setGame = GridStateFactory.getGameName(s.getGame1().getGame());
               boolean alreadyPlaying = false, iAmIgnored = false;
               long theirPID = (myPID == s.getPlayer1Pid()) ? s.getPlayer2Pid() : s.getPlayer1Pid();
               for (TBGame g : myTurn) {
                  long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
                  String myTurnGame = GridStateFactory.getGameName(g.getGame());
                  if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
                     alreadyPlaying = true;
                     break;
                  }
                  ;
               }
               ;
               if (alreadyPlaying)
                  continue;
               for (TBGame g : oppTurn) {
                  long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
                  String myTurnGame = GridStateFactory.getGameName(g.getGame());
                  if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
                     alreadyPlaying = true;
                     break;
                  }
                  ;
               }
               ;
               if (alreadyPlaying)
                  continue;

               List<DSGIgnoreData> ignoreData = dsgPlayerStorer.getIgnoreData(theirPID);
               for (Iterator<DSGIgnoreData> it = ignoreData.iterator(); it.hasNext(); ) {
                  DSGIgnoreData i = it.next();
                  if (i.getIgnorePid() == myPID) {
                     if (i.getIgnoreInvite()) {
                        iAmIgnored = true;
                        break;
                     }
                  }
               }
               if (iAmIgnored && !alreadyPlaying)
                  continue;


               String color = null;
               if (s.isTwoGameSet()) {
                  color = "white, black (2 game set)";
               } else if (s.getPlayer1Pid() == 0) {
                  color = "white";
               } else {
                  color = "black";
               }
               DSGPlayerData opp = dsgPlayerStorer.loadPlayer(s.getInviterPid());
               DSGPlayerGameData dsgPlayerGameData = opp.getPlayerGameData(s.getGame1().getGame());
               String oppName = "<a href=/gameServer/profile?viewName=" + opp.getName() +
                  ">" + opp.getName() + "</a>"; %>
            <tr>
               <td><a href="/gameServer/tb/replyInvitation?command=load&sid=<%= s.getSetId() %>">
                  <%= GridStateFactory.getGameName(s.getGame1().getGame()) %>
               </a></td>

               <td><%= oppName %><% if (dsgPlayerGameData != null) { %>
                  <%@ include file="../ratings.jspf" %>
                  <% } %></td>
               <td><%= color %>
               </td>
               <td><%= s.getGame1().getDaysPerMove() %> days</td>
               <td><%= s.getGame1().isRated() ? "Rated" : "Not Rated" %>
               </td>
            </tr>
            <% } %>
         </table>
         <br>
         <% } %>


      </td>
   </tr>

</table>
<br>

<%@ include file="../end.jsp" %>
