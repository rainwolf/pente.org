<%@ page import="org.pente.gameServer.kids.KidsServlet, org.pente.gameServer.tourney.*" %>
<%!
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
%>
<%
    String localName = (String) request.getAttribute("name");
    boolean isPost = "POST".equalsIgnoreCase(request.getMethod());
    Resources resources = (Resources) application.getAttribute(Resources.class.getName());

    int eid = -1;
    Tourney tourney = null;
    boolean eidOk = true;
    try {
        eid = Integer.parseInt(request.getParameter("eid"));
    } catch (NumberFormatException ignored) {
        eidOk = false;
    }
    if (eidOk) {
        tourney = resources.getTourneyStorer().getTourneyDetails(eid);
    }

    boolean badOrigin = false;
    TourneyWithdraw.Result result = null;
    if (isPost && !KidsServlet.sameOrigin(request.getHeader("Origin"),
            request.getHeader("Referer"), request.getServerName())) {
        badOrigin = true;
        response.setStatus(403);
    } else {
        DSGPlayerData dsgPlayerData = localName == null ? null : dsgPlayerStorer.loadPlayer(localName);
        boolean registered = false;
        boolean open = false;
        if (dsgPlayerData != null && tourney != null) {
            registered = resources.getTourneyStorer().getTourneyPlayerPids(eid).contains(dsgPlayerData.getPlayerID());
            open = TourneyWithdraw.isRegistrationOpen(tourney,
                    resources.getTourneyStorer().getUpcomingTournies(), new java.util.Date());
        }
        result = TourneyWithdraw.check(dsgPlayerData != null, tourney, open, registered);
        if (isPost && result == TourneyWithdraw.Result.OK) {
            resources.getTourneyStorer().removePlayerFromTourney(dsgPlayerData.getPlayerID(), eid);
        }
    }

    String title = (tourney == null ? "Tournament" : tourney.getName()) + " Withdraw";
    pageContext.setAttribute("title", title);
    pageContext.setAttribute("mobileShell", Boolean.TRUE);
%>
<%@ include file="../begin.jsp" %>
<%
    String backHref = tourney == null ? "index.jsp" : "tournamentConfirm.jsp?eid=" + eid;
%>
<table border="0" cellpadding="0" cellspacing="0" width="100%">
   <tr>
      <td>
         <% if (badOrigin) { %>
         <p>This request could not be verified. Please go back and try again.</p>
         <a href="<%= backHref %>">Back</a>
         <% } else if (!isPost && result == TourneyWithdraw.Result.OK) { %>
         <h3>Withdraw from <%= esc(tourney.getName()) %>?</h3>
         <p>You can sign up again while registration is open.</p>
         <form method="post" action="tournamentWithdraw.jsp">
            <input type="hidden" name="eid" value="<%= eid %>">
            <input type="submit" value="Withdraw">
            <a href="<%= backHref %>">Cancel</a>
         </form>
         <% } else { %>
         <p><%= esc(TourneyWithdraw.message(result, tourney == null ? "" : tourney.getName())) %></p>
         <a href="<%= backHref %>">Back</a>
         <% } %>
      </td>
   </tr>
</table>
<%@ include file="../end.jsp" %>
