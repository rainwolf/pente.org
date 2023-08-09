<%@ page import="org.pente.game.*,
                 org.pente.turnBased.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*" %>


<% pageContext.setAttribute("title", "Confirm Game Resignation"); %>
<%@ include file="../begin.jsp" %>


<%
   TBGame game = (TBGame) request.getAttribute("game");
   String message = (String) request.getAttribute("message");
   DSGPlayerData opponent = (DSGPlayerData) request.getAttribute("opponent");
%>

<table align="left" width="490" border="0" colspacing="1" colpadding="1">

   <tr>
      <td>
         <h3>Confirm Game Resignation</h3>
      </td>
   </tr>


   <tr>
      <td>
         <form name="resign_form" method="post"
               action="<%= request.getContextPath() %>/gameServer/tb/resign">
            <input type="hidden" name="gid" value="<%= game.getGid() %>">
            <input type="hidden" name="command" value="resign">
            <% if (message != null && !message.equals("")) { %>
            <input type="hidden" name="message" value="<%= message %>">
            <% } %>
            Are you sure you want to resign this
            <%= GridStateFactory.getGameName(game.getGame()) %> game
            against <%= opponent.getName() %>?<br>
            <br>
            <button type="button" name="command" value="No"
                    style="background-color:#f44336;color: white;font-size: 16px;padding: 5px 15px;"
                    onclick="javascript:window.location='/gameServer/tb/game?gid=<%= game.getGid() %>&command=load';">
               No
            </button>
            <button type="button" name="command" value="Yes"
                    style="background-color:#4CAF50;color: white;font-size: 16px;padding: 5px 15px;"
                    onclick="javascript:document.resign_form.submit();"> Yes, resign
            </button>
         </form>

      </td>
   </tr>

</table>


<%@ include file="../end.jsp" %>