<%@ page import="org.pente.database.*,
                 org.pente.game.*,
                 org.pente.turnBased.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.tourney.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.client.web.*,
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
         DateFormat messageDateFormat = new SimpleDateFormat("MM/dd/yy");
         messageDateFormat.setTimeZone(tz);
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
         boolean noAds = isRainwolf || !dsgPlayerData.showAds();
         boolean unlimitedGames = true;
         boolean hasTbGamesLimit = isRainwolf || (!dsgPlayerData.unlimitedTBGames() && !dsgPlayerData.unlimitedMobileTBGames());
         boolean subscriber = dsgPlayerData.hasPlayerDonated();
         boolean dbAccess = subscriber || dsgPlayerData.getRegisterDate().getTime() > System.currentTimeMillis() - 1000L*3600*24*30;

         DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
%>{"settings":{"noAds":<%=noAds%>,"unlimitedGames":<%=unlimitedGames%>,"tbGamesLimit":<%=(hasTbGamesLimit ? gamesLimit : -1)%>},"player":{"name":<%=jsonStr(dsgPlayerData.getName().toLowerCase())%>,"color":<%=(subscriber ? dsgPlayerData.getNameColorRGB() : 0)%>,"showAds":false,"subscriber":<%=subscriber%>,"livePlayers":<%=livePlayers%>,"dbAccess":<%=dbAccess%>,"emailMe":<%=emailMe%>,"onlineFollowing":<%=onlineFollowing%>,"personalizeAds":<%=personalizeAds%>},"kingOfTheHill":[<%
         Hill hill;
         long kingPid = 0;
         boolean canSendOpenKotH = false, amImember = false;
         boolean firstKoth = true;
         for (int gameInt: CacheKOTHStorer.tbGames) {
            hill = kothStorer.getHill(gameInt);
            if (hill == null) { continue; }
            kingPid = hill.getKing();
            if (!subscriber) { canSendOpenKotH = kothStorer.canPlayerBeChallenged(gameInt, myPID); }
            amImember = hill.hasPlayer(myPID);
            if (!firstKoth) { %>,<% } firstKoth = false;
%>{"numPlayers":<%=((hill != null) ? hill.getNumPlayers() : 0)%>,"amIMember":<%=((hill != null && amImember))%>,"iAmKing":<%=(kingPid == myPID)%>,"kingName":<%=jsonStr((kingPid != 0) ? dsgPlayerStorer.loadPlayer(kingPid).getName() : "")%>,"canChallenge":<%=(amImember && (subscriber || canSendOpenKotH))%>,"gameId":<%=gameInt%>}<%
         }
         for (int gameInt: CacheKOTHStorer.liveGames) {
            if (gameInt%2 == 0) { continue; }
            hill = kothStorer.getHill(gameInt);
            if (hill == null || hill.getMembers().isEmpty()) { continue; }
            kingPid = hill.getKing();
            if (!subscriber) { canSendOpenKotH = kothStorer.canPlayerBeChallenged(gameInt, myPID); }
            amImember = hill.hasPlayer(myPID);
            if (!firstKoth) { %>,<% } firstKoth = false;
%>{"numPlayers":<%=((hill != null) ? hill.getNumPlayers() : 0)%>,"amIMember":<%=((hill != null && amImember))%>,"iAmKing":<%=(kingPid == myPID)%>,"kingName":<%=jsonStr((kingPid != 0) ? dsgPlayerStorer.loadPlayer(kingPid).getName() : "")%>,"canChallenge":<%=(amImember && (subscriber || canSendOpenKotH))%>,"gameId":<%=gameInt%>}<%
         }
         for (int gameInt: CacheKOTHStorer.liveGames) {
            if (gameInt%2 == 1) { continue; }
            hill = kothStorer.getHill(gameInt);
            if (hill == null || hill.getMembers().isEmpty()) { continue; }
            kingPid = hill.getKing();
            if (!subscriber) { canSendOpenKotH = kothStorer.canPlayerBeChallenged(gameInt, myPID); }
            amImember = hill.hasPlayer(myPID);
            if (!firstKoth) { %>,<% } firstKoth = false;
%>{"numPlayers":<%=((hill != null) ? hill.getNumPlayers() : 0)%>,"amIMember":<%=((hill != null && amImember))%>,"iAmKing":<%=(kingPid == myPID)%>,"kingName":<%=jsonStr((kingPid != 0) ? dsgPlayerStorer.loadPlayer(kingPid).getName() : "")%>,"canChallenge":<%=(amImember && (subscriber || canSendOpenKotH))%>,"gameId":<%=gameInt%>}<%
         }
%>],"ratingStats":[<%
         boolean firstStat = true;
         Game[] statGames = GridStateFactory.getTbGames();
         for (int i = 0; i < statGames.length; i++) {
            DSGPlayerGameData dsgPlayerGameData = dsgPlayerData.getPlayerGameData(statGames[i].getId());
            if (dsgPlayerGameData == null || dsgPlayerGameData.getTotalGames() == 0) { continue; }
            if (!firstStat) { %>,<% } firstStat = false;
%>{"gameName":<%=jsonStr((statGames[i].getId()>50?"tb-":"") + GridStateFactory.getGameName(statGames[i].getId()).replace("Speed ", "Speed-"))%>,"rating":<%=(int) Math.round(dsgPlayerGameData.getRating())%>,"totalGames":<%=dsgPlayerGameData.getTotalGames()%>,"tourneyWinner":<%=dsgPlayerGameData.getTourneyWinner()%>,"lastGameDate":<%=jsonStr(dateFormat.format(dsgPlayerGameData.getLastGameDate()))%>,"gameId":<%=statGames[i].getId()%>}<%
         }
         statGames = GridStateFactory.getNormalGames();
         for (int i = 0; i < statGames.length; i++) {
            DSGPlayerGameData dsgPlayerGameData = dsgPlayerData.getPlayerGameData(statGames[i].getId());
            if (dsgPlayerGameData == null || dsgPlayerGameData.getTotalGames() == 0) { continue; }
            if (!firstStat) { %>,<% } firstStat = false;
%>{"gameName":<%=jsonStr((statGames[i].getId()>50?"tb-":"") + GridStateFactory.getGameName(statGames[i].getId()).replace("Speed ", "Speed-"))%>,"rating":<%=(int) Math.round(dsgPlayerGameData.getRating())%>,"totalGames":<%=dsgPlayerGameData.getTotalGames()%>,"tourneyWinner":<%=dsgPlayerGameData.getTourneyWinner()%>,"lastGameDate":<%=jsonStr(dateFormat.format(dsgPlayerGameData.getLastGameDate()))%>,"gameId":<%=statGames[i].getId()%>}<%
         }
         statGames = GridStateFactory.getSpeedGames();
         for (int i = 0; i < statGames.length; i++) {
            DSGPlayerGameData dsgPlayerGameData = dsgPlayerData.getPlayerGameData(statGames[i].getId());
            if (dsgPlayerGameData == null || dsgPlayerGameData.getTotalGames() == 0) { continue; }
            if (!firstStat) { %>,<% } firstStat = false;
%>{"gameName":<%=jsonStr((statGames[i].getId()>50?"tb-":"") + GridStateFactory.getGameName(statGames[i].getId()).replace("Speed ", "Speed-"))%>,"rating":<%=(int) Math.round(dsgPlayerGameData.getRating())%>,"totalGames":<%=dsgPlayerGameData.getTotalGames()%>,"tourneyWinner":<%=dsgPlayerGameData.getTourneyWinner()%>,"lastGameDate":<%=jsonStr(dateFormat.format(dsgPlayerGameData.getLastGameDate()))%>,"gameId":<%=statGames[i].getId()%>}<%
         }
%>],"invitationsReceived":[<%
         boolean firstInv = true;
         for (TBSet s : invitesTo) {
            String color = null;
            TBGame g = s.getGame1();
            boolean koth = g.getEventId() == kothStorer.getEventId(g.getGame());
            if (s.isTwoGameSet()) {
               color = "whiteblack";
            } else if (s.getPlayer2Pid() == myPID) {
               color = (s.getGame1().getGame() == GridStateFactory.TB_GO)?"white (p2)":"black (p2)";
            } else {
               color = (s.getGame1().getGame() != GridStateFactory.TB_GO)?"white (p1)":"black (p1)";
            }
            boolean tourney = false;
            if (!koth) {
               for (Tourney tmpTourney : currentTournies) {
                  if (tmpTourney.getEventID() == g.getEventId()) { tourney = true; break; }
               }
            }
            String ratedStr = "Not Rated";
            if (koth) { ratedStr = "KotH"; } else if (tourney) { ratedStr = "Tournament"; } else if (g.isRated()) { ratedStr = "Rated"; }
            DSGPlayerData d = dsgPlayerStorer.loadPlayer(s.getInviterPid());
            DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(s.getGame1().getGame());
            if (!firstInv) { %>,<% } firstInv = false;
%>{"setId":<%=s.getSetId()%>,"gameName":<%=jsonStr(GridStateFactory.getGameName(s.getGame1().getGame()))%>,"opponentName":<%=jsonStr(d.getName())%>,"opponentRating":<%=(int) Math.round(dsgPlayerGameData.getRating())%>,"color":<%=jsonStr(color)%>,"daysPerMove":<%=s.getGame1().getDaysPerMove()%>,"rated":<%=jsonStr(ratedStr)%>,"opponentColor":<%=(d.hasPlayerDonated()?((d.getNameColorRGB() & 0xFFFFFF)==0?((255<<24)+1):d.getNameColorRGB()):0)%>,"opponentTourneyWinner":<%=d.getTourneyWinner()%>}<%
         }
%>],"invitationsSent":[<%
         boolean firstSent = true;
         for (TBSet s : invitesFrom) {
            String color = null;
            boolean go = s.getGame1().getGame() == GridStateFactory.TB_GO ||
                         s.getGame1().getGame() == GridStateFactory.TB_GO9 ||
                         s.getGame1().getGame() == GridStateFactory.TB_GO13;
            if (s.isTwoGameSet()) {
               color = "whiteblack";
            } else if (s.getPlayer2Pid() == myPID) {
               color = (go)?"white (p2)":"black (p2)";
            } else {
               color = (!go)?"white (p1)":"black (p1)";
            }
            long pid = s.getInviteePid();
            DSGPlayerGameData dsgPlayerGameData = null;
            DSGPlayerData d = null;
            String anyoneString = "Anyone";
            TBGame g = s.getGame1();
            boolean koth = g.getEventId() == kothStorer.getEventId(g.getGame());
            boolean tourney = false;
            if (!koth) {
               for (Tourney tmpTourney : currentTournies) {
                  if (tmpTourney.getEventID() == g.getEventId()) { tourney = true; break; }
               }
            }
            String ratedStr = "Not Rated";
            if (koth) { ratedStr = "KotH"; } else if (tourney) { ratedStr = "Tournament"; } else if (g.isRated()) { ratedStr = "Rated"; }
            if (s.getInvitationRestriction() == TBSet.BEGINNER) { ratedStr = ratedStr + ", beginner"; }
            if (pid != 0) {
               d = dsgPlayerStorer.loadPlayer(pid);
               dsgPlayerGameData = d.getPlayerGameData(s.getGame1().getGame());
            } else {
               int myRating = 1600;
               if (s.getInvitationRestriction() != TBSet.ANY_RATING) {
                  DSGPlayerGameData myGameData = dsgPlayerData.getPlayerGameData(s.getGame1().getGame());
                  if (myGameData != null && myGameData.getTotalGames() > 0) {
                     myRating = (int) Math.round(myGameData.getRating());
                  }
               }
               if (s.getInvitationRestriction() == TBSet.ANYONE_NOTPLAYING) { anyoneString += " (new opponent)"; }
               if (s.getInvitationRestriction() == TBSet.LOWER_RATING) { anyoneString += " under " + myRating; }
               if (s.getInvitationRestriction() == TBSet.HIGHER_RATING) { anyoneString += " over " + myRating; }
               if (s.getInvitationRestriction() == TBSet.SIMILAR_RATING) { anyoneString += " similar"; }
               if (s.getInvitationRestriction() == TBSet.CLASS_RATING) {
                  if (myRating >= 1900) { anyoneString += " red"; }
                  else if (myRating >= 1700) { anyoneString += " yellow"; }
                  else if (myRating >= 1400) { anyoneString += " blue"; }
                  else if (myRating >= 1000) { anyoneString += " green"; }
                  else { anyoneString += " gray"; }
               }
            }
            if (!firstSent) { %>,<% } firstSent = false;
%>{"setId":<%=s.getSetId()%>,"gameName":<%=jsonStr(GridStateFactory.getGameName(s.getGame1().getGame()))%>,"opponentName":<%=jsonStr((pid == 0) ? anyoneString : d.getName())%>,"opponentRating":<%=((dsgPlayerGameData != null) ? (int) Math.round(dsgPlayerGameData.getRating()) : 1600)%>,"color":<%=jsonStr(color)%>,"daysPerMove":<%=s.getGame1().getDaysPerMove()%>,"rated":<%=jsonStr(ratedStr)%>,"opponentColor":<%=((pid == 0) ? 0 : (d.hasPlayerDonated()?((d.getNameColorRGB() & 0xFFFFFF)==0?((255<<24)+1):d.getNameColorRGB()):0))%>,"opponentTourneyWinner":<%=((pid == 0) ? 0 : d.getTourneyWinner())%>}<%
         }
%>],"activeGamesMyTurn":[<%
         boolean firstMyTurn = true;
         for (TBGame g : myTurn) {
            String color = "";
            boolean go = g.getGame() == GridStateFactory.TB_GO ||
                         g.getGame() == GridStateFactory.TB_GO9 ||
                         g.getGame() == GridStateFactory.TB_GO13;
            if (g.getPlayer1Pid() == myPID) {
               color = (!go)?"white (p1)":"black (p1)";
            } else {
               color = (go)?"white (p2)":"black (p2)";
            }
            boolean koth = g.getEventId() == kothStorer.getEventId(g.getGame());
            boolean tourney = false;
            if (!koth) {
               for (Tourney tmpTourney : currentTournies) {
                  if (tmpTourney.getEventID() == g.getEventId()) { tourney = true; break; }
               }
            }
            String ratedStr = "Not Rated";
            if (koth) { ratedStr = "KotH"; } else if (tourney) { ratedStr = "Tournament"; } else if (g.isRated()) { ratedStr = "Rated"; }
            long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
            DSGPlayerData d = dsgPlayerStorer.loadPlayer(oppPid);
            DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(g.getGame());
            if (!firstMyTurn) { %>,<% } firstMyTurn = false;
%>{"gid":<%=g.getGid()%>,"gameName":<%=jsonStr(GridStateFactory.getGameName(g.getGame()))%>,"opponentName":<%=jsonStr(d.getName())%>,"opponentRating":<%=(int) Math.round(dsgPlayerGameData.getRating())%>,"color":<%=jsonStr(color)%>,"numMoves":<%=(g.getNumMoves() + 1)%>,"timeLeft":<%=jsonStr(Utilities.getTimeLeft(g.getTimeoutDate().getTime()))%>,"rated":<%=jsonStr(ratedStr)%>,"opponentColor":<%=(d.hasPlayerDonated()?((d.getNameColorRGB() & 0xFFFFFF)==0?((255<<24)+1):d.getNameColorRGB()):0)%>,"opponentTourneyWinner":<%=d.getTourneyWinner()%>}<%
         }
%>],"activeGamesOpponentTurn":[<%
         boolean firstOppTurn = true;
         for (TBGame g : oppTurn) {
            String color = "";
            boolean go = g.getGame() == GridStateFactory.TB_GO ||
                         g.getGame() == GridStateFactory.TB_GO9 ||
                         g.getGame() == GridStateFactory.TB_GO13;
            if (g.getPlayer1Pid() == myPID) {
               color = (!go)?"white (p1)":"black (p1)";
            } else {
               color = (go)?"white (p2)":"black (p2)";
            }
            boolean koth = g.getEventId() == kothStorer.getEventId(g.getGame());
            boolean tourney = false;
            if (!koth) {
               for (Tourney tmpTourney : currentTournies) {
                  if (tmpTourney.getEventID() == g.getEventId()) { tourney = true; break; }
               }
            }
            String ratedStr = "Not Rated";
            if (koth) { ratedStr = "KotH"; } else if (tourney) { ratedStr = "Tournament"; } else if (g.isRated()) { ratedStr = "Rated"; }
            long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
            DSGPlayerData d = dsgPlayerStorer.loadPlayer(oppPid);
            DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(g.getGame());
            if (!firstOppTurn) { %>,<% } firstOppTurn = false;
%>{"gid":<%=g.getGid()%>,"gameName":<%=jsonStr(GridStateFactory.getGameName(g.getGame()))%>,"opponentName":<%=jsonStr(d.getName())%>,"opponentRating":<%=(int) Math.round(dsgPlayerGameData.getRating())%>,"color":<%=jsonStr(color)%>,"numMoves":<%=(g.getNumMoves() + 1)%>,"timeLeft":<%=jsonStr(Utilities.getTimeLeft(g.getTimeoutDate().getTime()))%>,"rated":<%=jsonStr(ratedStr)%>,"opponentColor":<%=(d.hasPlayerDonated()?((d.getNameColorRGB() & 0xFFFFFF)==0?((255<<24)+1):d.getNameColorRGB()):0)%>,"opponentTourneyWinner":<%=d.getTourneyWinner()%>}<%
         }
%>],"openInvitationGames":[<%
         boolean firstOpen = true;
         for (TBSet s : waitingSets) {
            String color = null;
            boolean go = s.getGame1().getGame() == GridStateFactory.TB_GO ||
                         s.getGame1().getGame() == GridStateFactory.TB_GO9 ||
                         s.getGame1().getGame() == GridStateFactory.TB_GO13;
            boolean koth = false;
            if (s.isTwoGameSet()) {
               color = "whiteblack";
            } else if (s.getPlayer2Pid() == 0) {
               color = (go)?"white (p2)":"black (p2)";
            } else {
               color = (!go)?"white (p1)":"black (p1)";
            }
            if (kothStorer.getEventId(s.getGame1().getGame()) == s.getGame1().getEventId()) { koth = true; }
            String ratedStr = "Not Rated";
            if (koth) { ratedStr = "KotH"; } else if (s.getGame1().isRated()) { ratedStr = "Rated"; }
            if (s.getInvitationRestriction() == TBSet.BEGINNER) { ratedStr = ratedStr + ", beginner"; }
            DSGPlayerData d = dsgPlayerStorer.loadPlayer(s.getInviterPid());
            DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(s.getGame1().getGame());
            if (!firstOpen) { %>,<% } firstOpen = false;
%>{"setId":<%=s.getSetId()%>,"gameName":<%=jsonStr(GridStateFactory.getGameName(s.getGame1().getGame()))%>,"inviterName":<%=jsonStr(d.getName())%>,"inviterRating":<%=(int) Math.round(dsgPlayerGameData.getRating())%>,"color":<%=jsonStr(color)%>,"daysPerMove":<%=s.getGame1().getDaysPerMove()%>,"rated":<%=jsonStr(ratedStr)%>,"inviterColor":<%=(d.hasPlayerDonated()?((d.getNameColorRGB() & 0xFFFFFF)==0?((255<<24)+1):d.getNameColorRGB()):0)%>,"inviterTourneyWinner":<%=d.getTourneyWinner()%>}<%
         }
%>],"messages":[<%
         int msgCount = 0;
         boolean firstMsg = true;
         for (DSGMessage m : messages) {
            msgCount += 1;
            if (msgCount > 50) { break; }
            DSGPlayerData from = dsgPlayerStorer.loadPlayer(m.getFromPid());
            if (!firstMsg) { %>,<% } firstMsg = false;
%>{"mid":<%=m.getMid()%>,"read":<%=m.isRead()%>,"subject":<%=jsonStr(m.getSubject())%>,"from":<%=jsonStr(from.getName())%>,"date":<%=jsonStr(messageDateFormat.format(m.getCreationDate()))%>,"fromColor":<%=(from.hasPlayerDonated()?((from.getNameColorRGB() & 0xFFFFFF)==0?((255<<24)+1):from.getNameColorRGB()):0)%>,"fromTourneyWinner":<%=from.getTourneyWinner()%>}<%
         }
%>],"tournaments":[<%
         boolean firstTourney = true;
         for (Tourney tmpTourney : (List<Tourney>) tourneyStorer.getUpcomingTournies()) {
            Tourney tourney = tourneyStorer.getTourney(tmpTourney.getEventID());
            if (!firstTourney) { %>,<% } firstTourney = false;
%>{"name":<%=jsonStr(tourney.getName())%>,"eventId":<%=tourney.getEventID()%>,"numRounds":<%=tourney.getNumRounds()%>,"gameName":<%=jsonStr((tourney.isTurnBased()?"tb-":"") + GridStateFactory.getGameName(tourney.getGame()))%>,"status":1,"date":<%=jsonStr(dateFormat.format(tourney.getSignupEndDate()))%>}<%
         }
         for (Tourney tmpTourney : currentTournies) {
            Tourney tourney = tourneyStorer.getTourney(tmpTourney.getEventID());
            if (!firstTourney) { %>,<% } firstTourney = false;
%>{"name":<%=jsonStr(tourney.getName())%>,"eventId":<%=tourney.getEventID()%>,"numRounds":<%=tourney.getNumRounds()%>,"gameName":<%=jsonStr((tourney.isTurnBased()?"tb-":"") + GridStateFactory.getGameName(tourney.getGame()))%>,"status":<%=(tourney.getNumRounds()==0?2:3)%>,"date":<%=jsonStr(dateFormat.format(tourney.getStartDate()))%>}<%
         }
%>],"onlinePlayers":[<%
         boolean firstOnline = true;
         for (String playerName : onlinePlayerNames) {
            if (!firstOnline) { %>,<% } firstOnline = false;
%><%=jsonStr(playerName)%><%
         }
%>]}<%
      } else {
%>{"error":"Invalid name or password, please try again."}<%
      }
   } else {
%>{"error":"Invalid name or password, please try again."}<%
   }
%>