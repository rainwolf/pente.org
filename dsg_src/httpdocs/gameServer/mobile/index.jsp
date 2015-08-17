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


if (dsgPlayerData.unlimitedTBGames() || dsgPlayerData.unlimitedMobileTBGames() ) { %>
Unlimited Games
<%} else { %>
tbGamesLimit;<%=ctx.getInitParameter("TBGamesLimit")%>;tbGamesLimit
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
<%=s.getSetId() + ";" + GridStateFactory.getGameName(s.getGame1().getGame()) + ";" + d.getName() + ";" + (int) Math.round(dsgPlayerGameData.getRating()) + ";" +  color + ";" + s.getGame1().getDaysPerMove() + " days;" + (s.getGame1().isRated() ? "Rated" : "Not Rated")%><%} 
     



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
                 if (pid != 0) {
                     d = dsgPlayerStorer.loadPlayer(pid);
                     dsgPlayerGameData = d.getPlayerGameData(s.getGame1().getGame());
                 } %>
<%=s.getSetId() + ";" + GridStateFactory.getGameName(s.getGame1().getGame()) + ";" + ((pid == 0) ? "Anyone":d.getName()) + ";" + ((dsgPlayerGameData != null)?(int) Math.round(dsgPlayerGameData.getRating()):"1600") + ";" +  color + ";" + s.getGame1().getDaysPerMove() + " days;" + (s.getGame1().isRated() ? "Rated" : "Not Rated")%><%} 

     


%>
Active Games - My Turn<%
        for (TBGame g : myTurn) {
                String color =  myPID == g.getPlayer1Pid() ?
                 "white (p1)" : "black (p2)";
                long oppPid = myPID == g.getPlayer1Pid() ?
                 g.getPlayer2Pid() : g.getPlayer1Pid();
                DSGPlayerData d = dsgPlayerStorer.loadPlayer(oppPid);
                DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(g.getGame());%>
<%=g.getGid() + ";" + GridStateFactory.getGameName(g.getGame()) + ";" + d.getName() + ";" + (int) Math.round(dsgPlayerGameData.getRating()) + ";" +  color + ";" + g.getNumMoves() + 1 + ";" + Utilities.getTimeLeft(g.getTimeoutDate().getTime()) +";" + (g.isRated() ? "Rated" : "Not Rated")%><%}
     



%>
Active Games - Opponents Turn<%
        for (TBGame g : oppTurn) {
                String color =  myPID == g.getPlayer1Pid() ?
                 "white (p1)" : "black (p2)";
                long oppPid = myPID == g.getPlayer1Pid() ?
                 g.getPlayer2Pid() : g.getPlayer1Pid();
                DSGPlayerData d = dsgPlayerStorer.loadPlayer(oppPid);
                DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(g.getGame());%>
<%=g.getGid() + ";" + GridStateFactory.getGameName(g.getGame()) + ";" + d.getName() + ";" + (int) Math.round(dsgPlayerGameData.getRating()) + ";" +  color + ";" + g.getNumMoves() + 1 + ";" + Utilities.getTimeLeft(g.getTimeoutDate().getTime()) +";" + (g.isRated() ? "Rated" : "Not Rated")%><%} 
     




%>
Open Invitation Games<%
        for (TBSet s : waitingSets) {
                if (s.getPlayer1Pid() == myPID || s.getPlayer2Pid() == myPID) {
                    continue;
                }
                boolean alreadyPlaying = false, iAmIgnored = false;
                long theirPID = (myPID == s.getPlayer1Pid()) ? s.getPlayer2Pid() : s.getPlayer1Pid();

                if (!"rainwolf".equals(name)) {
                    int nrGamesPlaying = 0;
                    String setGame = GridStateFactory.getGameName(s.getGame1().getGame());
                    for (TBGame g : myTurn) {
                        long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
                        String myTurnGame = GridStateFactory.getGameName(g.getGame());
                        if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
                            nrGamesPlaying++;
                            if (nrGamesPlaying > 0) {
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
                                if (nrGamesPlaying > 0) {
                                    alreadyPlaying = true;
                                    break;
                                }
                            };
                        };
                    }
                    if (alreadyPlaying) {
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
                    continue;
                }
            
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
<%=s.getSetId() + ";" + GridStateFactory.getGameName(s.getGame1().getGame()) + ";" + d.getName() + ";" + (int) Math.round(dsgPlayerGameData.getRating()) + ";" +  color + ";" + s.getGame1().getDaysPerMove() + " days;" + (s.getGame1().isRated() ? "Rated" : "Not Rated")%><%}




%>
Messages<%
   for (DSGMessage m : messages) {
       DSGPlayerData from = dsgPlayerStorer.loadPlayer(m.getFromPid()); %>
<%=m.getMid() + ";" +(m.isRead() ? "read" : "unread") + ";" + m.getSubject() + ";" + from.getName() + ";" + messageDateFormat.format(m.getCreationDate())%><%}%>




 <%
        }
    } else {
        %>Invalid name or password, please try again. <%
    }


%>

