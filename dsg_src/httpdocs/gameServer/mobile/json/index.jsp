<%@ page import="org.pente.database.*,
                 org.pente.game.*,
                 org.pente.turnBased.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.tourney.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.client.web.*,
                 org.pente.gameServer.mobile.*,
                 org.pente.message.*,
                 org.pente.kingOfTheHill.*,
                 com.google.gson.Gson,
                 java.util.*,
                 org.apache.log4j.*"
%>
<%@ page contentType="application/json; charset=UTF-8" %>
<%! private static Category log4j = Category.getInstance("org.pente.gameServer.web.client.jsp"); %>
<%
   String loginname = request.getParameter("name");
   String name = null;
   if (loginname != null) {
      name = loginname.toLowerCase();
   }
   String password = request.getParameter("password");

   DBHandler dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());
   LoginHandler loginHandler = new SmallLoginHandler(dbHandler);
   int loginResult = LoginHandler.INVALID;

   if ((name != null) && (password != null)) {
      loginResult = loginHandler.isValidLogin(name, password);
      if (loginResult == LoginHandler.INVALID) {
         PasswordHelper passwordHelper = (PasswordHelper) application.getAttribute(PasswordHelper.class.getName());
         password = passwordHelper.encrypt(password);
         loginResult = loginHandler.isValidLogin(name, password);
      }

      if (loginResult == LoginHandler.VALID) {
         String checkusername = request.getParameter("checkname");
         if (checkusername != null && name.equals("rainwolf")) {
            name = checkusername;
         }

         Resources resources = (Resources) application.getAttribute(Resources.class.getName());
         DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
         DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
         long myPID = dsgPlayerData.getPlayerID();

         SessionListener sessionListener = (SessionListener) application.getAttribute(SessionListener.class.getName());
         List<WhosOnlineRoom> rooms = WhosOnline.getPlayers(resources, sessionListener);
         List<String> onlinePlayerNames = new ArrayList<>();
         int livePlayers = 0;
         int onlineFollowing = 0;
         DSGFollowerStorer followerStorer = resources.getFollowerStorer();
         List<Long> followingers = followerStorer.getFollowing(myPID);
         for (Iterator<WhosOnlineRoom> iterator = rooms.iterator(); iterator.hasNext();) {
            WhosOnlineRoom r = iterator.next();
            for (DSGPlayerData d : r.getPlayers()) {
               onlinePlayerNames.add(d.getName());
               if (followingers.contains(d.getPlayerID())) {
                  onlineFollowing += 1;
               }
            }
            if ("web".equals(r.getName()) || "Mobile".equals(r.getName())) {
               continue;
            }
            livePlayers += r.getPlayers().size();
         }

         List<DSGPlayerPreference> prefs = dsgPlayerStorer.loadPlayerPreferences(myPID);
         boolean emailMe = true;
         boolean personalizeAds = false;
         for (DSGPlayerPreference pref : prefs) {
            if ("emailDsgMessages".equals(pref.getName())) {
               emailMe = ((Boolean) pref.getValue()).booleanValue();
            }
            if ("personalizeAds".equals(pref.getName())) {
               personalizeAds = ((Boolean) pref.getValue()).booleanValue();
            }
         }

         TourneyStorer tourneyStorer = resources.getTourneyStorer();
         List<Tourney> currentTournies = (List<Tourney>) tourneyStorer.getCurrentTournies();
         final CacheKOTHStorer kothStorer = resources.getKOTHStorer();
         TBGameStorer tbGameStorer = resources.getTbGameStorer();
         List<TBSet> waitingSets = ((CacheTBStorer)tbGameStorer).getWaitingSets();
         List<TBSet> currentSets = tbGameStorer.loadSets(myPID);
         List<TBSet> invitesTo = new ArrayList<TBSet>();
         List<TBSet> invitesFrom = new ArrayList<TBSet>();
         List<TBGame> myTurn = new ArrayList<TBGame>();
         List<TBGame> oppTurn = new ArrayList<TBGame>();
         Utilities.organizeGames(myPID, currentSets, invitesTo, invitesFrom, myTurn, oppTurn);
         List<DSGMessage> messages = resources.getDsgMessageStorer().getMessages(myPID);
         TimeZone tz = TimeZone.getTimeZone(dsgPlayerData.getTimezone());
         Collections.sort(messages, (m1,m2)-> (m2.getMid() - m1.getMid()));

         int openTBgames = 0;
         DSGPlayerData meData = dsgPlayerData;
         for (Iterator<TBSet> iterator = waitingSets.iterator(); iterator.hasNext();) {
            TBSet s = iterator.next();
            if (s.getPlayer1Pid() != meData.getPlayerID() && s.getPlayer2Pid() != meData.getPlayerID()) {
               openTBgames++;
            } else {
               iterator.remove();
               continue;
            }
            int nrGamesPlaying = 0;
            boolean alreadyPlaying = false, iAmIgnored = false;
            long theirPID = (0 == s.getPlayer1Pid()) ? s.getPlayer2Pid() : s.getPlayer1Pid();
            if (s.getInvitationRestriction() == TBSet.ANYONE_NOTPLAYING) {
               String setGame = GridStateFactory.getGameName(s.getGame1().getGame());
               for (TBGame g : myTurn) {
                  long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
                  String myTurnGame = GridStateFactory.getGameName(g.getGame());
                  if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
                     alreadyPlaying = true;
                     break;
                  }
               }
               if (!alreadyPlaying) {
                  for (TBGame g : oppTurn) {
                     long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
                     String myTurnGame = GridStateFactory.getGameName(g.getGame());
                     if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
                        alreadyPlaying = true;
                        break;
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
            for (Iterator<DSGIgnoreData> it = ignoreData.iterator(); it.hasNext();) {
               DSGIgnoreData i = it.next();
               if (i.getIgnorePid() == myPID) {
                  if (i.getIgnoreInvite()) {
                     iAmIgnored = true;
                     break;
                  }
               }
            }
            if (iAmIgnored) {
               openTBgames--;
               iterator.remove();
               continue;
            }
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
            if (s.getInvitationRestriction() == TBSet.ANY_RATING) {
               continue;
            }
            DSGPlayerGameData myGameData = meData.getPlayerGameData(s.getGame1().getGame());
            int myRating = 1200;
            if (myGameData != null && myGameData.getTotalGames() > 0) {
               myRating = (int) Math.round(myGameData.getRating());
            }
            DSGPlayerData oppData = dsgPlayerStorer.loadPlayer(theirPID);
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
               if (1900 <= myRating && 1900 <= oppRating) { continue; }
               if ((myRating >= 1700 && myRating < 1900) && (oppRating >= 1700 && oppRating < 1900)) { continue; }
               if ((myRating >= 1400 && myRating < 1700) && (oppRating >= 1400 && oppRating < 1700)) { continue; }
               if ((myRating >= 1000 && myRating < 1400) && (oppRating >= 1000 && oppRating < 1400)) { continue; }
               if (1000 > myRating && oppRating < 1000) { continue; }
               openTBgames--;
               iterator.remove();
            }
         }

         Collections.sort(waitingSets, (o1,o2)-> {
            boolean o1KotH = (kothStorer.getEventId(o1.getGame1().getGame()) == o1.getGame1().getEventId());
            boolean o2KotH = (kothStorer.getEventId(o2.getGame1().getGame()) == o2.getGame1().getEventId());
            boolean beginner1 = o1.getInvitationRestriction() == TBSet.BEGINNER, beginner2 = o2.getInvitationRestriction() == TBSet.BEGINNER;
            if (o1KotH && !o2KotH) { return -1; } else if (!o1KotH && o2KotH) { return 1; }
            if (beginner1 && !beginner2) { return -1; } else if (!beginner1 && beginner2) { return 1; }
            return o1.getGame1().getGame() - o2.getGame1().getGame();
         });

         int gamesLimit = 0;
         DSGPlayerGameData playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_PENTE);
         if (playerGameData != null) { gamesLimit += playerGameData.getTotalGames(); }
         playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_PENTE);
         if (playerGameData != null) { gamesLimit += playerGameData.getTotalGames(); }
         playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_KERYO);
         if (playerGameData != null) { gamesLimit += playerGameData.getTotalGames(); }
         playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_GOMOKU);
         if (playerGameData != null) { gamesLimit += playerGameData.getTotalGames(); }
         playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_DPENTE);
         if (playerGameData != null) { gamesLimit += playerGameData.getTotalGames(); }
         playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_GPENTE);
         if (playerGameData != null) { gamesLimit += playerGameData.getTotalGames(); }
         playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_POOF_PENTE);
         if (playerGameData != null) { gamesLimit += playerGameData.getTotalGames(); }
         playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_CONNECT6);
         if (playerGameData != null) { gamesLimit += playerGameData.getTotalGames(); }
         playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_BOAT_PENTE);
         if (playerGameData != null) { gamesLimit += playerGameData.getTotalGames(); }
         if (gamesLimit < 4) {
            gamesLimit = 4;
         } else if (gamesLimit > 20) {
            gamesLimit = 2000;
         }

         boolean isRainwolf = "rainwolf".equals(name);
         if (isRainwolf) { gamesLimit = 31; }
         boolean hasTbGamesLimit = isRainwolf || (!dsgPlayerData.unlimitedTBGames() && !dsgPlayerData.unlimitedMobileTBGames());

         IndexResponse indexResponse = new IndexResponse.Builder(
                 dsgPlayerData, name, dsgPlayerStorer, kothStorer, tourneyStorer, currentTournies)
             .setOnlineStats(livePlayers, onlineFollowing, onlinePlayerNames)
             .setPreferences(emailMe, personalizeAds, gamesLimit, hasTbGamesLimit)
             .setGames(myTurn, oppTurn, invitesTo, invitesFrom, waitingSets)
             .setMessages(messages, tz)
             .setUpcomingTournies((List<Tourney>) tourneyStorer.getUpcomingTournies())
             .build();
         out.print(new Gson().toJson(indexResponse));
      } else {
         out.print("{\"error\":\"Invalid name or password, please try again.\"}");
      }
   } else {
      out.print("{\"error\":\"Invalid name or password, please try again.\"}");
   }
%>