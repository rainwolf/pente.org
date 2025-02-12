<%@ page import="org.pente.game.*, org.pente.turnBased.*, org.pente.kingOfTheHill.*" %>

<% pageContext.setAttribute("title", "Play Turn-Based"); %>

<%@ include file="../begin.jsp" %>

<%
   Resources resources = (Resources) application.getAttribute(
      Resources.class.getName());

   String name = (String) request.getAttribute("name");
   DSGPlayerData meData = dsgPlayerStorer.loadPlayer(name);

   TBGameStorer tbGameStorer = resources.getTbGameStorer();
// List<TBSet> waitingSets = tbGameStorer.loadWaitingSets();
   List<TBSet> waitingSets = ((CacheTBStorer) tbGameStorer).getWaitingSets();
   List<TBSet> currentSets = tbGameStorer.loadSets(meData.getPlayerID());
   List<TBSet> invitesTo = new ArrayList<TBSet>();
   List<TBSet> invitesFrom = new ArrayList<TBSet>();
   List<TBGame> myTurn = new ArrayList<TBGame>();
   List<TBGame> oppTurn = new ArrayList<TBGame>();
   Utilities.organizeGames(meData.getPlayerID(), currentSets,
      invitesTo, invitesFrom, myTurn, oppTurn);
   final CacheKOTHStorer kothStorer = resources.getKOTHStorer();

// int myPenteRating = 0;
// int myKeryoPenteRating = 0;
// int myGomokuRating = 0;
// int myDPenteRating = 0;
// int myGPenteRating = 0;
// int myPoofPenteRating = 0;
// int myConnect6Rating = 0;
// int myBoatPenteRating = 0;

   int openTBgames = 0;
//int concurrentPlayLimit = 2;
   long myPID = meData.getPlayerID();

   for (Iterator<TBSet> iterator = waitingSets.iterator(); iterator.hasNext(); ) {
      TBSet s = iterator.next();

      if ((s.getPlayer1Pid() != myPID && s.getPlayer2Pid() != myPID) || "rainwolf".equals(name)) {
         openTBgames++;
      } else {
         iterator.remove();
         continue;
      }

//    int nrGamesPlaying = 0;
      boolean alreadyPlaying = false, iAmIgnored = false;
      long theirPID = (0 == s.getPlayer1Pid()) ? s.getPlayer2Pid() : s.getPlayer1Pid();
      if (s.getInvitationRestriction() == TBSet.ANYONE_NOTPLAYING) {
         String setGame = GridStateFactory.getGameName(s.getGame1().getGame());
         for (TBGame g : myTurn) {
            long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
            String myTurnGame = GridStateFactory.getGameName(g.getGame());
            if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
//                nrGamesPlaying++;
//                if (nrGamesPlaying > concurrentPlayLimit) {
               alreadyPlaying = true;
               break;
//                }
            }
         }
         if (!alreadyPlaying) {
            for (TBGame g : oppTurn) {
               long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
               String myTurnGame = GridStateFactory.getGameName(g.getGame());
               if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
//                    nrGamesPlaying++;
//                    if (nrGamesPlaying > concurrentPlayLimit) {
                  alreadyPlaying = true;
                  break;
//                    }
               }
            }
         }

         if (alreadyPlaying && !"rainwolf".equals(name)) {
            openTBgames--;
            iterator.remove();
            continue;
         }
      }


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
//      if (iAmIgnored && !alreadyPlaying) {
      if (iAmIgnored) {
         openTBgames--;
         iterator.remove();
         continue;
      }

      if ("rainwolf".equals(name)) {
         continue;
      }

      if (s.getGame1().isRated()) {
         int game = s.getGame1().getGame();
         if (kothStorer.getEventId(game) == s.getGame1().getEventId()) {
            Hill hill = kothStorer.getHill(game);
            if (!hill.hasPlayer(myPID)) {
               openTBgames--;
               iterator.remove();
               continue;
            } else {
               if (!meData.hasPlayerDonated() && !kothStorer.canPlayerBeChallenged(game, myPID)) {
                  openTBgames--;
                  iterator.remove();
                  continue;
               } else {
                  int stepsBetween = hill.stepsBetween(myPID, s.getInviterPid());
                  if (stepsBetween < 0) {
                     stepsBetween *= -1;
                  }
                  if (stepsBetween > 2) {
                     openTBgames--;
                     iterator.remove();
                     continue;
                  }
               }

            }

         }
      }


      if (s.getInvitationRestriction() == TBSet.ANY_RATING) {
         continue;
      }
      DSGPlayerGameData myGameData = meData.getPlayerGameData(s.getGame1().getGame());
      int myRating = 1200;
      if (myGameData != null && myGameData.getTotalGames() > 0) {
         myRating = (int) Math.round(myGameData.getRating());
      }
      DSGPlayerData oppData = null;
      oppData = dsgPlayerStorer.loadPlayer(theirPID);
      DSGPlayerGameData oppGameData = null;
      if (oppData != null) {
         oppGameData = oppData.getPlayerGameData(s.getGame1().getGame());
      }
      int oppRating = 1200;
      if (oppGameData != null && oppGameData.getTotalGames() > 0) {
         oppRating = (int) Math.round(oppGameData.getRating());
      }
      if (s.getInvitationRestriction() == TBSet.LOWER_RATING) {
         if (myRating > oppRating) {
            openTBgames--;
            iterator.remove();
         }
         continue;
      }
      if (s.getInvitationRestriction() == TBSet.HIGHER_RATING) {
         if (myRating <= oppRating) {
            openTBgames--;
            iterator.remove();
         }
         continue;
      }
      int delta = 100;
      if (s.getInvitationRestriction() == TBSet.SIMILAR_RATING) {
         if ((myRating + delta < oppRating) || (myRating - delta > oppRating)) {
            openTBgames--;
            iterator.remove();
         }
         continue;
      }
      if (s.getInvitationRestriction() == TBSet.CLASS_RATING) {
         if (1900 <= myRating && 1900 <= oppRating) {
            continue;
         }
         if ((myRating >= 1700 && myRating < 1900) && (oppRating >= 1700 && oppRating < 1900)) {
            continue;
         }
         if ((myRating >= 1400 && myRating < 1700) && (oppRating >= 1400 && oppRating < 1700)) {
            continue;
         }
         if ((myRating >= 1000 && myRating < 1400) && (oppRating >= 1000 && oppRating < 1400)) {
            continue;
         }
         if (1000 > myRating && oppRating < 1000) {
            continue;
         }
         openTBgames--;
         iterator.remove();
      }
   }

   Collections.sort(waitingSets, (o1, o2) -> {
      boolean o1KotH = (kothStorer.getEventId(o1.getGame1().getGame()) == o1.getGame1().getEventId());
      boolean o2KotH = (kothStorer.getEventId(o2.getGame1().getGame()) == o2.getGame1().getEventId());
      boolean beginner1 = o1.getInvitationRestriction() == TBSet.BEGINNER, beginner2 = o2.getInvitationRestriction() == TBSet.BEGINNER;
      if (beginner1 && !beginner2) {
         return -1;
      } else if (!beginner1 && beginner2) {
         return 1;
      }
      if (o1KotH && !o2KotH) {
         return -1;
      } else if (!o1KotH && o2KotH) {
         return 1;
      }
//              return o2.getCreationDate().compareTo(o1.getCreationDate());
      return o1.getGame1().getGame() - o2.getGame1().getGame();
   });

   boolean limitExceeded = false;
// ServletContext ctx = getServletContext();
// int gamesLimit = Integer.parseInt(ctx.getInitParameter("TBGamesLimit"));
// if (meData.unlimitedTBGames()) {
//   limitExceeded = false;
// } else {
//   int currentCount = myTurn.size() + oppTurn.size();
//   if (!invitesFrom.isEmpty()) {
//     for (TBSet s : invitesFrom) {
//       if (s.isTwoGameSet()) {
//         currentCount += 2;
//       } else {
//         currentCount++;
//       }
//     }
//   }
//   if (currentCount > gamesLimit) {
//     limitExceeded = true;
//   } else {
//     limitExceeded = false;
//   }
// }

%>
<br>
Here you can find other players who want to play turn-based games.<br>
A public invitation will not show up for you if you are already playing that particular game against the person inviting.
<br>
Games here can be accepted by anyone. To post a game here, click the button
below and do not specify a player to invite.<br>
<br>

<table align="center" style="width:180px">
   <tr>
      <td>
         <a class="boldbuttons" href="/gameServer/tb/new.jsp" align="center" style="margin-right:6px; margin-left: 6px"><span>Create Game Invitation </span></a>
      </td>
   </tr>
</table>
<%--
<input type="button" value="Create Game"
   onclick="javascript:window.location='/gameServer/tb/new.jsp';">
--%>
<table border="0" cellpadding="0" cellspacing="0" width="100%">
   <tr>
      <td>
         <% if (openTBgames == 0) { %>
         <h3>Public Invitation Games</h3>
         No public invitation games found.<br>
         <% } else { %>

         <br>
         Be kind. Don't gobble all the public invitations. <br> Post some in return.
         <br>
         <br>
         The "Open to" column is strictly informative, you can accept any invitation listed below.
         <br>
         <br>

         <% if (limitExceeded) { %>
         You have reached the limit of games you can play simultaenously on a free account. You can accept these
         invitations when you finish some games.
         This limit can be removed by becoming a subscriber.
         <br>
         <br>
         <%}%>
         <table border="0" cellspacing="0" cellpadding="0" width="800">
            <tr bgcolor="<%= textColor2 %>">
               <td colspan="6">
                  <font color="white">
                     <b>Public Invitation Games (<%= openTBgames %>)
                  </font>
               </td>
            </tr>
            <tr>
               <td><b>Game</b></td>
               <td><b>Player</b></td>
               <td><b>Play as</b></td>
               <td><b>Time/Move</b></td>
               <td><b>Rated</b></td>
               <% if ("rainwolf".equals(name)) { %>
               <td><b>Open to</b></td>
               <% } %>
            </tr>

            <%
               List<TBSet> beginnerList = new ArrayList<>(), kothList = new ArrayList<>(), restList = new ArrayList<>();
               for (TBSet s : waitingSets) {
                  if (s.getInvitationRestriction() == TBSet.BEGINNER) {
                     beginnerList.add(s);
                  } else if (kothStorer.getEventId(s.getGame1().getGame()) == s.getGame1().getEventId()) {
                     kothList.add(s);
                  } else {
                     restList.add(s);
                  }
               }
            %>
            <% for (TBSet s : kothList) {
               String color = null;
               boolean isGo = s.getGame1().getGame() == GridStateFactory.TB_GO ||
                  s.getGame1().getGame() == GridStateFactory.TB_GO9 ||
                  s.getGame1().getGame() == GridStateFactory.TB_GO13;
               if (s.isTwoGameSet()) {
                  color = "white, black (2 game set)";
               } else if (s.getPlayer1Pid() == 0) {
                  color = !isGo ? "white" : "black";
               } else {
                  color = isGo ? "white" : "black";
               }

               DSGPlayerData opp = dsgPlayerStorer.loadPlayer(s.getInviterPid());
               DSGPlayerData d = opp;
               DSGPlayerGameData dsgPlayerGameData = opp.getPlayerGameData(s.getGame1().getGame());
               String oppName = "<a href=/gameServer/profile?viewName=" + opp.getName() +
                  ">" + opp.getName() + "</a>"; %>

            <tr>
               <td>
                  <% if (limitExceeded) { %>
                  <%= GridStateFactory.getGameName(s.getGame1().getGame()) %>
                  <%} else {%>
                  <a href="/gameServer/tb/replyInvitation?command=load&sid=<%= s.getSetId() %>">
                     <%= GridStateFactory.getGameName(s.getGame1().getGame()) + " (KotH)"%>
                  </a>
                  <%}%></td>
               <td>
                  <%@include file="../playerLink.jspf" %>
                  <%@ include file="../ratings.jspf" %>
               </td>
               <td><%= color %>
               </td>
               <td><%= s.getGame1().getDaysPerMove() %> days</td>
               <td><%= s.getGame1().isRated() ? "Rated" : "Not Rated" %>
               </td>
               <% if (true || "rainwolf".equals(name)) { %>
               <td>
                  <%
                     String anyoneString = "Anyone";
                     DSGPlayerGameData oppGameData = null;
                     int oppRating = 1200;
                     if (s.getInvitationRestriction() != TBSet.ANY_RATING) {
                        oppGameData = opp.getPlayerGameData(s.getGame1().getGame());
                        if (oppGameData != null && oppGameData.getTotalGames() > 0) {
                           oppRating = (int) Math.round(oppGameData.getRating());
                        }
                     }
                     if ("rainwolf".equals(name) && (s.getInvitationRestriction() == TBSet.ANYONE_NOTPLAYING)) {
                        anyoneString += " new";
                     }
                     if (s.getInvitationRestriction() == TBSet.LOWER_RATING) {
                        anyoneString += " under " + oppRating;
                     }
                     if (s.getInvitationRestriction() == TBSet.HIGHER_RATING) {
                        anyoneString += " over " + oppRating;
                     }
                     if (s.getInvitationRestriction() == TBSet.SIMILAR_RATING) {
                        anyoneString += " " + oppRating + " &plusmn 100";
                     }
                     if (s.getInvitationRestriction() == TBSet.BEGINNER) {
                        anyoneString += " " + oppRating + " beginner";
                     }
                     if (s.getInvitationRestriction() == TBSet.CLASS_RATING) {
                        SimpleDSGPlayerGameData tmpData = new SimpleDSGPlayerGameData();
                        anyoneString += " <img src=\"/gameServer/images/" + tmpData.getRatingsGifRatingOnly(oppRating) + "\">";
                     }
                  %>

                  <%=anyoneString%>

               </td>
               <% } %>
            </tr>
            <% } %>
            <% if (!kothList.isEmpty()) { %>
            <tr>
               <td colspan="5"> &nbsp</td>
            </tr>
            <% } %>

            <% for (TBSet s : restList) {
               String color = null;
               boolean isGo = s.getGame1().getGame() == GridStateFactory.TB_GO ||
                  s.getGame1().getGame() == GridStateFactory.TB_GO9 ||
                  s.getGame1().getGame() == GridStateFactory.TB_GO13;
               if (s.isTwoGameSet()) {
                  color = "white, black (2 game set)";
               } else if (s.getPlayer1Pid() == 0) {
                  color = !isGo ? "white" : "black";
               } else {
                  color = isGo ? "white" : "black";
               }
               DSGPlayerData opp = dsgPlayerStorer.loadPlayer(s.getInviterPid());
               DSGPlayerData d = opp;
               DSGPlayerGameData dsgPlayerGameData = opp.getPlayerGameData(s.getGame1().getGame());
               String oppName = "<a href=/gameServer/profile?viewName=" + opp.getName() +
                  ">" + opp.getName() + "</a>"; %>

            <tr>
               <td>
                  <% if (limitExceeded) { %>
                  <%= GridStateFactory.getGameName(s.getGame1().getGame()) %>
                  <%} else {%>
                  <a href="/gameServer/tb/replyInvitation?command=load&sid=<%= s.getSetId() %>">
                     <%= GridStateFactory.getGameName(s.getGame1().getGame())%>
                  </a>
                  <%}%></td>
               <td>
                  <%@include file="../playerLink.jspf" %>
                  <%@ include file="../ratings.jspf" %>
               </td>
               <td><%= color %>
               </td>
               <td><%= s.getGame1().getDaysPerMove() %> days</td>
               <td><%= s.getGame1().isRated() ? "Rated" : "Not Rated" %>
               </td>
               <% if (true || "rainwolf".equals(name)) { %>
               <td>
                  <%
                     String anyoneString = "Anyone";
                     DSGPlayerGameData oppGameData = null;
                     int oppRating = 1200;
                     if (s.getInvitationRestriction() != TBSet.ANY_RATING) {
                        oppGameData = opp.getPlayerGameData(s.getGame1().getGame());
                        if (oppGameData != null && oppGameData.getTotalGames() > 0) {
                           oppRating = (int) Math.round(oppGameData.getRating());
                        }
                     }
                     if ("rainwolf".equals(name) && (s.getInvitationRestriction() == TBSet.ANYONE_NOTPLAYING)) {
                        anyoneString += " new";
                     }
                     if (s.getInvitationRestriction() == TBSet.LOWER_RATING) {
                        anyoneString += " under " + oppRating;
                     }
                     if (s.getInvitationRestriction() == TBSet.HIGHER_RATING) {
                        anyoneString += " over " + oppRating;
                     }
                     if (s.getInvitationRestriction() == TBSet.SIMILAR_RATING) {
                        anyoneString += " " + oppRating + " &plusmn 100";
                     }
                     if (s.getInvitationRestriction() == TBSet.BEGINNER) {
                        anyoneString += " " + oppRating + " beginner";
                     }
                     if (s.getInvitationRestriction() == TBSet.CLASS_RATING) {
                        SimpleDSGPlayerGameData tmpData = new SimpleDSGPlayerGameData();
                        anyoneString += " <img src=\"/gameServer/images/" + tmpData.getRatingsGifRatingOnly(oppRating) + "\">";
                     }
                  %>

                  <%=anyoneString%>

               </td>
               <% } %>
            </tr>
            <% } %>

            <% if (!restList.isEmpty()) { %>
            <tr>
               <td colspan="5"> &nbsp</td>
            </tr>
            <% } %>

            <% for (TBSet s : beginnerList) {
               String color = null;
               boolean isGo = s.getGame1().getGame() == GridStateFactory.TB_GO ||
                  s.getGame1().getGame() == GridStateFactory.TB_GO9 ||
                  s.getGame1().getGame() == GridStateFactory.TB_GO13;
               if (s.isTwoGameSet()) {
                  color = "white, black (2 game set)";
               } else if (s.getPlayer1Pid() == 0) {
                  color = !isGo ? "white" : "black";
               } else {
                  color = isGo ? "white" : "black";
               }
               DSGPlayerData opp = dsgPlayerStorer.loadPlayer(s.getInviterPid());
               DSGPlayerData d = opp;
               DSGPlayerGameData dsgPlayerGameData = opp.getPlayerGameData(s.getGame1().getGame());
               String oppName = "<a href=/gameServer/profile?viewName=" + opp.getName() +
                  ">" + opp.getName() + "</a>"; %>
            <tr>
               <td>
                  <% if (limitExceeded) { %>
                  <%= GridStateFactory.getGameName(s.getGame1().getGame()) %>
                  <%} else {%>
                  <a href="/gameServer/tb/replyInvitation?command=load&sid=<%= s.getSetId() %>">
                     <%= GridStateFactory.getGameName(s.getGame1().getGame())%>
                  </a>
                  <%}%></td>
               <td>
                  <%@include file="../playerLink.jspf" %>
                  <%@ include file="../ratings.jspf" %>
               </td>
               <td><%= color %>
               </td>
               <td><%= s.getGame1().getDaysPerMove() %> days</td>
               <td><%= s.getGame1().isRated() ? "Rated" : "Not Rated" %>
               </td>
               <% if (true || "rainwolf".equals(name)) { %>
               <td>
                  <%
                     String anyoneString = "Anyone";
                     DSGPlayerGameData oppGameData = null;
                     int oppRating = 1200;
                     if (s.getInvitationRestriction() != TBSet.ANY_RATING) {
                        oppGameData = opp.getPlayerGameData(s.getGame1().getGame());
                        if (oppGameData != null && oppGameData.getTotalGames() > 0) {
                           oppRating = (int) Math.round(oppGameData.getRating());
                        }
                     }
                     if ("rainwolf".equals(name) && (s.getInvitationRestriction() == TBSet.ANYONE_NOTPLAYING)) {
                        anyoneString += " new";
                     }
                     if (s.getInvitationRestriction() == TBSet.LOWER_RATING) {
                        anyoneString += " under " + oppRating;
                     }
                     if (s.getInvitationRestriction() == TBSet.HIGHER_RATING) {
                        anyoneString += " over " + oppRating;
                     }
                     if (s.getInvitationRestriction() == TBSet.SIMILAR_RATING) {
                        anyoneString += " " + oppRating + " &plusmn 100";
                     }
                     if (s.getInvitationRestriction() == TBSet.BEGINNER) {
                        anyoneString += " beginner";
                     }
                     if (s.getInvitationRestriction() == TBSet.CLASS_RATING) {
                        SimpleDSGPlayerGameData tmpData = new SimpleDSGPlayerGameData();
                        anyoneString += " <img src=\"/gameServer/images/" + tmpData.getRatingsGifRatingOnly(oppRating) + "\">";
                     }
                  %>

                  <%=anyoneString%>

               </td>
               <% } %>
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
