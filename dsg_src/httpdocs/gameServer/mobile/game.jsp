<%@ page import="org.pente.database.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.server.*, org.pente.game.*, org.pente.turnBased.*,
                 org.pente.turnBased.web.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*,
                 java.text.*,
                 java.util.*" %>
<%@ page contentType="text/html; charset=UTF-8" %>

<%
String loggedInStr = (String) request.getAttribute("name");
 if (loggedInStr == null) {
    response.sendRedirect("../index.jsp");
   } %>

<%  
com.jivesoftware.base.FilterChain filters = 
    new com.jivesoftware.base.FilterChain(
        null, 1, new com.jivesoftware.base.Filter[] { 
            new HTMLFilter(), new URLConverter(), new TBEmoticon(), new Newline() }, 
            new long[] { 1, 1, 1, 1 });


    DBHandler dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());
    ServletContext ctx = getServletContext();
    Resources resources = (Resources) application.getAttribute(Resources.class.getName());
    TBGameStorer tbGameStorer = resources.getTbGameStorer();
    String gidString = (String) request.getParameter("gid");
    TBGame  tbGame = tbGameStorer.loadGame(Long.parseLong(gidString));
    TBSet set = tbGame.getTbSet();

%>gid=<%=gidString%>

private=<%=(set.isPrivateGame()?"":"non-")+"private"%>

rated=<%=(tbGame.isRated()?"":"not ")+"Rated"%>
<%    


%>sid=<%=set.getSetId()%>

<%


String moves = "";
String messages = "";
String moveNums = "";
String seqNums = "";
String dates = "";
String players = ""; //indicates which seat made message
String tmpMsgs = "";
for (int i = 0; i < tbGame.getNumMoves(); i++) {
    moves += tbGame.getMove(i) + ",";
}
if (!"".equals(moves)) {
    tmpMsgs = moves.substring(0, moves.length() - 1);
    moves = tmpMsgs;
}

%>moves=<%=moves%>
<%

if (!tbGame.isCompleted()) {
    for (TBMessage m : tbGame.getMessages()) {
        // bug in URLConverter
        if (m.getMessage().length() == 1) {
            messages += m.getMessage() + ",";
        } else {
            messages += MessageEncoder.encodeMessage(
                filters.applyFilters(0, m.getMessage())) + ",";
        }
        seqNums += m.getSeqNbr() + ",";
        moveNums += m.getMoveNum() + ",";
        dates += m.getDate().getTime() + ",";
        if (tbGame.getPlayer1Pid() == m.getPid()) {
            players += "1,";
        }
        else {
            players += "2,";
        }
    }
}
if (!"".equals(messages)) {
//    tmpMsgs = messages.substring(0, messages.length() - 1);
    tmpMsgs = messages.substring(0, messages.length() - 1).replace("\\2","'");
    messages = tmpMsgs;
}

%>messages=<%=messages%>
<%

if (!"".equals(moveNums)) {
    tmpMsgs = moveNums.substring(0, moveNums.length() - 1);
    moveNums = tmpMsgs;
}

%>messageNums=<%=moveNums%>

gameName=<%=GridStateFactory.getGameName(tbGame.getGame())%>
<%



DSGPlayerStorer dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());
DSGPlayerData player1 = dsgPlayerStorer.loadPlayer(tbGame.getPlayer1Pid());
DSGPlayerGameData p1Data = player1.getPlayerGameData(tbGame.getGame());

%>player1=<%=player1.getName() + "," + ((int) p1Data.getRating()) %>
<%

player1 = dsgPlayerStorer.loadPlayer(tbGame.getPlayer2Pid());
p1Data = player1.getPlayerGameData(tbGame.getGame());

%>player2=<%=player1.getName() + "," + ((int) p1Data.getRating()) %>
<%


if (set.getCancelPid() != 0) {

player1 = dsgPlayerStorer.loadPlayer(set.getCancelPid());

%>cancel=<%=player1.getName() + "," + set.getCancelMsg().replace("\\2","'")%>

<%
    
}

if (!tbGame.isCompleted() && (tbGame.getGame() == GridStateFactory.TB_DPENTE)) {

%>dPenteState=<%=tbGame.getDPenteState()%>
<%

}




// DateFormat messageDateFormat = null;
// TimeZone tz = TimeZone.getTimeZone(dsgPlayerData.getTimezone());
// messageDateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm aa z");
// messageDateFormat.setTimeZone(tz);
// Collections.sort(messages, new Comparator<DSGMessage>() {
//     public int compare(DSGMessage m1, DSGMessage m2) {
//         return (m2.getMid() - m1.getMid());
//     }
// });

%>

EndOfSettingsParameters
