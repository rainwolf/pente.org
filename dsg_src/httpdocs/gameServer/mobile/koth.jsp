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
<%@ page contentType="text/html; charset=UTF-8" %>
<%! private static Category log4j =
   Category.getInstance("org.pente.gameServer.web.client.jsp"); %><%
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

   String lineBreak = System.getProperty("line.separator");

   DBHandler dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());

   Resources resources = (Resources) application.getAttribute(
      Resources.class.getName());
   CacheKOTHStorer kothStorer = resources.getKOTHStorer();
   DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
   DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
   long myPid = dsgPlayerData.getPlayerID();
   DSGPlayerData meData = dsgPlayerData;
   DateFormat dateFormat = null;
// TimeZone tz = TimeZone.getTimeZone(dsgPlayerData.getTimezone());
   dateFormat = new SimpleDateFormat("MM/dd/yyyy");
// dateFormat.setTimeZone(tz);

   Hill hill;
// game = GridStateFactory.PENTE;
   hill = kothStorer.getHill(game);
   long kingPid = 0;
   if (hill != null && hill.getSteps().size() > 0) {
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
      for (int i = 0; i < steps.size(); i++) {
         Collections.sort(steps.get(i).getPlayers(), (o1, o2) -> o2.getLastGame().compareTo(o1.getLastGame()));

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
%><%=d.getName() + "," + ((dsgPlayerGameData != null) ? (int) Math.round(dsgPlayerGameData.getRating()) : "1600") + "," + (canIchallenge && myPid != pid && (myStep - i) * (myStep - i) < 5 && canChallengeThem ? "yes" : "no") + "," + (d.hasPlayerDonated() ? d.getNameColorRGB() : 0) + "," + d.getTourneyWinner() + "," + dateFormat.format(player.getLastGame()) + ";"%><%
   }
%><%=lineBreak%><%
   }
} else {
%><%
   }
%>