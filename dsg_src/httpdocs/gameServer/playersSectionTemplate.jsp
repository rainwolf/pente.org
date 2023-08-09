<form method="post" action="<%= request.getContextPath() %>/gameServer/profile"
      style="margin-top:0px;margin-bottom:0px;margin-left:0px;margin-right:0px">
   <tr>
      <td bgcolor="black">
         <table border="0" cellspacing="0" cellpadding="3" width="100%">
            <tr>
               <td bgcolor="<%= bgColor1 %>">
                  <font face="Verdana, Arial, Helvetica, sans-serif" size="3" color="<%= textColor1 %>">
                     <b>Players</b>
                  </font>
               </td>
            </tr>
            <tr>
               <td bgcolor="<%= bgColor2 %>">
                  <font face="Verdana, Arial, Helvetica, sans-serif" size="2"><b>
                     <a href="<%= request.getContextPath() %>/gameServer/myprofile">My Profile</a>
                     <br>
                     <a href="<%= request.getContextPath() %>/gameServer/mymessages">My Messages</a></b>
                     <% if (me != null) {
                        DSGPlayerData meData = dsgPlayerStorer.loadPlayer(me); %>
                     <span style="font-family:verdana;font-size:7pt;color:<%= textColor2 %>">
	                        (<%= globalResources.getDsgMessageStorer().getNumNewMessages(meData.getPlayerID()) %>)
	                      </span>
                     <% } %>
                     <br>
                     <b>
                        <a href="<%= request.getContextPath() %>/gameServer/profile.jsp">
                           Search for Player</a><br>
                        &nbsp;&nbsp;<input type="text" name="viewName" size="8">&nbsp;<input type="submit" value="Go">
                        <br>
                        <a href="<%= request.getContextPath() %>/gameServer/statsMain.jsp">Player Rankings</a>
                        <br>
                        <a href="<%= request.getContextPath() %>/gameServer/who.jsp">Who's Online</a>
                        <br>
                     </b></font>
               </td>
            </tr>
         </table>
      </td>
   </tr>
</form>