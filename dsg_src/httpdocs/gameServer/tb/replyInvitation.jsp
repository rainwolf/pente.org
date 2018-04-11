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

<% pageContext.setAttribute("title", "Game Invitation"); %>
<%@ include file="../begin.jsp" %>



<script type="text/javascript">
  function acceptInvite() {
    document.reply_invitation_form.command.value='Accept';
    document.reply_invitation_form.submit();
  }
  function declineInvite() {
    document.reply_invitation_form.command.value='Decline';
    document.reply_invitation_form.submit();
  }
</script>
<%
String error = (String) request.getAttribute("error");
TBSet set = null;
TBGame game = null;
DSGPlayerData inviter = null;
DSGPlayerGameData dsgPlayerGameData = null;
    boolean isGo = false;
if (error == null) {
	set = (TBSet) request.getAttribute("set");
	game = set.getGame1();
	inviter = (DSGPlayerData) request.getAttribute("inviter");
	dsgPlayerGameData = inviter.getPlayerGameData(game.getGame());
    isGo = game.getGame()==GridStateFactory.TB_GO ||
            game.getGame()==GridStateFactory.TB_GO9 ||
            game.getGame()==GridStateFactory.TB_GO13;
}




Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());

String nm = (String) request.getAttribute("name");
DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(nm);

TBGameStorer tbGameStorer = resources.getTbGameStorer();
List<TBSet> currentSets = tbGameStorer.loadSets(dsgPlayerData.getPlayerID());
List<TBSet> invitesTo = new ArrayList<TBSet>();
List<TBSet> invitesFrom = new ArrayList<TBSet>();
List<TBGame> myTurn = new ArrayList<TBGame>();
List<TBGame> oppTurn = new ArrayList<TBGame>();
Utilities.organizeGames(dsgPlayerData.getPlayerID(), currentSets,
    invitesTo, invitesFrom, myTurn, oppTurn);
String title2 = "Dashboard";

boolean limitExceeded;
ServletContext ctx = getServletContext();
int gamesLimit = Integer.parseInt(ctx.getInitParameter("TBGamesLimit"));
// int gamesLimit = 6;
if (dsgPlayerData.unlimitedTBGames()) {
  limitExceeded = false;
} else {
  int currentCount = myTurn.size() + oppTurn.size();
  if (!invitesFrom.isEmpty()) {
    for (TBSet s : invitesFrom) {
      if (s.isTwoGameSet()) {
        currentCount += 2;
      } else {
        currentCount++;
      }
    }
  }
  if (currentCount > gamesLimit) {
    limitExceeded = true;
  } else {
    limitExceeded = false;
  }
}

%>

<table align="left" width="490" border="0" colspacing="1" colpadding="1">




<% if (error != null) { %>

<tr>
 <td>
  <b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
   Reply Invitation failed: <%= error %>
  </font></b>
 </td>
</tr>

<% } else { %> 

<tr>
 <td>
  <h3><% if (set.isWaitingSet()) { %>Open<% } else { %>Game<% } %> Invitation</h3>
 </td>
</tr>
<tr>
 <td>

   <form name="reply_invitation_form" method="post" 
         action="<%= request.getContextPath() %>/gameServer/tb/replyInvitation">
     <input type="hidden" name="sid" value="<%= set.getSetId() %>">
   <table border="0"  cellspacing="0" cellpadding="1"   bordercolor="black">

     <tr width="400">
      <td width="150">
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Player:
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
         <a href="/gameServer/profile?viewName=<%= inviter.getName() %>">
           <%= inviter.getName() %></a><%@ include file="../ratings.jspf" %>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Game:
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
         <%= GridStateFactory.getGameName(game.getGame()) %>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Days per move:
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <%= game.getDaysPerMove() %> Days
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Rated game:
       </font>
      </td>
      <td>
        <%= game.isRated() ? "Yes" : "No" %>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Private game:
       </font>
      </td>
      <td>
        <%= set.isPrivateGame() ? "Yes" : "No" %>
      </td>
     </tr>
     <tr>
       <td>
	     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
	       Play as:
	     </font>
       </td>
       <td>
           <% if (set.isTwoGameSet()) { %>
             White, Black (2 game set)
           <% } else if (set.getInviterPid() == set.getPlayer1Pid() && !isGo) { %>
           Black (player 2)
           <% } else if (set.getInviterPid() != set.getPlayer1Pid() && !isGo) { %>
           White (player 1)
           <% } else if (set.getInviterPid() == set.getPlayer1Pid() && isGo) { %>
           White (player 2)
           <% } else if (set.getInviterPid() != set.getPlayer1Pid() && isGo) { %>
           Black (player 1)
           <% } %>
       </td>
     </tr>
       <%
           if (set.getInvitationRestriction() == TBSet.BEGINNER) {
       %>
       <tr>
           <td colspan="3">
               <br>
               <font color="red"><b>This is a beginner invitation, accepting it means the server will post an identical one in your name.</b></font>
               <br>
           </td>
       </tr>
       
       <%
           }
       %>
     <tr>
      <td valign="top" colspan="2">

        <table border="0"  cellspacing="0" cellpadding="0"  bgcolor="<%= bgColor2	 %>" width="100%">
          <tr>
            <td width="150" valign="top">
		       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
		        Message:
		       </font>
		    </td>
		    <td>
		        <% String message = "";
		           if (game.getInviterMessage() != null) {
		               message = filters.applyFilters(0, game.getInviterMessage().getMessage());
		           } %>
		        <%= message %>
		    </td>
          </tr>
        </table>
        
       </td>
     <tr>
   </table>
   <br>
   
   <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
    Reply Message (Optional, 255 character max):<br>
    <textarea cols="50" rows="3" name="inviteeMessage"></textarea><br>
    <input type="hidden" name="command" value="Accept">
    
    <% if (!set.isWaitingSet()) { %>
    <input type="checkbox" name="ignore" value="Y"> Ignore invites from this player<br>
    <% } %>
    <% if (!limitExceeded) { %>
    <input type="button" onclick="javascript:acceptInvite();" value="Accept">
    <% } else { %>
    (Free account limit reached.) 
    <% } %>
    <% if (!set.isWaitingSet()) { %>
    <input type="button" onclick="javascript:declineInvite();" value="Decline">
    <% } %>
   </font>
   
   </form>
 </td>
</tr>

<% } %>

</table>


<%@ include file="../end.jsp" %>