<%@ page import="org.pente.game.*,
                 org.pente.turnBased.*,
                 org.pente.turnBased.web.TBEmoticon,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*" %>

<%
   TBEmoticon emoticon = new TBEmoticon();
   emoticon.setImageURL("/gameServer/forums/images/emoticons");

   com.jivesoftware.base.FilterChain filters =
      new com.jivesoftware.base.FilterChain(
         null, 1, new com.jivesoftware.base.Filter[]{
         new HTMLFilter(), new URLConverter(), emoticon, new Newline()},
         new long[]{1, 1, 1, 1});
%>

<% pageContext.setAttribute("title", "Cancel Game Invitation"); %>
<%@ include file="../begin.jsp" %>


<%
   String error = (String) request.getAttribute("error");
   TBSet set = (TBSet) request.getAttribute("set");
   DSGPlayerData invitee = (DSGPlayerData) request.getAttribute("invitee");
   DSGPlayerGameData dsgPlayerGameData = null;
   if (invitee != null) {
      dsgPlayerGameData = invitee.getPlayerGameData(set.getGame1().getGame());
   }

%>

<table align="left" width="490" border="0" colspacing="1" colpadding="1">

   <tr>
      <td>
         <h3>Cancel Game Invitation</h3>
      </td>
   </tr>


   <% if (error != null) { %>

   <tr>
      <td>
         <b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
            Loading game failed: <%= error %>
         </font></b>
      </td>
   </tr>

   <% } else { %>

   <tr>
      <td>

         <form name="cancel_invitation_form" method="post"
               action="<%= request.getContextPath() %>/gameServer/tb/cancelInvitation">
            <input type="hidden" name="sid" value="<%= set.getSetId() %>">
            <table border="0" cellspacing="0" cellpadding="1" bordercolor="black">

               <tr width="400">
                  <td width="150">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Invited:
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <% if (invitee != null) { %>
                        <a href="/gameServer/profile?viewName=<%= invitee.getName() %>">
                           <%= invitee.getName() %>
                        </a><% if (dsgPlayerGameData != null) { %>
                        <%@ include file="../ratings.jspf" %>
                        <% } %>
                        <% } %>
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
                        <%= GridStateFactory.getGameName(set.getGame1().getGame()) %>
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
                        <%= set.getGame1().getDaysPerMove() %> Days
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
                     <%= set.getGame1().isRated() ? "Yes" : "No" %>
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
                     <% } else if (set.getInviterPid() == set.getPlayer1Pid()) { %>
                     White (player 2)
                     <% } else { %>
                     Black (player 1)
                     <% } %>
                  </td>
               </tr>
               <tr>
                  <td valign="top" colspan="2">

                     <table border="0" cellspacing="0" cellpadding="0" bgcolor="<%= bgColor2	 %>" width="100%">
                        <tr>
                           <td width="150" valign="top">
                              <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                                 Message:
                              </font>
                           </td>
                           <td>
                              <% String message = "";
                                 if (set.getGame1().getInviterMessage() != null) {
                                    message = filters.applyFilters(0, set.getGame1().getInviterMessage().getMessage());
                                 } %>
                              <%= message %>
                           </td>
                        </tr>
                     </table>

                  </td>
               <tr>
            </table>
            <br>

            <input type="submit" name="command" value="Cancel">

         </form>
      </td>
   </tr>

   <% } %>

</table>


<%@ include file="../end.jsp" %>