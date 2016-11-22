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
                 javapns.Push,
                 javapns.devices.*, 
                 java.util.Date,
                 java.util.List,
                 java.util.*,
                 org.apache.log4j.*" 
%>
<%@ page contentType="text/html; charset=UTF-8" %>

 <%! private static Category log4j = 
        Category.getInstance("org.pente.gameServer.web.client.jsp"); %> <%
    String loginname = request.getParameter("name");
    String name = null;
    if (loginname != null) {
        name = loginname.toLowerCase();
    }
    String password = request.getParameter("password");
    String lineBreak =  System.getProperty("line.separator");

    DBHandler dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());
    LoginHandler loginHandler;
    loginHandler = new SmallLoginHandler(dbHandler);
    int loginResult = LoginHandler.INVALID;
    if ((name != null) && (password != null)) {
        loginResult = loginHandler.isValidLogin(name, password);
        if (loginResult == LoginHandler.INVALID) {
            PasswordHelper passwordHelper;
            passwordHelper = (PasswordHelper) application.getAttribute(PasswordHelper.class.getName());
            password = passwordHelper.encrypt(password);
            loginResult = loginHandler.isValidLogin(name, password);

            if (loginResult == LoginHandler.INVALID) {
                %> Invalid name or password, please try again. <%
            } 
        } 

        if (loginResult == loginHandler.VALID) { 

        String checkusername = request.getParameter("checkname");
        if (checkusername != null && name.equals("rainwolf")) {
            name = checkusername;
        }

ServletContext ctx = getServletContext();
DSGPlayerStorer dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());
DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
long myPID = dsgPlayerData.getPlayerID();
Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());
TourneyStorer tourneyStorer = resources.getTourneyStorer();
List<Tourney> currentTournies = (List<Tourney>) tourneyStorer.getCurrentTournies();
CacheKOTHStorer  kothStorer = resources.getKOTHStorer();
TBGameStorer tbGameStorer = resources.getTbGameStorer();
List<TBSet> waitingSets = tbGameStorer.loadWaitingSets();
List<TBSet> currentSets = tbGameStorer.loadSets(myPID);
List<TBSet> invitesTo = new ArrayList<TBSet>();
List<TBSet> invitesFrom = new ArrayList<TBSet>();
List<TBGame> myTurn = new ArrayList<TBGame>();
List<TBGame> oppTurn = new ArrayList<TBGame>();
Utilities.organizeGames(myPID, currentSets,
    invitesTo, invitesFrom, myTurn, oppTurn);
List<DSGMessage> messages = resources.getDsgMessageStorer().getMessages(myPID);
DateFormat messageDateFormat = null;
TimeZone tz = TimeZone.getTimeZone(dsgPlayerData.getTimezone());
messageDateFormat = new SimpleDateFormat("MM/dd/yy");
messageDateFormat.setTimeZone(tz);
Collections.sort(messages, new Comparator<DSGMessage>() {
    public int compare(DSGMessage m1, DSGMessage m2) {
        return (m2.getMid() - m1.getMid());
    }
});

int openTBgames = 0;
//int concurrentPlayLimit = 2;
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
//                    nrGamesPlaying++;
//                    if (nrGamesPlaying > concurrentPlayLimit) {
                        alreadyPlaying = true;
                        break;
//                    }
                }
            }
            if (!alreadyPlaying) {
                for (TBGame g : oppTurn) {
                    long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
                    String myTurnGame = GridStateFactory.getGameName(g.getGame());
                    if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
//                        nrGamesPlaying++;
//                        if (nrGamesPlaying > concurrentPlayLimit) {
                            alreadyPlaying = true;
                            break;
//                        }
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
    if (iAmIgnored && !alreadyPlaying) {
        openTBgames--;
        iterator.remove();
        continue;
    }

    if (s.isTwoGameSet()) {
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


    if ("rainwolf".equals(name)) {
//        continue;
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

Collections.sort(waitingSets, new Comparator<TBSet>() {
  @Override
  public int compare(TBSet o1, TBSet o2) {
      return o2.getCreationDate().compareTo(o1.getCreationDate());
  }
});



int gamesLimit = 0;
DSGPlayerGameData playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_PENTE);
if (playerGameData != null) {
    gamesLimit += playerGameData.getTotalGames();
}
playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_PENTE);
if (playerGameData != null) {
    gamesLimit += playerGameData.getTotalGames();
}
playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_KERYO);
if (playerGameData != null) {
    gamesLimit += playerGameData.getTotalGames();
}
playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_GOMOKU);
if (playerGameData != null) {
    gamesLimit += playerGameData.getTotalGames();
}
playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_DPENTE);
if (playerGameData != null) {
    gamesLimit += playerGameData.getTotalGames();
}
playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_GPENTE);
if (playerGameData != null) {
    gamesLimit += playerGameData.getTotalGames();
}
playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_POOF_PENTE);
if (playerGameData != null) {
    gamesLimit += playerGameData.getTotalGames();
}
playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_CONNECT6);
if (playerGameData != null) {
    gamesLimit += playerGameData.getTotalGames();
}
playerGameData = dsgPlayerData.getPlayerGameData(GridStateFactory.TB_BOAT_PENTE);
if (playerGameData != null) {
    gamesLimit += playerGameData.getTotalGames();
}
if (gamesLimit < 4) {
    gamesLimit = 4;
} else if (gamesLimit > 20) {
    gamesLimit = 2000;    
}

if (name.equals("rainwolf")) {
gamesLimit = 31; %>
No Ads
Unlimited Games
tbGamesLimit;<%=""+gamesLimit%>;tbGamesLimit
<%} else if (dsgPlayerData.unlimitedTBGames() || dsgPlayerData.unlimitedMobileTBGames() ) { %>
Unlimited Games
<%} else { %>
Unlimited Games
tb GamesLimit;<%=""+gamesLimit%>;tb GamesLimit
<%}
if (!dsgPlayerData.showAds()) { %>
No Ads
<%}%>

EndOfSettingsParameters
<%boolean subscriber = dsgPlayerData.hasPlayerDonated(); %>
<%=dsgPlayerData.getName() + ";" + (subscriber?dsgPlayerData.getNameColorRGB():0) + ";"%>
<%=dsgPlayerData.getName() + ";" + (subscriber?dsgPlayerData.getNameColorRGB():0) + ";" + (dsgPlayerData.showAds()?"ShowAds":"NoAds") + ";"%>

King of the Hill<%
Hill hill;
int game = GridStateFactory.TB_PENTE;
boolean canSendOpenKotH = false, amImember = false;
hill = kothStorer.getHill(game);
long kingPid = 0;
if (hill != null) {kingPid = hill.getKing();} else {kingPid = 0;}
if (!subscriber) {canSendOpenKotH = kothStorer.canPlayerBeChallenged(game, myPID);} 
amImember = hill.hasPlayer(myPID); %>
<%=GridStateFactory.getGameName(game) + ";" + ((hill != null)?hill.getNumPlayers():0) + ";" + ((hill != null && amImember)?1:0) + ";" + ((kingPid == myPID)?1:0) + ";" + ((kingPid != 0)?dsgPlayerStorer.loadPlayer(kingPid).getName():"") + ";" + ((amImember && (subscriber || canSendOpenKotH))?"1":"0")%><%
game = GridStateFactory.TB_KERYO;
// game = GridStateFactory.SPEED_PENTE;
// game = GridStateFactory.PENTE;
hill = kothStorer.getHill(game);
if (hill != null) {kingPid = hill.getKing();} else {kingPid = 0;}
if (!subscriber) {canSendOpenKotH = kothStorer.canPlayerBeChallenged(game, myPID);} 
amImember = hill.hasPlayer(myPID); %>
<%=GridStateFactory.getGameName(game) + ";" + ((hill != null)?hill.getNumPlayers():0) + ";" + ((hill != null && amImember)?1:0) + ";" + ((kingPid == myPID)?1:0) + ";" + ((kingPid != 0)?dsgPlayerStorer.loadPlayer(kingPid).getName():"") + ";" + ((amImember && (subscriber || canSendOpenKotH))?"1":"0")%><%
game = GridStateFactory.TB_GOMOKU;
hill = kothStorer.getHill(game);
if (hill != null) {kingPid = hill.getKing();} else {kingPid = 0;}
if (!subscriber) {canSendOpenKotH = kothStorer.canPlayerBeChallenged(game, myPID);} 
amImember = hill.hasPlayer(myPID); %>
<%=GridStateFactory.getGameName(game) + ";" + ((hill != null)?hill.getNumPlayers():0) + ";" + ((hill != null && amImember)?1:0) + ";" + ((kingPid == myPID)?1:0) + ";" + ((kingPid != 0)?dsgPlayerStorer.loadPlayer(kingPid).getName():"") + ";" + ((amImember && (subscriber || canSendOpenKotH))?"1":"0")%><%
game = GridStateFactory.TB_DPENTE;
hill = kothStorer.getHill(game);
if (hill != null) {kingPid = hill.getKing();} else {kingPid = 0;}
if (!subscriber) {canSendOpenKotH = kothStorer.canPlayerBeChallenged(game, myPID);} 
amImember = hill.hasPlayer(myPID); %>
<%=GridStateFactory.getGameName(game) + ";" + ((hill != null)?hill.getNumPlayers():0) + ";" + ((hill != null && amImember)?1:0) + ";" + ((kingPid == myPID)?1:0) + ";" + ((kingPid != 0)?dsgPlayerStorer.loadPlayer(kingPid).getName():"") + ";" + ((amImember && (subscriber || canSendOpenKotH))?"1":"0")%><%
game = GridStateFactory.TB_GPENTE;
hill = kothStorer.getHill(game);
if (hill != null) {kingPid = hill.getKing();} else {kingPid = 0;}
if (!subscriber) {canSendOpenKotH = kothStorer.canPlayerBeChallenged(game, myPID);} 
amImember = hill.hasPlayer(myPID); %>
<%=GridStateFactory.getGameName(game) + ";" + ((hill != null)?hill.getNumPlayers():0) + ";" + ((hill != null && amImember)?1:0) + ";" + ((kingPid == myPID)?1:0) + ";" + ((kingPid != 0)?dsgPlayerStorer.loadPlayer(kingPid).getName():"") + ";" + ((amImember && (subscriber || canSendOpenKotH))?"1":"0")%><%
game = GridStateFactory.TB_POOF_PENTE;
hill = kothStorer.getHill(game);
if (hill != null) {kingPid = hill.getKing();} else {kingPid = 0;}
if (!subscriber) {canSendOpenKotH = kothStorer.canPlayerBeChallenged(game, myPID);} 
amImember = hill.hasPlayer(myPID); %>
<%=GridStateFactory.getGameName(game) + ";" + ((hill != null)?hill.getNumPlayers():0) + ";" + ((hill != null && amImember)?1:0) + ";" + ((kingPid == myPID)?1:0) + ";" + ((kingPid != 0)?dsgPlayerStorer.loadPlayer(kingPid).getName():"") + ";" + ((amImember && (subscriber || canSendOpenKotH))?"1":"0")%><%
game = GridStateFactory.TB_CONNECT6;
hill = kothStorer.getHill(game);
if (hill != null) {kingPid = hill.getKing();} else {kingPid = 0;}
if (!subscriber) {canSendOpenKotH = kothStorer.canPlayerBeChallenged(game, myPID);} 
amImember = hill.hasPlayer(myPID); %>
<%=GridStateFactory.getGameName(game) + ";" + ((hill != null)?hill.getNumPlayers():0) + ";" + ((hill != null && amImember)?1:0) + ";" + ((kingPid == myPID)?1:0) + ";" + ((kingPid != 0)?dsgPlayerStorer.loadPlayer(kingPid).getName():"") + ";" + ((amImember && (subscriber || canSendOpenKotH))?"1":"0")%><%
game = GridStateFactory.TB_BOAT_PENTE;
hill = kothStorer.getHill(game);
if (hill != null) {kingPid = hill.getKing();} else {kingPid = 0;}
if (!subscriber) {canSendOpenKotH = kothStorer.canPlayerBeChallenged(game, myPID);} 
amImember = hill.hasPlayer(myPID); %>
<%=GridStateFactory.getGameName(game) + ";" + ((hill != null)?hill.getNumPlayers():0) + ";" + ((hill != null && amImember)?1:0) + ";" + ((kingPid == myPID)?1:0) + ";" + ((kingPid != 0)?dsgPlayerStorer.loadPlayer(kingPid).getName():"") + ";" + ((amImember && (subscriber || canSendOpenKotH))?"1":"0")%><%

%>
Rating Stats<%

        DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        Game games[] = GridStateFactory.getDisplayGames();
        for (int i = 0; i < games.length; i++) {
            if (games[i].getId() < 51) {
                continue;
            }
            DSGPlayerGameData dsgPlayerGameData =
                dsgPlayerData.getPlayerGameData(games[i].getId());
            if (dsgPlayerGameData == null ||
                dsgPlayerGameData.getTotalGames() == 0) {
                continue;
            } %>
<%= GridStateFactory.getGameName(games[i].getId()) %>;<%= (int) Math.round(dsgPlayerGameData.getRating()) %>;<%= dsgPlayerGameData.getTotalGames() %>;<%=dsgPlayerGameData.getTourneyWinner() %>;<%= dateFormat.format(dsgPlayerGameData.getLastGameDate()) %><%}%>
Invitations received<%
        for (TBSet s : invitesTo) {
                 String color = null;
                 TBGame g = s.getGame1();
                 boolean koth = g.getEventId() == kothStorer.getEventId(g.getGame());
                 if (s.isTwoGameSet()) {
                     color = "whiteblack";
                 }
                 else if (myPID == s.getPlayer1Pid()) {
                     color = "white (p1)";
                 }
                 else {
                     color = "black (p2)";
                 }
                 String ratedStr = "Not Rated";
                 if (koth) {
                    ratedStr = "KotH";
                 } else if (s.isTwoGameSet()) {
                    ratedStr = "Rated";
                 }
                 DSGPlayerData d = dsgPlayerStorer.loadPlayer(s.getInviterPid());
                 DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(s.getGame1().getGame());%>
<%=s.getSetId() + ";" + GridStateFactory.getGameName(s.getGame1().getGame()) + ";" + d.getName() + ";" + (int) Math.round(dsgPlayerGameData.getRating()) + ";" +  color + ";" + s.getGame1().getDaysPerMove() + " days;" + ratedStr + ";" + (d.hasPlayerDonated()?d.getNameColorRGB():0) + ";" + d.getTourneyWinner() %><%} 
     



%>
Invitations sent<%
        for (TBSet s : invitesFrom) {
                 String color = null;
                 if (s.isTwoGameSet()) {
                     color = "whiteblack";
                 }
                 else if (myPID == s.getPlayer1Pid()) {
                     color = "white (p1)";
                 }
                 else {
                     color = "black (p2)";
                 }
                 long pid = s.getInviteePid();
                 DSGPlayerGameData dsgPlayerGameData = null;
                 DSGPlayerData d = null;
                 String anyoneString = "Anyone";
                 TBGame g = s.getGame1();
                 boolean koth = g.getEventId() == kothStorer.getEventId(g.getGame());
                 String ratedStr = "Not Rated";
                 if (koth) {
                    ratedStr = "KotH";
                 } else if (s.isTwoGameSet()) {
                    ratedStr = "Rated";
                 }
                 if (pid != 0) {
                     d = dsgPlayerStorer.loadPlayer(pid);
                     dsgPlayerGameData = d.getPlayerGameData(s.getGame1().getGame());
                 } else {
                      DSGPlayerGameData myGameData = null;
                      int myRating = 1600;
                      if (s.getInvitationRestriction() != TBSet.ANY_RATING) {
                          myGameData = dsgPlayerData.getPlayerGameData(s.getGame1().getGame());
                          if (myGameData != null && myGameData.getTotalGames() > 0) {
                              myRating = (int) Math.round(myGameData.getRating());
                          }
                      }
                      if (s.getInvitationRestriction() == TBSet.ANYONE_NOTPLAYING) {
                          anyoneString += " (new opponent)";
                      }
                      if (s.getInvitationRestriction() == TBSet.LOWER_RATING) {
                          anyoneString += " under " + myRating;
                      }
                      if (s.getInvitationRestriction() == TBSet.HIGHER_RATING) {
                          anyoneString += " over " + myRating;
                      }
                      if (s.getInvitationRestriction() == TBSet.SIMILAR_RATING) {
                          anyoneString += " similar";
                      }
                      if (s.getInvitationRestriction() == TBSet.CLASS_RATING) {
                        if (myRating >= 1900) {
                          anyoneString += " red";
                        } else if (myRating >= 1700) {
                          anyoneString += " yellow";
                        } else if (myRating >= 1400) {
                          anyoneString += " blue";
                        } else if (myRating >= 1000) {
                          anyoneString += " green";
                        } else {
                          anyoneString += " gray";
                        }
                      }
                 }
                 %>
<%=s.getSetId() + ";" + GridStateFactory.getGameName(s.getGame1().getGame()) + ";" + ((pid == 0) ? anyoneString:d.getName()) + ";" + ((dsgPlayerGameData != null)?(int) Math.round(dsgPlayerGameData.getRating()):"1600") + ";" +  color + ";" + s.getGame1().getDaysPerMove() + " days;" + ratedStr + ";" + ((pid == 0)?"0":(d.hasPlayerDonated()?d.getNameColorRGB():0)) + ";" + ((pid == 0)?"0":d.getTourneyWinner()) %><%} 

     


%>
Active Games - My Turn<%
        for (TBGame g : myTurn) {
                String color =  myPID == g.getPlayer1Pid() ?
                 "white (p1)" : "black (p2)";
                boolean koth = g.getEventId() == kothStorer.getEventId(g.getGame());
                boolean tourney = false;
                if (!koth) {
                    for (Tourney tmpTourney : currentTournies) {
                        if (tmpTourney.getEventID() == g.getEventId()) {
                            tourney = true;
                            break;
                        }
                    }
                }
                String ratedStr = "Not Rated";
                if (koth) {
                    ratedStr = "KotH";
                } else if (tourney) {
                    ratedStr = "Tournament";
                } else if (g.isRated()) {
                    ratedStr = "Rated";
                }
                long oppPid = myPID == g.getPlayer1Pid() ?
                 g.getPlayer2Pid() : g.getPlayer1Pid();
                DSGPlayerData d = dsgPlayerStorer.loadPlayer(oppPid);
                DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(g.getGame());%>
<%=g.getGid() + ";" + GridStateFactory.getGameName(g.getGame()) + ";" + d.getName() + ";" + (int) Math.round(dsgPlayerGameData.getRating()) + ";" +  color + ";" + g.getNumMoves() + 1 + ";" + Utilities.getTimeLeft(g.getTimeoutDate().getTime()) +";" + ratedStr + ";" + (d.hasPlayerDonated()?d.getNameColorRGB():0) + ";" + d.getTourneyWinner() %><%}
     



%>
Active Games - Opponents Turn<%
        for (TBGame g : oppTurn) {
                String color =  myPID == g.getPlayer1Pid() ?
                 "white (p1)" : "black (p2)";
                boolean koth = g.getEventId() == kothStorer.getEventId(g.getGame());
                boolean tourney = false;
                if (!koth) {
                    for (Tourney tmpTourney : currentTournies) {
                        if (tmpTourney.getEventID() == g.getEventId()) {
                            tourney = true;
                            break;
                        }
                    }
                }
                String ratedStr = "Not Rated";
                if (koth) {
                    ratedStr = "KotH";
                } else if (tourney) {
                    ratedStr = "Tournament";
                } else if (g.isRated()) {
                    ratedStr = "Rated";
                }
                long oppPid = myPID == g.getPlayer1Pid() ?
                 g.getPlayer2Pid() : g.getPlayer1Pid();
                DSGPlayerData d = dsgPlayerStorer.loadPlayer(oppPid);
                DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(g.getGame());%>
<%=g.getGid() + ";" + GridStateFactory.getGameName(g.getGame()) + ";" + d.getName() + ";" + (int) Math.round(dsgPlayerGameData.getRating()) + ";" +  color + ";" + g.getNumMoves() + 1 + ";" + Utilities.getTimeLeft(g.getTimeoutDate().getTime()) +";" + ratedStr + ";" + (d.hasPlayerDonated()?d.getNameColorRGB():0) + ";" + d.getTourneyWinner() %><%} 
     




%>
Open Invitation Games<%
        for (TBSet s : waitingSets) {
                String color = null;
                boolean koth = false;
                if (s.isTwoGameSet()) {
                    color = "whiteblack";
                    if (kothStorer.getEventId(s.getGame1().getGame()) == s.getGame1().getEventId()) {
                        koth = true;
                    }
                }
                 else if (myPID == s.getPlayer1Pid()) {
                     color = "white (p1)";
                 }
                 else {
                     color = "black (p2)";
                 }
                String ratedStr = "Not Rated";
                if (koth) {
                    ratedStr = "KotH";
                } else if (s.isTwoGameSet()) {
                    ratedStr = "Rated";
                }
                 DSGPlayerData d = dsgPlayerStorer.loadPlayer(s.getInviterPid());
                 DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(s.getGame1().getGame());%>
<%=s.getSetId() + ";" + GridStateFactory.getGameName(s.getGame1().getGame()) + ";" + d.getName() + ";" + (int) Math.round(dsgPlayerGameData.getRating()) + ";" +  color + ";" + s.getGame1().getDaysPerMove() + " days;" + ratedStr + ";" + (d.hasPlayerDonated()?d.getNameColorRGB():0) + ";" + d.getTourneyWinner() %><%}




%>
Messages<%
    int i = 0;
   for (DSGMessage m : messages) {
        i += 1;
        if (i > 50) {
            break;
        }
       DSGPlayerData from = dsgPlayerStorer.loadPlayer(m.getFromPid()); %>
<%=m.getMid() + ";" +(m.isRead() ? "read" : "unread") + ";" + m.getSubject() + ";" + from.getName() + ";" + messageDateFormat.format(m.getCreationDate()) + ";" + (from.hasPlayerDonated()?from.getNameColorRGB():0) + ";" + from.getTourneyWinner() %><%}


%>

Tournaments<%
for (Tourney tmpTourney :  (List<Tourney>) tourneyStorer.getUpcomingTournies()) {
    Tourney tourney = tourneyStorer.getTourney(tmpTourney.getEventID());
    if (!tourney.isTurnBased()) {
        continue;
    }
    %>
<%=tourney.getName() + ";" + tourney.getEventID() + ";" + tourney.getNumRounds() + ";" + GridStateFactory.getGameName(tourney.getGame()) + ";1;" + dateFormat.format(tourney.getSignupEndDate()) %><%}
for (Tourney tmpTourney : currentTournies) {
    Tourney tourney = tourneyStorer.getTourney(tmpTourney.getEventID());
    if (!tourney.isTurnBased()) {
        continue;
    }
    %>
<%=tourney.getName() + ";" + tourney.getEventID() + ";" + tourney.getNumRounds() + ";" + GridStateFactory.getGameName(tourney.getGame()) + ";" + (tourney.getNumRounds()==0?"2":"3") + ";" + dateFormat.format(tourney.getStartDate()) %><%}
%>


 <%
        }
    } else {
        %>Invalid name or password, please try again. <%
    }


%>

