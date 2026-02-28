<%@ page import="org.pente.database.*,
                 org.pente.game.*,
                 org.pente.turnBased.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.tourney.*,
                 org.pente.gameServer.server.*,
                 org.pente.message.*,
                 org.pente.kingOfTheHill.*,
                 java.text.*,
                 java.sql.*,
                 java.util.Date,
                 java.util.List,
                 java.util.*,
                 org.apache.log4j.*"
%>
<%@ page contentType="application/json; charset=UTF-8" %>
<%!
   private static Category log4j = Category.getInstance("org.pente.gameServer.web.client.jsp");
   private static String jsonStr(String s) {
      if (s == null) return "null";
      return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
   }
%>
<%
   String loginname = request.getParameter("name");
   String name = null;
   if (loginname != null) {
      name = loginname.toLowerCase();
   }
   String gameStr = (String) request.getParameter("game");
   if (gameStr == null) {
      gameStr = (String) request.getAttribute("game");
   }
   int game = 0;
   if (gameStr != null) {
      game = Integer.parseInt(gameStr);
   }

   DBHandler dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());
   Resources resources = (Resources) application.getAttribute(Resources.class.getName());
   CacheKOTHStorer kothStorer = resources.getKOTHStorer();
   DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
   DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
   long myPid = dsgPlayerData.getPlayerID();
   DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");

   Hill hill = kothStorer.getHill(game);
%>[<%
   if (hill != null && !hill.getSteps().isEmpty()) {
      boolean canIchallenge = hill.hasPlayer(myPid);
      if (game > 50) {
         if (!dsgPlayerData.hasPlayerDonated()) {
            canIchallenge = canIchallenge && kothStorer.canPlayerBeChallenged(game, myPid);
         }
      }
      int myStep = -1;
      if (canIchallenge) {
         myStep = hill.myStep(myPid);
      }
      List<Step> steps = hill.getSteps();
      boolean firstStep = true;
      for (int i = 0; i < steps.size(); i++) {
         Collections.sort(steps.get(i).getPlayers(), (o1, o2) -> o2.getLastGame().compareTo(o1.getLastGame()));
         if (!firstStep) { %>,<% } firstStep = false;
%>[<%
         boolean firstPlayer = true;
         for (Player player : steps.get(i).getPlayers()) {
            long pid = player.getPid();
            DSGPlayerData d = dsgPlayerStorer.loadPlayer(pid);
            DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(game);
            boolean canChallengeThem = false;
            if (canIchallenge && myPid != pid && (myStep - i) * (myStep - i) < 5) {
               if (game > 50) {
                  boolean iAmIgnored = false;
                  List<DSGIgnoreData> ignoreData = dsgPlayerStorer.getIgnoreData(pid);
                  for (Iterator<DSGIgnoreData> it = ignoreData.iterator(); it.hasNext(); ) {
                     DSGIgnoreData id = it.next();
                     if (id.getIgnorePid() == myPid) {
                        if (id.getIgnoreInvite()) {
                           iAmIgnored = true;
                           break;
                        }
                     }
                  }
                  canChallengeThem = !iAmIgnored && kothStorer.canPlayerBeChallenged(game, pid);
               } else {
                  canChallengeThem = true;
               }
            }
            if (!firstPlayer) { %>,<% } firstPlayer = false;
%>{"name":<%=jsonStr(d.getName())%>,"rating":<%=((dsgPlayerGameData != null) ? (int) Math.round(dsgPlayerGameData.getRating()) : 1600)%>,"canChallenge":<%=(canIchallenge && myPid != pid && (myStep - i) * (myStep - i) < 5 && canChallengeThem)%>,"color":<%=(d.hasPlayerDonated() ? d.getNameColorRGB() : 0)%>,"tourneyWinner":<%=d.getTourneyWinner()%>,"lastGame":<%=jsonStr(dateFormat.format(player.getLastGame()))%>}<%
         }
%>]<%
      }
   }
%>]