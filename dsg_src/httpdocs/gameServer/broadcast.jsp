<%@ page import="org.pente.kingOfTheHill.*,
                org.pente.game.*" %>


<% pageContext.setAttribute("title", "New Game"); %>
<%@ include file="begin.jsp" %>

<%
//Resources resources = (Resources) application.getAttribute(
//   Resources.class.getName());
//  CacheKOTHStorer kothStorer = resources.getKOTHStorer();

String name = (String) request.getAttribute("name");
DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(name);

%>



<script type="text/javascript">
function submitbroadcastform()
{
  document.broadcast_form.submit();
}
</script>

<table align="left" width="100%" border="0" colspacing="1" colpadding="1">

<tr>
 <td>
  <h3>Broadcast to your followers or friends</h3> 
     
     <div align="left" style="position:relative;font-weight:bold;border:2px <%= textColor2 %> solid; background:#ffd0a7">
       Here you can send an alert to your followers or friends (followers who follow you back) and let them know you are available to play in the live game room.
       All your recipients receive a message (from you), and players with the penteLive mobile app receive a special notification.
       <br>
     <br>
      Players' profiles are equipped with a follow button where you can add that player to your following list. The list of people you follow and are following you is 
      available in your profile. Once you follow a player, the follow button changes into an unfollow button that you can use to undo your mistake.
       <br>
     
     </div> 
     <br>
     <div align="left" style="position:relative;font-weight:bold;border:2px <%= textColor2 %> solid; background:#ffd0a7">
     - You can broadcast once per hour at most. <br>
     - Broadcasting is a subscriber-only feature. Non-subscribers can still receive broadcasts
     </div> 
     <br>
 </td>
</tr>

<tr>
 <td>

   <form name="broadcast_form" method="post" 
         action="<%= request.getContextPath() %>/gameServer/broadcast">
   <table border="0"  cellspacing="0" cellpadding="1"   bordercolor="black">

     <tr width="400">
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Game:
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
         <select size="1" id="game" name="game">
             <option value="any game">any game</option>
         <% Game games[] = GridStateFactory.getAllGames();
            for (int i = 1; i < games.length; i++) { 
            if (games[i].getId() > 50) continue;%>
               <option value="<%= GridStateFactory.getDisplayName(games[i].getId()) %>"><%= GridStateFactory.getDisplayName(games[i].getId()) %></option>
         <% } %>
       </font>
       
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Broadcast to  
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <select name="sendTo" size="1">
          <option selected value="followers">followers</option>
          <option value="friends">friends</option>
        </select>
       </font>
      </td>
     </tr>
     </td>
     </tr>
   </table>
   <br>

<% if (dsgPlayerData != null && dsgPlayerData.hasPlayerDonated()) { %>
  
        <a class="boldbuttons" href="javascript: submitbroadcastform()" style="margin-right:6px; margin-left: 6px"><span>Broadcast</span></a>
    
<%}%>
   
   </form>
 </td>
</tr>

</table>

    <br>
    <br>

<%@ include file="end.jsp" %>