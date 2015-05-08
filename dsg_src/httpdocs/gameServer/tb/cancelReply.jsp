<%@ page import="org.pente.game.*, org.pente.turnBased.*,
				 org.pente.turnBased.web.TBEmoticon,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*" %>

<%
TBEmoticon emoticon = new TBEmoticon();
emoticon.setImageURL("/gameServer/forums/images/emoticons");

com.jivesoftware.base.FilterChain filters = 
	new com.jivesoftware.base.FilterChain(
        null, 1, new com.jivesoftware.base.Filter[] { 
            new HTMLFilter(), new URLConverter(), emoticon, new Newline() }, 
            new long[] { 1, 1, 1, 1 });
%>

<% pageContext.setAttribute("title", "Cancel Set"); %>
<%@ include file="../begin.jsp" %>



<%
TBSet set = (TBSet) request.getAttribute("set");
TBGame game = (TBGame) request.getAttribute("game");

DSGPlayerData meData = dsgPlayerStorer.loadPlayer(me);
DSGPlayerData opponent = dsgPlayerStorer.loadPlayer(
	game.getOpponent(meData.getPlayerID()));

%>

<table align="left" width="490" border="0" colspacing="1" colpadding="1">

<tr>
 <td>
  <h3>Cancel Set</h3>
 </td>
</tr>



<tr>
 <td>

   <form name="reply_cancel_form" method="post" 
         action="<%= request.getContextPath() %>/gameServer/tb/cancel">
     <input type="hidden" name="sid" value="<%= set.getSetId() %>">
     <input type="hidden" name="gid" value="<%= game.getGid() %>">
     
     <%= opponent.getName() %> is requesting that this set be cancelled.<br>
     <br>
     Message:
    <% String message = "";
       if (set.getCancelMsg() != null) {
           message = filters.applyFilters(0, set.getCancelMsg());
       } %>
    <%= message %><br>
	<br>
    Do you want this set to be cancelled?<br>
    <input type="submit" name="command" value="Yes">
    <input type="submit" name="command" value="No">

   </form>
 </td>
</tr>
<tr>
 <td>
<%
DSGPlayerData p1 = (DSGPlayerData) request.getAttribute("p1");
DSGPlayerGameData p1GameData = p1.getPlayerGameData(game.getGame());
DSGPlayerData p2 = (DSGPlayerData) request.getAttribute("p2");
DSGPlayerGameData p2GameData = p2.getPlayerGameData(game.getGame());
String myTurn = "false";
String setStatus = "active";
String otherGame = "";
if (set.isTwoGameSet()) {
	otherGame = Long.toString(set.getOtherGame(game.getGid()).getGid());
}
String moves = "";
for (int i = 0; i < game.getNumMoves(); i++) {
    moves += game.getMove(i) + ",";
}
String messages = "";
String moveNums = "";
String seqNums = "";
String dates = "";
String players = ""; //indicates which seat made message
Boolean showMessages = new Boolean(false);
String attach = "true";
int height = 400;
int width = 400;
String version = globalResources.getAppletVersion();
String cancelRequested="true";
%>
<%@ include file="applet.jsp" %>
 
 </td>
</tr>

</table>


<%@ include file="../end.jsp" %>