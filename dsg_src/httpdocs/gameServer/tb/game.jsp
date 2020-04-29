<%@ page import="org.pente.game.*, org.pente.turnBased.*,
                 org.pente.turnBased.web.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*" %>

<%  
com.jivesoftware.base.FilterChain filters = 
	new com.jivesoftware.base.FilterChain(
        null, 1, new com.jivesoftware.base.Filter[] { 
            new HTMLFilter(), new URLConverter(), new TBEmoticon(), new Newline() }, 
            new long[] { 1, 1, 1, 1 });

TBGame game = (TBGame) request.getAttribute("game");
TBSet set = game.getTbSet();

DSGPlayerData p1 = (DSGPlayerData) request.getAttribute("p1");
DSGPlayerGameData p1GameData = p1.getPlayerGameData(game.getGame());
DSGPlayerData p2 = (DSGPlayerData) request.getAttribute("p2");
DSGPlayerGameData p2GameData = p2.getPlayerGameData(game.getGame());
String myTurn = (String) request.getAttribute("myTurn");
if (myTurn == null) myTurn="false";
String setStatus = "active";
if (set.isDraw()) {
	setStatus = "draw";
}
else if (set.isCancelled()) {
    setStatus = "cancelled";
}
else if (set.isCompleted()) {
	long wPid = set.getWinnerPid();
	if (wPid == p1.getPlayerID()) {
		setStatus = p1.getName() + " wins";
	}
	else if (wPid == p2.getPlayerID()) {
		setStatus = p2.getName() + " wins";
	}
}
String otherGame = "";
if (set.isTwoGameSet()) {
	otherGame = Long.toString(set.getOtherGame(game.getGid()).getGid());
}

String moves = "";
String messages = "";
String moveNums = "";
String seqNums = "";
String dates = "";
String players = ""; //indicates which seat made message
for (int i = 0; i < game.getNumMoves(); i++) {
    moves += game.getMove(i) + ",";
}
for (TBMessage m : game.getMessages()) {
	// bug in URLConverter
	if (m.getMessage().length() == 1) {
		messages += m.getMessage() + ",";
	}
	else {
	    messages += MessageEncoder.encodeMessage(
    	    filters.applyFilters(0, m.getMessage())) + ",";
	}
    seqNums += m.getSeqNbr() + ",";
    moveNums += m.getMoveNum() + ",";
    dates += m.getDate().getTime() + ",";
    if (p1.getPlayerID() == m.getPid()) {
    	players += "1,";
    }
    else {
    	players += "2,";
    }
}

Boolean showMessages = (Boolean) request.getAttribute("showMessages");
if (showMessages == null) {
	showMessages = new Boolean(true);
}

String attach = (String) request.getAttribute("attach");
if (attach == null) {
    attach = "true";
}

int height = 550;
int width = 700;
if (request.getParameter("h") != null) {
	try { height = Integer.parseInt(request.getParameter("h")); } catch (NumberFormatException n) {}
}
if (request.getParameter("w") != null) {
    try { width = Integer.parseInt(request.getParameter("w")); } catch (NumberFormatException n) {}
}
%>

<% pageContext.setAttribute("title", "Game"); %>
<% pageContext.setAttribute("leftNav", "false"); %>
<%@ include file="../begin.jsp" %>
<%
String version = globalResources.getAppletVersion();
DSGPlayerData meData = dsgPlayerStorer.loadPlayer(me);

String cancelRequested="false";
%>
<% if (meData.showAds()) { %>
	<center>
	    <div id = "senseReplace" style="width:728px;height:90px;" top="50%"> </div>
	    <%@include file="728x90ad.jsp" %>
	    <script type="text/javascript">
	        sensePage();
	    </script>
	</center>
<% } %>



<table align="left" border="0" colspacing="1" colpadding="1">

<% String error = (String) request.getAttribute("error");
   if (error != null) { %>

<tr>
 <td>
  <b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
   Error: <%= error %>
  </font></b>
 </td>
</tr>

<%   
   }
%>

<tr>
 <td>
 
<%@ include file="applet.jsp" %>

<%--<script type="text/javascript">--%>
<%--window.google_analytics_uacct = "UA-20529582-2";--%>
<%--</script>--%>

    <br>
    <br>
<a href="/gameServer/tbpgn.jsp?g=<%= game.getGid() %>">Text (PGN)</a> version.
    
 </td>
</tr>

</table>
<br><br>

<%@ include file="../end.jsp" %>
