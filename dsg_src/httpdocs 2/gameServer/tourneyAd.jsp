<%@page import="org.pente.gameServer.tourney.*, org.pente.game.*" %>
<% adWidth = 550; 
List tournamentPlayers = resources.getTourneyStorer().getTourneyPlayers(1160);

%>

  <td width="100">&nbsp;</td>
  <td align="center" valign="middle">
    <table border="1" cellpadding="0" cellspacing="0" 
       width="550" bgcolor="white" bordercolor="gray">

     <tr>
       <td>
        <table border="0" cellpadding="2" cellspacing="0">
         <tr>
          <td align="center">
           <font size="4" color="black" face="Verdana, Arial, Helvetica, sans-serif">
            Signup now for the <a target="_blank"
            					  href="/gameServer/tournaments/tournamentConfirm.jsp?eid=1160">
            					  Spring Connect6 Classic Tournament!</a>
            <br>
            Live tournament - format is Single-Elimination.<br>
            <br>
            <font size="5">Prizes </font>
<table border="0" align="center">
<tr>
<td>
<font color="black" size="2">
            1st Place - Gold Crown <img src="/gameServer/images/crown.gif"> and
            Pente.org Shirt <img src="/gameServer/images/store/shirt1.jpg">
</font>
</td>
<td>
<font color="black" size="2">
            2nd Place - Pente.org Mug <img src="/gameServer/images/store/mug.jpg">
</font>
</td>
</tr></table>
          </td>
         </tr>
         <tr> 
    <td align="center">
            <font face="Verdana, Arial, Helvetica, sans-serif" size="4" color="<%= textColor2 %>">
              Tournament starts March 15th!</font>
            </font>
            <br>
      <br>
      <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="black">
      
      <b><font color="<%= textColor2 %>" size="3"><%= tournamentPlayers.size() %></font></b> Players currently signed up!
      <div style="width: 350px; height: 100px; overflow: auto;  margin: 0 1px 0 1px; border: 1px solid black">
      
        <table border="0" cellpadding="0" cellspacing="0" width="100%">
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
                  <td align="center"><font face="Verdana, Arial, Helvetica, sans-serif" color="black" size="2">
                    <%= (i + 1) %>
                  </td>
                  <td><font face="Verdana, Arial, Helvetica, sans-serif" color="black" size="2">
                    <%= t.getName() %>
                    <% DSGPlayerData d = resources.getDsgPlayerStorer().loadPlayer(t.getName());
                       DSGPlayerGameData g = d.getPlayerGameData(GridStateFactory.PENTE);
                       if (g != null) {
                        int tourneyWinner = g.getTourneyWinner(); %>
			            <%@ include file="tournaments/crown.jspf" %>
                    <% } %>
                    &nbsp;&nbsp;
                  </font></td>
                  <td align="center"><font face="Verdana, Arial, Helvetica, sans-serif" color="black" size="2">
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
                  <td align="center"><font color="black" face="Verdana, Arial, Helvetica, sans-serif" size="2">
                    <%= t.getTotalGames() %>
                  </font></td>
                </tr> <%
            }
            %>
        </table>
        </div>
        <br>
      </font>
    </td>
  </tr>
        </table>
       </td>
     </tr>
    </table>
   </td>
