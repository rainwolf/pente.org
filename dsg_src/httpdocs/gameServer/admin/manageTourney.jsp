<%@ page import="java.util.*,
                 java.sql.*,
                 org.pente.gameServer.tourney.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.core.*,
                 org.pente.game.*,
                 org.apache.log4j.*" %>

<%! private static Category log4j =
   Category.getInstance("org.pente.gameServer.web.client.jsp"); %>

<% Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());

   try {

      String eidStr = request.getParameter("eid");
      int eid = Integer.parseInt(eidStr);
      List<TourneyPlayerData> players = null;
      Tourney tourney = resources.getTourneyStorer().getTourney(eid);
      boolean notComplete = false;

      // just viewing page for first time
      if (request.getParameter("command") == null) {
         if (tourney.getNumRounds() == 0) {
            players = resources.getTourneyStorer().getTourneyPlayers(eid);
         } else {
            players = tourney.getLastRound().getPlayers();
         }
      }
      // starting new round
      else {


         TourneyRound newRound = null;

         // then create next round
         if (tourney.getNumRounds() > 0) {
            int currentRound = tourney.getLastRound().getRound();

            List updateMatches = null;
            // forfeit/drop players first
            String toForfeitStr[] = request.getParameterValues("forfeit");
            String toDropStr[] = request.getParameterValues("drop");
            if (toForfeitStr != null) {
               long toForfeit[] = new long[toForfeitStr.length];
               boolean toDrop[] = new boolean[toForfeit.length];
               for (int i = 0; i < toForfeit.length; i++) {
                  toForfeit[i] = Long.parseLong(toForfeitStr[i]);
                  System.out.println("forfeit=" + toForfeit[i]);
                  if (toDropStr != null) {
                     // see if drop checkbox checked for toForfeit[i]
                     for (int j = 0; j < toDropStr.length; j++) {
                        if (toForfeitStr[i].equals(toDropStr[j])) {
                           toDrop[i] = true;
                           break;
                        }
                     }
                  }
               }
               updateMatches = tourney.getLastRound().forfeitPlayers(
                  toForfeit, toDrop);
            }
            // this will insert next round for us, if current round done
            resources.getTourneyStorer().updateMatches(updateMatches, tourney);

            if (!tourney.isComplete()) {
               // make sure a new round was created
               if (currentRound == tourney.getLastRound().getRound()) {
                  notComplete = true;
               }

               // get new list of players
               players = tourney.getLastRound().getPlayers();
            }
         } else {
            // drop players first
            String toDrop[] = request.getParameterValues("drop");
            if (toDrop != null) {
               for (int i = 0; i < toDrop.length; i++) {
                  long pid = Long.parseLong(toDrop[i]);
                  resources.getTourneyStorer().removePlayerFromTourney(
                     pid, eid);
               }
            }

            // create first round
            players = resources.getTourneyStorer().setInitialSeeds(eid);
            newRound = tourney.createFirstRound(players);
            resources.getTourneyStorer().insertRound(newRound);
         }
      }

%>

<html>
<head>
   <title>Manage Tournament</title>
</head>

<body>

<h3>Manage Tournament: <%= tourney.getName() %>
</h3>
From the list of players below, select any who are to forfeit their match
in this round. If you want to drop a player from the whole tournament,
then check both the forfeit AND drop checkboxes.
After submitting this, the next round will start, provided
that all matches from the previous round have an outcome.<br>
<br>

<% if (tourney.isComplete()) { %>
<font color="red">
   Tournament is complete.<br>
   <br>
</font>
<% } else if (notComplete) { %>
<font color="red">
   After forfeiting/dropping players you selected, the round still isn't complete,
   someone still needs to play games, or you need to forfeit/drop someone else.
</font>
<% } %>

<% if (!tourney.isComplete()) {

   int round = tourney.getNumRounds() + 1; %>
<form name="manage" method="post" action="manageTourney.jsp">
   <input type="hidden" name="eid" value="<%= eidStr %>">
   <input type="hidden" name="command" value="start">
   <b>Players to forfeit/drop</b><br>
   <table border="1" cellpadding="2">
      <tr>
         <td>Forfeit</td>
         <td>Drop</td>
         <td>Name</td>
      </tr>
      <% for (Iterator it = players.iterator(); it.hasNext(); ) {
         TourneyPlayerData d = (TourneyPlayerData) it.next(); %>
      <tr>
         <td>
            <input type="checkbox" name="forfeit" value="<%= d.getPlayerID() %>">
         </td>
         <td>
            <input type="checkbox" name="drop" value="<%= d.getPlayerID() %>">
         </td>
         <td><%= d.getName() %>
         </td>
      </tr>
      <% } %>
   </table>
   <input type="submit" value="Start round <%= round %>">
   <% } %>

</form>
<a href=".">Back to admin</a>

</body>
</html>

<% } catch (Throwable t) {
   t.printStackTrace();
} %>
