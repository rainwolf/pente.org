<%@ page import="org.pente.game.*,
                 org.pente.turnBased.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*" %>


<% pageContext.setAttribute("title", "Request Game Cancel"); %>
<%@ include file="../begin.jsp" %>


<%
   TBSet set = (TBSet) request.getAttribute("set");
   String message = (String) request.getAttribute("message");
   DSGPlayerData opponent = (DSGPlayerData) request.getAttribute("opponent");
%>

<table align="left" width="490" border="0" colspacing="1" colpadding="1">

   <tr>
      <td>
         <h3>Request Game Cancel</h3>
      </td>
   </tr>


   <tr>
      <td>

         <form name="cancel_form" method="post"
               action="<%= request.getContextPath() %>/gameServer/tb/cancel">
            <input type="hidden" name="sid" value="<%= set.getSetId() %>">
            <input type="hidden" name="command" value="request">
            <% if (message != null && !message.equals("")) { %>
            <input type="hidden" name="message" value="<%= message %>">
            <% } %>
            Are you sure you want to cancel this
            <%= GridStateFactory.getGameName(set.getGame1().getGame()) %> set
            against <%= opponent.getName() %>? Your opponent will have to agree
            before the set is cancelled.<br>
            <br>
            <input type="button" value="No"
                   onclick="javascript:window.location='/gameServer/tb/game?gid=<%= request.getParameter("gid") %>&command=load';">
            <input type="button" value="Yes" onclick="javascript:document.cancel_form.submit();">
         </form>

      </td>
   </tr>

</table>


<%@ include file="../end.jsp" %>