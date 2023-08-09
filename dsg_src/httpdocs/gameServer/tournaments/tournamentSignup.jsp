<%@ page import="org.pente.gameServer.tourney.*" %>

<%
   String localName = (String) request.getAttribute("name");
   DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(localName);

   String eidStr = request.getParameter("eid");
   int eid = Integer.parseInt(eidStr);

// make sure they read the rules
   if (request.getParameter("rules") == null) {
      request.setAttribute("readRules", "N");
      getServletContext().getRequestDispatcher(request.getContextPath() +
            "/gameServer/tournaments/tournamentConfirm.jsp?eid=" + eidStr).
         forward(request, response);
      return;
   }

   Resources resources = (Resources) application.getAttribute(
      Resources.class.getName());

   Tourney tourney = resources.getTourneyStorer().getTourneyDetails(eid);
   resources.getTourneyStorer().addPlayerToTourney(dsgPlayerData.getPlayerID(), eid);

   String currentPage = "Sign-up";
   String title = tourney.getName() + " Signup";
   pageContext.setAttribute("title", title);
%>

<%@ include file="../begin.jsp" %>


<table border="0" cellpadding="0" cellspacing="0" width="100%">
   <tr>
      <td>
         <h3><a name="sign-up"><%= tourney.getName() %> Signup Successful</a></h3>
      </td>
   </tr>
   <tr>
      <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
            <%= localName %>, you have successfully signed up for <%= tourney.getName() %>.<br>
            <% if (tourney.isSpeed()) { %>
            This is a speed tournament, all games are played at the same time,
            so show up on time to the game room created
            for the tournament.
            <% } else { %>
            Check the forum for this tournament when it is scheduled to start
            for your matchups and for any other information or to ask questions.<br>
            <% } %>
            <br>
            <a href="tournamentConfirm.jsp?eid=<%= eidStr %>">Back</a>
            <br>
         </font>
      </td>
   </tr>
   <tr>
      <td>&nbsp;</td>
   </tr>
</table>

<%@ include file="../end.jsp" %>
