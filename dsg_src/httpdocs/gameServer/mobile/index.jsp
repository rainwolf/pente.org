<%@ page import="org.pente.database.*,
                 org.pente.game.*,
                 org.pente.turnBased.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.server.*,
                 org.pente.message.*,
                 java.text.*,
                 java.sql.*, 
                 javapns.Push,
                 javapns.devices.*, 
                 java.util.Date,
                 java.util.List,
                 java.util.*,
                 org.apache.log4j.*" 
%> <%! private static Category log4j = 
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
messageDateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm aa z");
messageDateFormat.setTimeZone(tz);
Collections.sort(messages, new Comparator<DSGMessage>() {
    public int compare(DSGMessage m1, DSGMessage m2) {
        return (m2.getMid() - m1.getMid());
    }
});

int openTBgames = 0;
int concurrentPlayLimit = 2;
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
    String setGame = GridStateFactory.getGameName(s.getGame1().getGame());
    boolean alreadyPlaying = false, iAmIgnored = false;
    long theirPID = (myPID == s.getPlayer1Pid()) ? s.getPlayer2Pid() : s.getPlayer1Pid();
    for (TBGame g : myTurn) {
        long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
        String myTurnGame = GridStateFactory.getGameName(g.getGame());
        if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
            nrGamesPlaying++;
            if (nrGamesPlaying > concurrentPlayLimit) {
                alreadyPlaying = true;
                break;
            }
        };
    };
    if (!alreadyPlaying) {
        for (TBGame g : oppTurn) {
            long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
            String myTurnGame = GridStateFactory.getGameName(g.getGame());
            if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
                nrGamesPlaying++;
                if (nrGamesPlaying > concurrentPlayLimit) {
                    alreadyPlaying = true;
                    break;
                }
            };
        };
    }

    if (alreadyPlaying && !"rainwolf".equals(name)) {
        openTBgames--;
        iterator.remove();
        continue;
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
    DSGPlayerData oppData = dsgPlayerStorer.loadPlayer(theirPID);
    DSGPlayerGameData oppGameData = oppData.getPlayerGameData(s.getGame1().getGame());
    int oppRating = 1200;
    if (oppGameData != null && oppGameData.getTotalGames() > 0) {
        oppRating = (int) Math.round(oppGameData.getRating());
    }
    if (s.getInvitationRestriction() == TBSet.LOWER_RATING) {
        if (myRating >= oppRating) {
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
    int delta = 75;
    if (s.getInvitationRestriction() == TBSet.SIMILAR_RATING) {
        if ((myRating + delta < oppRating) || (myRating - delta > oppRating)) {
            openTBgames--;
            iterator.remove();
        }
        continue;
    }
    if (s.getInvitationRestriction() == TBSet.CLASS_RATING) {
        if (1900 <= myRating && 1900 > oppRating) {
            openTBgames--;
            iterator.remove();
            continue;
        }
        if (1700 <= myRating && (oppRating < 1700 || oppRating >= 1900)) {
            openTBgames--;
            iterator.remove();
            continue;
        }
        if (1400 <= myRating && (oppRating < 1400 || oppRating >= 1700)) {
            openTBgames--;
            iterator.remove();
            continue;
        }
        if (1000 <= myRating && (oppRating < 1000 || oppRating >= 1400)) {
            openTBgames--;
            iterator.remove();
            continue;
        }
        if (1000 > myRating && oppRating >= 1000) {
            openTBgames--;
            iterator.remove();
            continue;
        }
    }
}


if (dsgPlayerData.unlimitedTBGames() || dsgPlayerData.unlimitedMobileTBGames() ) { %>
Unlimited Games
<%} else { %>
Unlimited Games
tb GamesLimit;<%=ctx.getInitParameter("TBGamesLimit")%>;tb GamesLimit
<%}
if (!dsgPlayerData.showAds()) { %>
No Ads
<%}%>

EndOfSettingsParameters

Invitations received<%
        for (TBSet s : invitesTo) {
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
                 DSGPlayerData d = dsgPlayerStorer.loadPlayer(s.getInviterPid());
                 DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(s.getGame1().getGame());%>
<%=s.getSetId() + ";" + GridStateFactory.getGameName(s.getGame1().getGame()) + ";" + d.getName() + ";" + (int) Math.round(dsgPlayerGameData.getRating()) + ";" +  color + ";" + s.getGame1().getDaysPerMove() + " days;" + (s.getGame1().isRated() ? "Rated" : "Not Rated") + ";" + (d.hasPlayerDonated()?d.getNameColorRGB():0)%><%} 
     



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
<%=s.getSetId() + ";" + GridStateFactory.getGameName(s.getGame1().getGame()) + ";" + ((pid == 0) ? anyoneString:d.getName()) + ";" + ((dsgPlayerGameData != null)?(int) Math.round(dsgPlayerGameData.getRating()):"1600") + ";" +  color + ";" + s.getGame1().getDaysPerMove() + " days;" + (s.getGame1().isRated() ? "Rated" : "Not Rated") + ";" + ((pid == 0)?"0":(d.hasPlayerDonated()?d.getNameColorRGB():0))%><%} 

     


%>
Active Games - My Turn<%
        for (TBGame g : myTurn) {
                String color =  myPID == g.getPlayer1Pid() ?
                 "white (p1)" : "black (p2)";
                long oppPid = myPID == g.getPlayer1Pid() ?
                 g.getPlayer2Pid() : g.getPlayer1Pid();
                DSGPlayerData d = dsgPlayerStorer.loadPlayer(oppPid);
                DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(g.getGame());%>
<%=g.getGid() + ";" + GridStateFactory.getGameName(g.getGame()) + ";" + d.getName() + ";" + (int) Math.round(dsgPlayerGameData.getRating()) + ";" +  color + ";" + g.getNumMoves() + 1 + ";" + Utilities.getTimeLeft(g.getTimeoutDate().getTime()) +";" + (g.isRated() ? "Rated" : "Not Rated") + ";" + (d.hasPlayerDonated()?d.getNameColorRGB():0)%><%}
     



%>
Active Games - Opponents Turn<%
        for (TBGame g : oppTurn) {
                String color =  myPID == g.getPlayer1Pid() ?
                 "white (p1)" : "black (p2)";
                long oppPid = myPID == g.getPlayer1Pid() ?
                 g.getPlayer2Pid() : g.getPlayer1Pid();
                DSGPlayerData d = dsgPlayerStorer.loadPlayer(oppPid);
                DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(g.getGame());%>
<%=g.getGid() + ";" + GridStateFactory.getGameName(g.getGame()) + ";" + d.getName() + ";" + (int) Math.round(dsgPlayerGameData.getRating()) + ";" +  color + ";" + g.getNumMoves() + 1 + ";" + Utilities.getTimeLeft(g.getTimeoutDate().getTime()) +";" + (g.isRated() ? "Rated" : "Not Rated") + ";" + (d.hasPlayerDonated()?d.getNameColorRGB():0)%><%} 
     




%>
Open Invitation Games<%
        for (TBSet s : waitingSets) {
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
                 DSGPlayerData d = dsgPlayerStorer.loadPlayer(s.getInviterPid());
                 DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(s.getGame1().getGame());%>
<%=s.getSetId() + ";" + GridStateFactory.getGameName(s.getGame1().getGame()) + ";" + d.getName() + ";" + (int) Math.round(dsgPlayerGameData.getRating()) + ";" +  color + ";" + s.getGame1().getDaysPerMove() + " days;" + (s.getGame1().isRated() ? "Rated" : "Not Rated") + ";" + (d.hasPlayerDonated()?d.getNameColorRGB():0)%><%}




%>
Messages<%
   for (DSGMessage m : messages) {
       DSGPlayerData from = dsgPlayerStorer.loadPlayer(m.getFromPid()); %>
<%=m.getMid() + ";" +(m.isRead() ? "read" : "unread") + ";" + m.getSubject() + ";" + from.getName() + ";" + messageDateFormat.format(m.getCreationDate()) + ";" + (from.hasPlayerDonated()?from.getNameColorRGB():0)%><%}%>




 <%
        }
    } else {
        %>Invalid name or password, please try again. <%
    }


%>

