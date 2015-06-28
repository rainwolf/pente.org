<%@ page import="org.pente.game.*, org.pente.gameServer.tourney.*" %>

<%! private boolean alreadySignedUp(List players, DSGPlayerData data) {
        for (Iterator it = players.iterator(); it.hasNext();) {
            TourneyPlayerData d = (TourneyPlayerData) it.next();
            if (d.getPlayerID() == data.getPlayerID()) return true;
        }
        return false;
    }
%>

<%
String eidStr = request.getParameter("eid");
int eid = Integer.parseInt(eidStr);

Resources resources = (Resources) application.getAttribute(
    Resources.class.getName());

Tourney tourney = resources.getTourneyStorer().getTourneyDetails(eid);
List tournamentPlayers = resources.getTourneyStorer().getTourneyPlayers(eid);


String currentPage = "Signup";
String title = tourney.getName() + " Signup Confirmation";
pageContext.setAttribute("title", title);
%>


<%@ include file="../begin.jsp" %>

<%
String name = (String) request.getAttribute("name");
DSGPlayerData dsgPlayerData = null;
if (name != null) {
    dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
}
%>

<table border="0" cellpadding="0" cellspacing="0" width="100%">

  <tr> 
    <td valign="top">
      <h3>Signup for <%= tourney.getName() %></h3>
      <%@ include file="tourneyDetails.jsp" %>
    </td>
    <td valign="top" align="right">
      <%@ include file="/gameServer/rightAd.jsp" %>
    </td>
  </tr>
  <tr>
    <td colspan="2">
      <% if (name == null) { %>
        You must be a registered player to signup for this tournament.
        Please <a href="/join.jsp">Join</a> pente.org and then signup!
      <% } else { %>
	     <% if (alreadySignedUp(tournamentPlayers, dsgPlayerData)) { %>
	            You are currently signed up for this tournament.<br>
	     <% } else { 
	          DSGPlayerGameData game = dsgPlayerData.getPlayerGameData(tourney.getGame()); %>

	          <form method="post" action="tournamentSignup.jsp">
	            <input type="hidden" name="eid" value="<%= eid %>">

		        You are going to sign-up with the name <b><font color="red"><%= name %></font></b>,
		        make sure this is the user name you wish to play with.  Also note that you must
		        sign-up with your <b>primary</b> user name, that is the username you have
		        played the most games with.<br>
		        <br>
		        
		        <% boolean pass = true;
		           for (Iterator it = tourney.getRestrictions().iterator(); it.hasNext();) {
                     Restriction r = (Restriction) it.next();
                     if (r.getType() == Restriction.RATING_RESTRICTION_BELOW &&
                         game.getRating() >= r.getValue()) { 
                         pass = false; %>
                         
	            <font color="red">
	              Sorry, this tournament is only for players who have a rating
	              below <%= r.getValue() %>.  Your current
	              rating for <%= GridStateFactory.getGameName(tourney.getGame()) %>
	              is <%= (int) Math.round(game.getRating()) %>.
	            </font>
	            
                  <% } else if (r.getType() == Restriction.RATING_RESTRICTION_ABOVE &&
                                game.getRating() < r.getValue()) { 
                         pass = false; %>
                                
	            <font color="red">
	              Sorry, this tournament is only for players who have a rating
	              of <%= r.getValue() %> or above.  Your current
	              rating for <%= GridStateFactory.getGameName(tourney.getGame()) %>
	              is <%= (int) Math.round(game.getRating()) %>.
	            </font>
	            
                  <% } else if (r.getType() == Restriction.GAMES_RESTRICTION_ABOVE &&
                                game.getTotalGames() <= r.getValue()) { 
                         pass = false; %>
                                
	            <font color="red">
	              Sorry, to signup for this tournament you must have completed
	              <%= r.getValue() %> <%= GridStateFactory.getGameName(tourney.getGame()) %>
	              games.  You have played <%= game.getTotalGames() %>.
	            </font>
	            
                  <% }
                   } %>

	         <% if (pass) { %>

			    <% if (!tourney.isSpeed() && !dsgPlayerData.getEmailValid()) { %>
		             The tournament director may need to contact you using the 
		             email address in your player profile
			         (<font color="red"><%= dsgPlayerData.getEmail() %></font>).
			         <font color="red">Invalid Email Address.</font>
			         Pente.org has your email address marked as invalid,
			         meaning the last email sent to you was returned.  You can not sign-up
			         for the tournament until you update your <b><a href="../myprofile">profile
			         </a></b> with a valid email address.
			    <% }
			       else {
			         if (!tourney.isSpeed()) { %>			       
		             The tournament director may need to contact you using the 
		             email address in your player profile
			         (<font color="red"><%= dsgPlayerData.getEmail() %></font>).
		 	         Please verify that your email address is correct, if not you will
		 	         be dropped from the tournament.  If it is incorrect, correct it at your
			         <b><a href="../myprofile">profile</a></b> and then continue with sign-up.<br>
			        <br><br>
                  <% } %>
<% if (tourney.getEventID() == 1182) { %>
 <div style="font-family:Verdana, Arial, Helvetica, sans-serif; margin-top:10px;
 margin-bottom:10px; background:#fffbcc; border:1px solid #e6db55; padding:5px; font-weight:bold; width:100%;color:red;">

   Note: This tournament has VERY different rules/procedures from past tournaments at pente.org, <br>you <b>MUST <a href="/help/helpWindow.jsp?file=tourneySeventh-Heaven">read these new rules</a></b> and understand them prior to signing up.
    </div>
    <% } else { %>
                     Read the <b><a href="/gameServer/help/helpWindow.jsp?file=tournaments">Official Rules</a></b> for the full
                     details of the tournament.<br>
		     <% } %>
                     <% if (request.getAttribute("readRules") != null) { %>
                       <font color="red"><b>You must indicate that you have read
                         the Official Rules</b></font><br>
                     <% } %>
                    <input type="checkbox" name="rules" value="Y">I have read
                    and understand the Pente.org Tournament Rules.
                    <br><br>
		            <input type="submit" value="Signup">
		        <% } %>
		      <% } %>
	          </form>
	     <% } %>
     <% } %>
      </font>
    </td>

  </tr>
  <tr><td>&nbsp;</td></tr>
  <tr> 
    <td colspan="2">
      <h3><%= tournamentPlayers.size() %> Players signed up for <%= tourney.getName() %></h3>

      <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <table border="0" cellpadding="0" cellspacing="0">
          <tr bgcolor="<%= bgColor1 %>">
            <td>
              <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white">
                <b>Seed</b>&nbsp;&nbsp;
              </font>
            </td>
            <td>
              <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white">
                <b>Name</b>
              </font>
            </td>
            <td align="center">
              <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white">
                <b>Current Rating</b>&nbsp;&nbsp;
              </font>
            </td>
            <td align="center">
              <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white">
                <b>Games</b>&nbsp;&nbsp;
              </font>
            </td>
          </tr>
            <%
            for (int i = 0; i < tournamentPlayers.size(); i++) {
                TourneyPlayerData t = (TourneyPlayerData) tournamentPlayers.get(i); %>
                <tr>
                  <td align="center"><font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                    <%= (i + 1) %>
                  </td>
                  <td><font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                    <a href="../profile?viewName=<%= t.getName() %>"><%= t.getName() %></a>
                    <% DSGPlayerData d = resources.getDsgPlayerStorer().loadPlayer(t.getName());
                       DSGPlayerGameData g = d.getPlayerGameData(tourney.getGame());
                       if (g != null) {
                        int tourneyWinner = g.getTourneyWinner(); %>
			            <%@ include file="crown.jspf" %>
                    <% } %>
                    &nbsp;&nbsp;
                  </font></td>
                  <td align="center"><font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                  <%
		           int rating = t.getRating();
		    	   String gif = "ratings_";
		    	   if (t.getTotalGames() < 20) {
		    	       gif += "white.gif";
		    	   }
				   else if (rating > 1899) {
				       gif += "red.gif";
				   }
				   else if (rating > 1699) {
				       gif += "yellow.gif";
				   }
				   else if (rating > 1399) {
				       gif += "blue.gif";
				   }
				   else if (rating > 999) {
				       gif += "green.gif";
				   }
				   else {
				       gif += "gray.gif";
				   }
				  %>
					<img src="/gameServer/images/<%= gif %>"> <%= rating %>
                  </font></td>
                  <td align="center"><font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                    <%= t.getTotalGames() %>
                  </font></td>
                </tr> <%
            }
            %>
        </table>
        <br>
      </font>
    </td>
  </tr>
</table>

<%@ include file="../end.jsp" %>
